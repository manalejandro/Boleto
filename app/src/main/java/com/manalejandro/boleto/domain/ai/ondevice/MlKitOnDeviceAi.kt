package com.manalejandro.boleto.domain.ai.ondevice

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.content
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * [OnDeviceAi] backed by ML Kit's GenAI Prompt API (Gemini Nano vía AICore).
 *
 * The model is downloaded on demand by the system (about 2 GB) and shared
 * between apps, so the download happens only once per device. Everything is
 * wrapped in `runCatching`: devices without AICore, with an unlocked
 * bootloader or with a busy service simply report [OnDeviceAiStatus.Unsupported]
 * instead of crashing.
 */
class MlKitOnDeviceAi : OnDeviceAi {

    private val model: GenerativeModel? by lazy {
        runCatching { Generation.getClient() }.getOrNull()
    }

    override suspend fun status(): OnDeviceAiStatus {
        val client = model ?: return OnDeviceAiStatus.Unsupported
        return runCatching {
            when (client.checkStatus()) {
                FeatureStatus.AVAILABLE -> OnDeviceAiStatus.Available
                FeatureStatus.DOWNLOADABLE -> OnDeviceAiStatus.Downloadable
                FeatureStatus.DOWNLOADING -> OnDeviceAiStatus.Downloading(0L, 0L)
                else -> OnDeviceAiStatus.Unsupported
            }
        }.getOrDefault(OnDeviceAiStatus.Unsupported)
    }

    override fun download(): Flow<OnDeviceAiStatus> {
        val client = model ?: return flowOf(OnDeviceAiStatus.Unsupported)
        var totalBytes = 0L
        return runCatching { client.download() }
            .getOrElse { return flowOf(OnDeviceAiStatus.Downloadable) }
            .map { status ->
                when (status) {
                    is DownloadStatus.DownloadStarted -> {
                        totalBytes = status.bytesToDownload
                        OnDeviceAiStatus.Downloading(0L, totalBytes)
                    }

                    is DownloadStatus.DownloadProgress ->
                        OnDeviceAiStatus.Downloading(status.totalBytesDownloaded, totalBytes)

                    is DownloadStatus.DownloadCompleted -> OnDeviceAiStatus.Available

                    is DownloadStatus.DownloadFailed -> OnDeviceAiStatus.Downloadable

                    else -> OnDeviceAiStatus.Downloading(0L, totalBytes)
                }
            }
            .catch { emit(OnDeviceAiStatus.Downloadable) }
    }

    override suspend fun generate(
        systemPrompt: String,
        userPrompt: String,
        maxOutputTokens: Int,
    ): String {
        val client = model ?: error("On-device AI is not available on this device")
        val request = generateContentRequest(
            systemInstruction = SystemInstruction(systemPrompt),
            content = content { text(userPrompt) },
        ) {
            this.maxOutputTokens = maxOutputTokens
            this.temperature = 0.4f
        }
        val response = client.generateContent(request)
        return response.candidates.firstOrNull()?.text?.trim().orEmpty()
    }

    override fun close() {
        runCatching { model?.close() }
    }
}
