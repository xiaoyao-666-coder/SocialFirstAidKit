package com.socialfirstaidkit.core.llm.impl

import android.graphics.Bitmap
import com.socialfirstaidkit.core.llm.api.LlmProvider
import com.socialfirstaidkit.core.llm.model.LlmProviderType
import com.socialfirstaidkit.core.llm.model.LlmRequest
import com.socialfirstaidkit.core.llm.model.LlmResponse
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Public-build placeholder for the competition-provided vivo on-device SDK.
 *
 * The original SDK and model files are not redistributed. Developers with an
 * authorized SDK can replace this class with their own adapter implementation.
 */
@Singleton
class VivoOnDeviceProvider @Inject constructor(
    @Named("vivo_ondevice_model_path") private val configuredModelPath: String
) : LlmProvider {

    override val providerName = "vivo on-device model (not distributed)"
    override val providerType = LlmProviderType.VIVO_ON_DEVICE
    override val defaultModel = MODEL_BLUE_LM_3B

    override suspend fun chat(request: LlmRequest): LlmResponse = unavailableError()

    override fun chatStream(request: LlmRequest): Flow<LlmResponse.StreamChunk> = flow {
        emit(LlmResponse.StreamChunk(delta = "Error: $UNAVAILABLE_MESSAGE", finished = true))
    }

    override suspend fun validateApiKey(): Boolean = false

    suspend fun describeBitmap(
        bitmap: Bitmap,
        prompt: String = DEFAULT_IMAGE_PROMPT
    ): VisionProbeResult {
        return VisionProbeResult(
            success = false,
            content = UNAVAILABLE_MESSAGE,
            callVitMs = 0,
            generationMs = 0,
            totalMs = 0,
            width = bitmap.width,
            height = bitmap.height,
            errorCode = ERROR_CODE
        )
    }

    private fun unavailableError(): LlmResponse.Error {
        return LlmResponse.Error(ERROR_CODE, UNAVAILABLE_MESSAGE)
    }

    data class VisionProbeResult(
        val success: Boolean,
        val content: String,
        val callVitMs: Long,
        val generationMs: Long,
        val totalMs: Long,
        val width: Int,
        val height: Int,
        val errorCode: String?
    )

    companion object {
        const val MODEL_BLUE_LM_3B = "BlueLM-OnDevice-3B"
        const val DEFAULT_MODEL_PARENT_PATH = "/sdcard/1225"
        const val DEFAULT_MODEL_PATH = "/sdcard/1225/1.7.0.4_1225_mtk9500"
        const val DEFAULT_VISION_MODEL_PATH = "/sdcard/bluelm_v_3b"

        private const val DEFAULT_IMAGE_PROMPT = "Describe the image content."
        private const val ERROR_CODE = "PROVIDER_NOT_DISTRIBUTED"
        private const val UNAVAILABLE_MESSAGE =
            "The competition-provided vivo SDK and model are not included. Configure an accessible provider instead."
    }
}
