package com.manalejandro.boleto.domain.ai.ondevice

import kotlinx.coroutines.flow.Flow

/**
 * Availability of the on-device generative model (Gemini Nano, served by the
 * Android AICore system service).
 */
sealed interface OnDeviceAiStatus {
    /** The device has no support for on-device generative AI. */
    data object Unsupported : OnDeviceAiStatus

    /** Supported, but the model still has to be downloaded. */
    data object Downloadable : OnDeviceAiStatus

    /** Download in progress. */
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long) : OnDeviceAiStatus

    /** Model downloaded and ready to use. */
    data object Available : OnDeviceAiStatus
}

/**
 * On-device generative AI used to explain a generated ticket in natural
 * language. Nothing leaves the phone: the model runs through AICore.
 *
 * Implemented by [MlKitOnDeviceAi] on top of ML Kit's GenAI Prompt API. All
 * calls are defensive: devices without AICore simply report
 * [OnDeviceAiStatus.Unsupported].
 */
interface OnDeviceAi {

    /** Current availability of the model. */
    suspend fun status(): OnDeviceAiStatus

    /** Emits download progress until the model becomes available. */
    fun download(): Flow<OnDeviceAiStatus>

    /**
     * Generates a short text from [userPrompt], steered by [systemPrompt].
     * Throws when the model is not available or generation fails.
     */
    suspend fun generate(
        systemPrompt: String,
        userPrompt: String,
        maxOutputTokens: Int = 360,
    ): String

    /** Releases the underlying model. */
    fun close()
}
