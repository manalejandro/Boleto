package com.manalejandro.boleto.domain.ai.ondevice

import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.ModelPreference
import com.google.mlkit.genai.prompt.ModelReleaseStage
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.content
import com.google.mlkit.genai.prompt.generateContentRequest
import com.google.mlkit.genai.prompt.generationConfig
import com.google.mlkit.genai.prompt.modelConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * [OnDeviceAi] backed by ML Kit's GenAI Prompt API (Gemini Nano via AICore).
 *
 * Two clients are created on purpose:
 *
 *  1. **FAST** (`STABLE` × `FAST`, AICore feature 645). This is the one that
 *     works on every shipping AICore, including nano-v3 devices such as the
 *     Pixel 10.
 *  2. the **default** client (`STABLE` × `FULL`, feature 648), kept as a
 *     fallback for devices whose AICore does provide it.
 *
 * Reason: `genai-prompt` 1.0.0-beta3/beta4 changed the default feature ID from
 * 636 to 648 and no production AICore provides 648, which broke the Prompt API
 * on nano-v3 phones (googlesamples/mlkit#1061). Requesting FAST avoids the
 * regression, and trying the default client afterwards keeps compatibility
 * with older AICore builds.
 *
 * The model is downloaded on demand by the system and shared between apps.
 * Everything is wrapped defensively: devices without AICore, with an unlocked
 * bootloader or with a busy service report [OnDeviceAiStatus.Unsupported] or
 * [OnDeviceAiStatus.Failed] instead of crashing.
 */
class MlKitOnDeviceAi : OnDeviceAi {

    private val clients: List<GenerativeModel> by lazy {
        listOfNotNull(
            runCatching { Generation.getClient(fastConfig()) }
                .onFailure { Log.w(TAG, "FAST client could not be created", it) }
                .getOrNull(),
            runCatching { Generation.getClient() }
                .onFailure { Log.w(TAG, "default client could not be created", it) }
                .getOrNull(),
        )
    }

    private fun fastConfig() = generationConfig {
        modelConfig = modelConfig {
            releaseStage = ModelReleaseStage.STABLE
            preference = ModelPreference.FAST
        }
    }

    /** First client whose feature is not [FeatureStatus.UNAVAILABLE]. */
    private suspend fun resolveClient(): Pair<GenerativeModel, Int>? {
        var fallback: Pair<GenerativeModel, Int>? = null
        clients.forEach { client ->
            val status = runCatching { client.checkStatus() }
                .onFailure { Log.w(TAG, "checkStatus failed", it) }
                .getOrNull() ?: return@forEach
            Log.d(TAG, "checkStatus -> $status (${status.toUiStatus()})")
            if (status == FeatureStatus.AVAILABLE) return client to status
            if (fallback == null && status != FeatureStatus.UNAVAILABLE) {
                fallback = client to status
            }
        }
        return fallback
    }

    override suspend fun status(): OnDeviceAiStatus {
        val resolved = resolveClient() ?: return OnDeviceAiStatus.Unsupported
        return resolved.second.toUiStatus()
    }

    override fun download(): Flow<OnDeviceAiStatus> = flow {
        val resolved = resolveClient()
        if (resolved == null) {
            Log.w(TAG, "download requested but no AICore feature is available")
            emit(OnDeviceAiStatus.Unsupported)
            return@flow
        }
        val (client, featureStatus) = resolved
        if (featureStatus == FeatureStatus.AVAILABLE) {
            emit(OnDeviceAiStatus.Available)
            return@flow
        }

        val downloadFlow = try {
            client.download()
        } catch (error: Exception) {
            Log.e(TAG, "download() could not start", error)
            emit(OnDeviceAiStatus.Failed(error.message))
            return@flow
        }

        var totalBytes = 0L
        emitAll(
            downloadFlow
                .map { status ->
                    when (status) {
                        is DownloadStatus.DownloadStarted -> {
                            totalBytes = status.bytesToDownload
                            Log.d(TAG, "download started, $totalBytes bytes")
                            OnDeviceAiStatus.Downloading(0L, totalBytes)
                        }

                        is DownloadStatus.DownloadProgress -> {
                            Log.d(TAG, "downloaded ${status.totalBytesDownloaded} bytes")
                            OnDeviceAiStatus.Downloading(status.totalBytesDownloaded, totalBytes)
                        }

                        is DownloadStatus.DownloadCompleted -> {
                            Log.d(TAG, "download completed")
                            OnDeviceAiStatus.Available
                        }

                        is DownloadStatus.DownloadFailed -> {
                            Log.e(TAG, "download failed: ${status.e.message}", status.e)
                            OnDeviceAiStatus.Failed(status.e.message)
                        }

                        else -> OnDeviceAiStatus.Downloading(0L, totalBytes)
                    }
                }
                .catch { error ->
                    Log.e(TAG, "download flow error", error)
                    emit(OnDeviceAiStatus.Failed(error.message))
                }
        )
    }

    override suspend fun generate(
        systemPrompt: String,
        userPrompt: String,
        maxOutputTokens: Int,
    ): String {
        val resolved = resolveClient()
            ?: error("On-device AI is not available on this device")
        val (client, featureStatus) = resolved
        if (featureStatus != FeatureStatus.AVAILABLE) {
            error("On-device model is not ready")
        }
        val request = generateContentRequest(
            systemInstruction = SystemInstruction(systemPrompt),
            content = content { text(userPrompt) },
        ) {
            this.maxOutputTokens = maxOutputTokens
            this.temperature = 0.4f
        }
        Log.d(TAG, "generating insight (${userPrompt.length} chars)")
        val response = client.generateContent(request)
        return response.candidates.firstOrNull()?.text?.trim().orEmpty()
    }

    override fun close() {
        clients.forEach { client -> runCatching { client.close() } }
    }

    private fun Int.toUiStatus(): OnDeviceAiStatus = when (this) {
        FeatureStatus.AVAILABLE -> OnDeviceAiStatus.Available
        FeatureStatus.DOWNLOADABLE -> OnDeviceAiStatus.Downloadable
        FeatureStatus.DOWNLOADING -> OnDeviceAiStatus.Downloading(0L, 0L)
        else -> OnDeviceAiStatus.Unsupported
    }

    private companion object {
        const val TAG = "BoletoGenAi"
    }
}
