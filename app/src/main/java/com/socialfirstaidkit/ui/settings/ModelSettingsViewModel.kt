package com.socialfirstaidkit.ui.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.socialfirstaidkit.core.data.local.SecureStorage
import com.socialfirstaidkit.core.data.ocr.OcrException
import com.socialfirstaidkit.core.data.ocr.VivoOcrProcessor
import com.socialfirstaidkit.core.llm.api.LlmProvider
import com.socialfirstaidkit.core.llm.impl.AgnesProvider
import com.socialfirstaidkit.core.llm.impl.GeminiProvider
import com.socialfirstaidkit.core.llm.impl.LanxinProvider
import com.socialfirstaidkit.core.llm.impl.MimoProvider
import com.socialfirstaidkit.core.llm.impl.TongyiProvider
import com.socialfirstaidkit.core.llm.impl.VivoAigcProvider
import com.socialfirstaidkit.core.llm.impl.VivoOnDeviceProvider
import com.socialfirstaidkit.core.llm.impl.WenxinProvider
import com.socialfirstaidkit.core.llm.model.LlmMessage
import com.socialfirstaidkit.core.llm.model.LlmRequest
import com.socialfirstaidkit.core.llm.model.LlmResponse
import com.socialfirstaidkit.core.llm.model.LlmRole
import com.socialfirstaidkit.core.llm.model.TokenUsage
import com.socialfirstaidkit.core.llm.routing.AiCapability
import com.socialfirstaidkit.core.llm.routing.AiFunctionSlot
import com.socialfirstaidkit.core.llm.routing.AiModelRegistry
import com.socialfirstaidkit.core.llm.routing.AiModelRoute
import com.socialfirstaidkit.core.llm.routing.AiProviderId
import com.socialfirstaidkit.core.llm.routing.AiRouteStore
import com.socialfirstaidkit.core.llm.routing.CustomOpenAiProfile
import com.socialfirstaidkit.core.llm.routing.CustomOpenAiProfiles
import com.socialfirstaidkit.core.network.imagegen.ImageGenerationClient
import com.socialfirstaidkit.core.network.imagegen.ImageGenerationParameters
import com.socialfirstaidkit.core.network.imagegen.ImageGenerationRequest
import com.socialfirstaidkit.di.PortableDefaultSecrets
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class ModelSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("default_okhttp") private val okHttpClient: OkHttpClient,
    @Named("ocr_okhttp") private val ocrOkHttpClient: OkHttpClient,
    private val secureStorage: SecureStorage,
    private val aiRouteStore: AiRouteStore,
    private val vivoOnDeviceProvider: VivoOnDeviceProvider
) : ViewModel() {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    enum class ConnectionTestStatus {
        RUNNING,
        SUCCESS,
        FAILED
    }

    data class ConnectionTestUiState(
        val testKey: String,
        val title: String,
        val providerName: String,
        val modelName: String,
        val capabilityLabel: String,
        val prompt: String,
        val status: ConnectionTestStatus = ConnectionTestStatus.RUNNING,
        val summary: String = "正在连接 API ...",
        val responseText: String = "",
        val usageText: String = "",
        val requestDebugText: String = "",
        val traceId: String = "",
        val elapsedMs: Long? = null,
        val imageUrl: String? = null,
        val requiresAllFilesAccess: Boolean = false
    ) {
        val isRunning: Boolean
            get() = status == ConnectionTestStatus.RUNNING
    }

    data class ModelSettingsUiState(
        val routes: Map<AiFunctionSlot, AiModelRoute> = emptyMap(),
        val defaultRoutes: Map<AiFunctionSlot, AiModelRoute> = emptyMap(),

        val vivoBaseUrl: String = DEFAULT_VIVO_BASE_URL,
        val vivoApiKey: String = "",

        val customName: String = "",
        val customBaseUrl: String = "",
        val customApiKey: String = "",
        val customModel: String = "",
        val customProfiles: List<CustomOpenAiProfile> = emptyList(),

        val wenxinKey: String = "",
        val wenxinSecret: String = "",
        val lanxinAppId: String = "",
        val lanxinAppKey: String = "",
        val tongyiKey: String = "",
        val geminiKey: String = "",
        val mimoKey: String = "",
        val mimoBaseUrl: String = DEFAULT_MIMO_BASE_URL,
        val agnesKey: String = "",
        val agnesBaseUrl: String = DEFAULT_AGNES_BASE_URL,
        val connectionTest: ConnectionTestUiState? = null
    ) {
        fun isDefaultRoute(slot: AiFunctionSlot): Boolean {
            return routes[slot] == defaultRoutes[slot]
        }

        fun isProviderConfigured(provider: AiProviderId): Boolean {
            return when (provider) {
                AiProviderId.VIVO -> true
                AiProviderId.VIVO_ON_DEVICE -> false
                AiProviderId.MIMO ->
                    (mimoKey.isNotBlank() || PortableDefaultSecrets.mimoApiKey().isNotBlank()) &&
                        mimoBaseUrl.isNotBlank()
                AiProviderId.GEMINI -> geminiKey.isNotBlank()
                AiProviderId.WENXIN -> wenxinKey.isNotBlank() && wenxinSecret.isNotBlank()
                AiProviderId.LANXIN -> lanxinAppId.isNotBlank() && lanxinAppKey.isNotBlank()
                AiProviderId.TONGYI -> tongyiKey.isNotBlank()
                AiProviderId.AGNES ->
                    (agnesKey.isNotBlank() || PortableDefaultSecrets.agnesApiKey().isNotBlank()) &&
                        agnesBaseUrl.isNotBlank()
                AiProviderId.CUSTOM_OPENAI ->
                    customProfiles.any { it.baseUrl.isNotBlank() && it.apiKey.isNotBlank() && it.model.isNotBlank() } ||
                        (customBaseUrl.isNotBlank() && customApiKey.isNotBlank() && customModel.isNotBlank())
            }
        }
    }

    private val _uiState = MutableStateFlow(ModelSettingsUiState())
    val uiState: StateFlow<ModelSettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        val connectionTest = _uiState.value.connectionTest
        _uiState.value = ModelSettingsUiState(
            routes = aiRouteStore.getAllRoutes(),
            defaultRoutes = AiFunctionSlot.entries.associateWith { AiModelRegistry.defaultRoute(it) },

            vivoBaseUrl = secureStorage.getLlmApiKey(KEY_VIVO_BASE_URL) ?: DEFAULT_VIVO_BASE_URL,
            vivoApiKey = readUserVisibleApiKey(KEY_VIVO_API_KEY) {
                PortableDefaultSecrets.vivoAigcApiKey()
            },

            customName = secureStorage.getString(KEY_CUSTOM_NAME).orEmpty(),
            customBaseUrl = secureStorage.getString(KEY_CUSTOM_BASE_URL).orEmpty(),
            customApiKey = secureStorage.getString(KEY_CUSTOM_API_KEY).orEmpty(),
            customModel = secureStorage.getString(KEY_CUSTOM_MODEL).orEmpty(),
            customProfiles = CustomOpenAiProfiles.load(secureStorage),

            wenxinKey = secureStorage.getLlmApiKey(KEY_WENXIN_KEY).orEmpty(),
            wenxinSecret = secureStorage.getLlmApiKey(KEY_WENXIN_SECRET).orEmpty(),
            lanxinAppId = secureStorage.getLlmApiKey(KEY_LANXIN_APP_ID).orEmpty(),
            lanxinAppKey = secureStorage.getLlmApiKey(KEY_LANXIN_APP_KEY).orEmpty(),
            tongyiKey = secureStorage.getLlmApiKey(KEY_TONGYI_KEY).orEmpty(),
            geminiKey = secureStorage.getLlmApiKey(KEY_GEMINI_KEY).orEmpty(),
            mimoKey = readUserVisibleApiKey(KEY_MIMO_KEY) {
                PortableDefaultSecrets.mimoApiKey()
            },
            mimoBaseUrl = secureStorage.getLlmApiKey(KEY_MIMO_BASE_URL) ?: DEFAULT_MIMO_BASE_URL,
            agnesKey = readUserVisibleApiKey(KEY_AGNES_KEY) {
                PortableDefaultSecrets.agnesApiKey()
            },
            agnesBaseUrl = secureStorage.getLlmApiKey(KEY_AGNES_BASE_URL) ?: DEFAULT_AGNES_BASE_URL,
            connectionTest = connectionTest
        )
    }

    private fun readUserVisibleApiKey(providerKey: String, defaultKeyProvider: () -> String): String {
        val storedKey = secureStorage.getLlmApiKey(providerKey)
            ?.trim()
            .orEmpty()
        val builtInKey = defaultKeyProvider().trim()
        return storedKey
            .takeIf { it.isNotBlank() && it != builtInKey }
            .orEmpty()
    }

    fun saveRoute(slot: AiFunctionSlot, modelId: String): Boolean {
        val saved = CustomOpenAiProfiles.profileIdFromModelId(modelId)
            ?.let { profileId ->
                CustomOpenAiProfiles.load(secureStorage)
                    .firstOrNull { it.id == profileId }
                    ?.let { profile ->
                        aiRouteStore.saveRoute(
                            slot,
                            AiModelRoute(
                                provider = AiProviderId.CUSTOM_OPENAI,
                                modelId = CustomOpenAiProfiles.modelId(profile.id),
                                modelName = profile.model
                            )
                        )
                    }
            }
            ?: aiRouteStore.saveRouteByModelId(slot, modelId)
        loadSettings()
        return saved
    }

    fun resetRoute(slot: AiFunctionSlot) {
        aiRouteStore.resetRoute(slot)
        loadSettings()
    }

    fun saveVivoConfig(baseUrl: String, apiKey: String) {
        secureStorage.saveLlmApiKey(KEY_VIVO_BASE_URL, baseUrl.trim().ifBlank { DEFAULT_VIVO_BASE_URL })
        secureStorage.saveLlmApiKey(KEY_VIVO_API_KEY, apiKey.trim())
        loadSettings()
    }

    fun addCustomLlm(name: String, baseUrl: String, apiKey: String, model: String) {
        if (baseUrl.isNotBlank() && model.isNotBlank()) {
            CustomOpenAiProfiles.add(secureStorage, name, baseUrl, apiKey, model)
            clearCustomLlmDraft()
        } else {
            saveCustomLlmDraft(name, baseUrl, apiKey, model)
        }
        loadSettings()
    }

    fun updateCustomLlm(profileId: String, name: String, baseUrl: String, apiKey: String, model: String) {
        if (baseUrl.isNotBlank() && model.isNotBlank()) {
            CustomOpenAiProfiles.update(secureStorage, profileId, name, baseUrl, apiKey, model)
            clearCustomLlmDraft()
        } else {
            saveCustomLlmDraft(name, baseUrl, apiKey, model)
        }
        loadSettings()
    }

    private fun saveCustomLlmDraft(name: String, baseUrl: String, apiKey: String, model: String) {
        secureStorage.saveString(KEY_CUSTOM_NAME, name.trim())
        secureStorage.saveString(KEY_CUSTOM_BASE_URL, baseUrl.trim())
        secureStorage.saveString(KEY_CUSTOM_API_KEY, apiKey.trim())
        secureStorage.saveString(KEY_CUSTOM_MODEL, model.trim())
    }

    private fun clearCustomLlmDraft() {
        saveCustomLlmDraft("", "", "", "")
    }

    fun saveStandardKeys(
        wenxinKey: String,
        wenxinSecret: String,
        lanxinAppId: String,
        lanxinAppKey: String,
        tongyiKey: String,
        geminiKey: String
    ) {
        secureStorage.saveLlmApiKey(KEY_WENXIN_KEY, wenxinKey.trim())
        secureStorage.saveLlmApiKey(KEY_WENXIN_SECRET, wenxinSecret.trim())
        secureStorage.saveLlmApiKey(KEY_LANXIN_APP_ID, lanxinAppId.trim())
        secureStorage.saveLlmApiKey(KEY_LANXIN_APP_KEY, lanxinAppKey.trim())
        secureStorage.saveLlmApiKey(KEY_TONGYI_KEY, tongyiKey.trim())
        secureStorage.saveLlmApiKey(KEY_GEMINI_KEY, geminiKey.trim())
        loadSettings()
    }

    fun saveMimoConfig(key: String, baseUrl: String) {
        secureStorage.saveLlmApiKey(KEY_MIMO_KEY, key.trim())
        secureStorage.saveLlmApiKey(KEY_MIMO_BASE_URL, baseUrl.trim().ifBlank { DEFAULT_MIMO_BASE_URL })
        loadSettings()
    }

    fun saveAgnesConfig(key: String, baseUrl: String) {
        secureStorage.saveLlmApiKey(KEY_AGNES_KEY, key.trim())
        secureStorage.saveLlmApiKey(KEY_AGNES_BASE_URL, baseUrl.trim().ifBlank { DEFAULT_AGNES_BASE_URL })
        loadSettings()
    }

    fun dismissConnectionTest() {
        _uiState.update { it.copy(connectionTest = null) }
    }

    fun retryConnectionTest(testKey: String) {
        if (!testKey.startsWith("slot:")) return
        val slotKey = testKey.removePrefix("slot:")
        val slot = AiFunctionSlot.entries.firstOrNull { it.key == slotKey } ?: return
        testRoute(slot)
    }

    fun testRoute(slot: AiFunctionSlot) {
        val settings = _uiState.value
        val route = settings.routes[slot] ?: settings.defaultRoutes[slot] ?: AiModelRegistry.defaultRoute(slot)
        val compatible = AiModelRegistry.isRouteCompatible(slot, route)
        val providerConfigured = settings.isProviderConfigured(route.provider)

        val title = "${slot.title} 测试"
        val initial = ConnectionTestUiState(
            testKey = "slot:${slot.key}",
            title = title,
            providerName = providerDisplayName(route.provider, settings.customName),
            modelName = route.modelName,
            capabilityLabel = slot.requiredCapability.label,
            prompt = testPromptFor(slot.requiredCapability)
        )

        if (!compatible) {
            showImmediateTestFailure(
                initial,
                "当前模型能力不匹配",
                "所选模型 ${route.modelName} 不支持 ${slot.requiredCapability.label}，未发送请求。"
            )
            return
        }
        if (!providerConfigured) {
            showImmediateTestFailure(
                initial,
                "服务商 Key 未配置",
                "请先补齐 ${initial.providerName} 的 Base URL / API Key / 模型名称后再测试。"
            )
            return
        }

        runConnectionTest(initial) {
            when (slot.requiredCapability) {
                AiCapability.TEXT_CHAT -> testChatRoute(route, settings, withImage = false)
                AiCapability.VISION_CHAT -> testChatRoute(route, settings, withImage = true)
                AiCapability.OCR -> testOcrRoute(route, settings)
                AiCapability.IMAGE_GENERATION -> testImageGenerationRoute(route, settings)
                AiCapability.ASR -> LiveTestOutput(
                    success = false,
                    summary = "该能力需要真实音频样本",
                    responseText = "当前页面没有内置音频样本，未发送请求。"
                )
                AiCapability.TTS -> LiveTestOutput(
                    success = false,
                    summary = "该能力需要语音合成接口参数",
                    responseText = "当前页面没有配置 TTS 的真实测试样本，未发送请求。"
                )
                AiCapability.VIDEO_GENERATION -> LiveTestOutput(
                    success = false,
                    summary = "该能力是异步视频任务",
                    responseText = "当前项目尚未接入视频任务轮询流程，未发送请求。"
                )
            }
        }
    }

    fun testVivoConfig(baseUrl: String, apiKey: String) {
        val provider = VivoAigcProvider(
            baseUrl.trim().ifBlank { DEFAULT_VIVO_BASE_URL },
            apiKey.trim().ifBlank { PortableDefaultSecrets.vivoAigcApiKey() }
        )
        runProviderChatTest(
            testKey = "provider:vivo",
            title = "vivo 连接测试",
            providerName = "vivo 官方平台",
            modelName = provider.defaultModel,
            provider = provider
        )
    }

    fun testCustomLlm(name: String, baseUrl: String, apiKey: String, model: String) {
        val providerName = name.trim().ifBlank { "自定义 OpenAI" }
        val trimmedModel = model.trim()
        val capability = AiModelRegistry.primaryCustomOpenAiCapability(trimmedModel)
        val prompt = if (capability == AiCapability.TEXT_CHAT) {
            textTestPromptForModel(trimmedModel)
        } else {
            testPromptFor(capability)
        }
        val initial = ConnectionTestUiState(
            testKey = "provider:custom",
            title = "$providerName 连接测试",
            providerName = providerName,
            modelName = trimmedModel,
            capabilityLabel = capability.label,
            prompt = prompt
        )

        if (baseUrl.isBlank() || apiKey.isBlank() || model.isBlank()) {
            showImmediateTestFailure(
                initial,
                "配置不完整",
                "请补齐 Base URL、API Key 和模型名称，未发送请求。"
            )
            return
        }

        runConnectionTest(initial) {
            when (capability) {
                AiCapability.IMAGE_GENERATION -> openAiCompatibleImageGeneration(
                    baseUrl = baseUrl.trim(),
                    apiKey = apiKey.trim(),
                    model = model.trim()
                )
                AiCapability.VIDEO_GENERATION -> LiveTestOutput(
                    success = false,
                    summary = "暂不支持视频模型测试",
                    responseText = "当前配置识别为视频生成模型，但设置页暂未接入视频生成测试。"
                )
                else -> openAiCompatibleChat(
                    baseUrl = baseUrl.trim(),
                    apiKey = apiKey.trim(),
                    model = model.trim(),
                    withImage = capability == AiCapability.VISION_CHAT
                )
            }
        }
    }

    fun testMimoConfig(baseUrl: String, apiKey: String) {
        val provider = MimoProvider(
            baseUrl.trim().ifBlank { DEFAULT_MIMO_BASE_URL },
            apiKey.trim().ifBlank { PortableDefaultSecrets.mimoApiKey() }
        )
        runProviderChatTest(
            testKey = "provider:mimo",
            title = "MiMo 连接测试",
            providerName = "小米 MiMo",
            modelName = provider.defaultModel,
            provider = provider
        )
    }

    fun testAgnesConfig(baseUrl: String, apiKey: String) {
        val provider = AgnesProvider(
            baseUrl.trim().ifBlank { DEFAULT_AGNES_BASE_URL },
            apiKey.trim().ifBlank { PortableDefaultSecrets.agnesApiKey() }
        )
        runProviderChatTest(
            testKey = "provider:agnes",
            title = "Agnes 连接测试",
            providerName = "Agnes AI",
            modelName = provider.defaultModel,
            provider = provider
        )
    }

    private fun runProviderChatTest(
        testKey: String,
        title: String,
        providerName: String,
        modelName: String,
        provider: LlmProvider
    ) {
        val initial = ConnectionTestUiState(
            testKey = testKey,
            title = title,
            providerName = providerName,
            modelName = modelName,
            capabilityLabel = "文本",
            prompt = TEXT_TEST_PROMPT
        )
        runConnectionTest(initial) {
            responseToOutput(provider.chat(simpleChatRequest(modelName)))
        }
    }

    private fun runConnectionTest(
        initial: ConnectionTestUiState,
        block: suspend () -> LiveTestOutput
    ) {
        _uiState.update { it.copy(connectionTest = initial) }
        viewModelScope.launch {
            val start = SystemClock.elapsedRealtime()
            val output = try {
                block()
            } catch (e: Exception) {
                val message = e.message ?: e.javaClass.simpleName
                LiveTestOutput(
                    success = false,
                    summary = "请求异常",
                    responseText = message,
                    requiresAllFilesAccess = message.contains("ALL_FILES_ACCESS_REQUIRED")
                )
            }
            val elapsedMs = SystemClock.elapsedRealtime() - start
            _uiState.update {
                if (it.connectionTest?.testKey != initial.testKey) {
                    it
                } else {
                    it.copy(
                        connectionTest = initial.copy(
                            status = if (output.success) ConnectionTestStatus.SUCCESS else ConnectionTestStatus.FAILED,
                            summary = output.summary,
                            responseText = output.responseText,
                            usageText = output.usageText,
                            requestDebugText = output.requestDebugText,
                            traceId = output.traceId,
                            elapsedMs = elapsedMs,
                            imageUrl = output.imageUrl,
                            requiresAllFilesAccess = output.requiresAllFilesAccess
                        )
                    )
                }
            }
        }
    }

    private fun showImmediateTestFailure(
        initial: ConnectionTestUiState,
        summary: String,
        responseText: String
    ) {
        _uiState.update {
            it.copy(
                connectionTest = initial.copy(
                    status = ConnectionTestStatus.FAILED,
                    summary = summary,
                    responseText = responseText,
                    elapsedMs = 0,
                    requiresAllFilesAccess = responseText.contains("ALL_FILES_ACCESS_REQUIRED")
                )
            )
        }
    }

    private suspend fun testChatRoute(
        route: AiModelRoute,
        settings: ModelSettingsUiState,
        withImage: Boolean
    ): LiveTestOutput {
        if (withImage &&
            route.provider == AiProviderId.VIVO_ON_DEVICE &&
            route.modelId == AiModelRegistry.VIVO_ON_DEVICE_3B_ID
        ) {
            return testVivoOnDeviceVisionRoute()
        }

        if (route.provider == AiProviderId.CUSTOM_OPENAI) {
            val profile = CustomOpenAiProfiles.profileIdFromModelId(route.modelId)
                ?.let { profileId -> settings.customProfiles.firstOrNull { it.id == profileId } }
            return openAiCompatibleChat(
                baseUrl = profile?.baseUrl ?: settings.customBaseUrl,
                apiKey = profile?.apiKey ?: settings.customApiKey,
                model = profile?.model ?: settings.customModel.ifBlank { route.modelName },
                withImage = withImage
            )
        }

        val provider = providerForRoute(route.provider, settings)
            ?: return LiveTestOutput(false, "暂不支持该服务商测试", "没有找到 ${route.provider} 的测试客户端。")
        return responseToOutput(provider.chat(simpleChatRequest(route.modelName, withImage)))
    }

    private suspend fun testVivoOnDeviceVisionRoute(): LiveTestOutput = withContext(Dispatchers.IO) {
        val bitmap = createTestBitmap()
        try {
            val result = vivoOnDeviceProvider.describeBitmap(bitmap, VISION_TEST_PROMPT)
            LiveTestOutput(
                success = result.success && result.content.isNotBlank(),
                summary = if (result.success) {
                    "端侧多模态模型已返回图文理解结果"
                } else {
                    "端侧多模态图文理解失败：${result.errorCode ?: "UNKNOWN"}"
                },
                responseText = buildString {
                    appendLine(result.content.ifBlank { "(空)" })
                    appendLine()
                    appendLine("callVit=${result.callVitMs}ms")
                    appendLine("generation=${result.generationMs}ms")
                    appendLine("total=${result.totalMs}ms")
                },
                requiresAllFilesAccess = result.errorCode == "ALL_FILES_ACCESS_REQUIRED"
            )
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun testOcrRoute(
        route: AiModelRoute,
        settings: ModelSettingsUiState
    ): LiveTestOutput {
        if (route.provider == AiProviderId.VIVO_ON_DEVICE &&
            route.modelId == AiModelRegistry.VIVO_ON_DEVICE_3B_ID
        ) {
            return withContext(Dispatchers.IO) {
                val bitmap = createTestBitmap()
                try {
                    val result = vivoOnDeviceProvider.describeBitmap(
                        bitmap,
                        "请识别图片中的文字，并用一句中文说明你看到了什么。"
                    )
                    LiveTestOutput(
                        success = result.success && result.content.isNotBlank(),
                        summary = if (result.success) {
                            "端侧多模态模型已返回识图结果"
                        } else {
                            "端侧多模态识图失败：${result.errorCode ?: "UNKNOWN"}"
                        },
                        responseText = buildString {
                            appendLine(result.content.ifBlank { "(空)" })
                            appendLine()
                            appendLine("callVit=${result.callVitMs}ms")
                            appendLine("generation=${result.generationMs}ms")
                            appendLine("total=${result.totalMs}ms")
                        },
                        requiresAllFilesAccess = result.errorCode == "ALL_FILES_ACCESS_REQUIRED"
                    )
                } finally {
                    bitmap.recycle()
                }
            }
        }

        if (route.modelId == AiModelRegistry.VIVO_OCR_ROTATED_ID) {
            return withContext(Dispatchers.IO) {
                val bitmap = createTestBitmap()
                try {
                    val processor = VivoOcrProcessor(
                        context = context,
                        okHttpClient = ocrOkHttpClient,
                        vivoApiKey = settings.vivoApiKey.ifBlank { PortableDefaultSecrets.vivoAigcApiKey() }
                    )
                    val result = processor.recognizeText(bitmap)
                    LiveTestOutput(
                        success = result.text.isNotBlank() || result.blocks.isNotEmpty(),
                        summary = if (result.text.isNotBlank() || result.blocks.isNotEmpty()) {
                            "OCR 接口已返回识别结果"
                        } else {
                            "OCR 接口返回为空"
                        },
                        responseText = buildString {
                            appendLine("识别文本：")
                            appendLine(result.text.ifBlank { "(空)" })
                            appendLine()
                            appendLine("文本块数量：${result.blocks.size}")
                            appendLine("置信度：${result.confidence}")
                        }
                    )
                } catch (e: OcrException) {
                    LiveTestOutput(false, "OCR 请求失败", e.message ?: "Unknown OCR error")
                } finally {
                    bitmap.recycle()
                }
            }
        }

        return testChatRoute(route, settings, withImage = true)
    }

    private suspend fun testImageGenerationRoute(
        route: AiModelRoute,
        settings: ModelSettingsUiState
    ): LiveTestOutput {
        if (route.provider == AiProviderId.CUSTOM_OPENAI) {
            val profile = CustomOpenAiProfiles.profileIdFromModelId(route.modelId)
                ?.let { profileId -> settings.customProfiles.firstOrNull { it.id == profileId } }
            return openAiCompatibleImageGeneration(
                baseUrl = profile?.baseUrl ?: settings.customBaseUrl,
                apiKey = profile?.apiKey ?: settings.customApiKey,
                model = profile?.model ?: settings.customModel.ifBlank { route.modelName }
            )
        }

        val client = ImageGenerationClient(
            okHttpClient = okHttpClient,
            apiKey = settings.vivoApiKey.ifBlank { PortableDefaultSecrets.vivoAigcApiKey() },
            agnesBaseUrl = settings.agnesBaseUrl.ifBlank { DEFAULT_AGNES_BASE_URL },
            agnesApiKey = settings.agnesKey.ifBlank { PortableDefaultSecrets.agnesApiKey() }
        )
        val response = client.generateImage(
            ImageGenerationRequest(
                model = route.modelName,
                prompt = "A tiny green check mark icon on a clean white background. Connectivity test image.",
                parameters = ImageGenerationParameters(
                    size = if (route.provider == AiProviderId.AGNES) "1024x1024" else "2K",
                    watermark = false,
                    promptExtend = false
                )
            )
        )
        val images = response.data?.images.orEmpty()
        val firstImageUrl = images.firstOrNull()?.url
        return LiveTestOutput(
            success = response.code == 0 && images.isNotEmpty(),
            summary = if (response.code == 0 && images.isNotEmpty()) {
                "图片生成接口已返回结果"
            } else {
                "图片生成接口请求失败"
            },
            responseText = buildString {
                appendLine("code=${response.code}")
                appendLine("message=${response.message}")
                appendLine("images=${images.size}")
                images.firstOrNull()?.let { image ->
                    appendLine("url=${image.url}")
                    appendLine("size=${image.size}")
                }
                response.data?.usage?.let { usage ->
                    appendLine("usage: images=${usage.imageCount}, totalTokens=${usage.totalTokens ?: 0}")
                }
            },
            traceId = response.traceId,
            imageUrl = firstImageUrl
        )
    }

    private fun providerForRoute(
        provider: AiProviderId,
        settings: ModelSettingsUiState
    ): LlmProvider? {
        return when (provider) {
            AiProviderId.VIVO -> VivoAigcProvider(
                settings.vivoBaseUrl.ifBlank { DEFAULT_VIVO_BASE_URL },
                settings.vivoApiKey.ifBlank { PortableDefaultSecrets.vivoAigcApiKey() }
            )
            AiProviderId.VIVO_ON_DEVICE -> vivoOnDeviceProvider
            AiProviderId.MIMO -> MimoProvider(
                settings.mimoBaseUrl.ifBlank { DEFAULT_MIMO_BASE_URL },
                settings.mimoKey.ifBlank { PortableDefaultSecrets.mimoApiKey() }
            )
            AiProviderId.GEMINI -> GeminiProvider(settings.geminiKey, okHttpClient)
            AiProviderId.WENXIN -> WenxinProvider(settings.wenxinKey, settings.wenxinSecret, okHttpClient)
            AiProviderId.LANXIN -> LanxinProvider(settings.lanxinAppId, settings.lanxinAppKey, okHttpClient)
            AiProviderId.TONGYI -> TongyiProvider(settings.tongyiKey)
            AiProviderId.AGNES -> AgnesProvider(
                settings.agnesBaseUrl.ifBlank { DEFAULT_AGNES_BASE_URL },
                settings.agnesKey.ifBlank { PortableDefaultSecrets.agnesApiKey() }
            )
            AiProviderId.CUSTOM_OPENAI -> null
        }
    }

    private suspend fun openAiCompatibleChat(
        baseUrl: String,
        apiKey: String,
        model: String,
        withImage: Boolean
    ): LiveTestOutput = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank() || apiKey.isBlank() || model.isBlank()) {
            return@withContext LiveTestOutput(false, "配置不完整", "Base URL、API Key 和模型名称不能为空。")
        }

        val isGpt5Model = model.trim().startsWith("gpt-5", ignoreCase = true)
        val testPrompt = textTestPromptForModel(model)
        val requestBody = buildJsonObject {
            put("model", model.trim())
            if (isGpt5Model) {
                put("temperature", 0.7)
            } else {
                put("max_tokens", 128)
                put("temperature", 0.1)
                put("stream", false)
            }
            put("messages", buildJsonArray {
                if (!isGpt5Model) {
                    add(buildJsonObject {
                        put("role", "system")
                        put("content", "You are a connectivity test assistant. Reply briefly.")
                    })
                }
                add(buildJsonObject {
                    put("role", "user")
                    if (withImage) {
                        put("content", buildJsonArray {
                            add(buildJsonObject {
                                put("type", "text")
                                put("text", VISION_TEST_PROMPT)
                            })
                            add(buildJsonObject {
                                put("type", "image_url")
                                put("image_url", buildJsonObject {
                                    put("url", "data:image/jpeg;base64,${createTestImageBase64()}")
                                })
                            })
                        })
                    } else {
                        put("content", testPrompt)
                    }
                })
            })
        }.toString()
        val requestDebugText = buildString {
            appendLine("POST ${openAiChatUrl(baseUrl)}")
            appendLine("auth: ${apiKey.safeKeyFingerprint()}")
            append(requestBody)
        }

        val httpRequest = Request.Builder()
            .url(openAiChatUrl(baseUrl))
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            okHttpClient.newCall(httpRequest).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val responseDebugText = buildString {
                    appendLine(requestDebugText)
                    appendLine()
                    appendLine("response:")
                    appendLine("HTTP ${response.code}")
                    appendLine("content-type: ${response.header("Content-Type").orEmpty().ifBlank { "(empty)" }}")
                    appendLine(body.take(800).ifBlank { "(empty body)" })
                }
                if (!response.isSuccessful) {
                    val authMessage = if (response.code == 401 && body.contains("Invalid token", ignoreCase = true)) {
                        "服务端拒绝了这个 API Key。请确认 Base URL 和 Key 来自同一个平台，Key 是 API Key/令牌而不是登录 token，并检查 Key 是否启用。"
                    } else {
                        body.ifBlank { "Empty error body" }
                    }
                    return@withContext LiveTestOutput(
                        success = false,
                        summary = "HTTP ${response.code}",
                        responseText = authMessage,
                        requestDebugText = responseDebugText
                    )
                }

                parseOpenAiCompatibleTestBody(body).copy(requestDebugText = responseDebugText)
            }
        } catch (e: Exception) {
            LiveTestOutput(
                success = false,
                summary = "请求异常",
                responseText = e.message ?: e.javaClass.simpleName,
                requestDebugText = requestDebugText
            )
        }
    }

    private suspend fun openAiCompatibleImageGeneration(
        baseUrl: String,
        apiKey: String,
        model: String
    ): LiveTestOutput = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank() || apiKey.isBlank() || model.isBlank()) {
            return@withContext LiveTestOutput(false, "配置不完整", "Base URL、API Key 和模型名称不能为空。")
        }

        val prompt = "A tiny green check mark icon on a clean white background. Connectivity test image."
        val requestBody = buildJsonObject {
            put("model", model.trim())
            put("prompt", prompt)
            put("size", "1024x1024")
            put("n", 1)
        }.toString()
        val imagesUrl = openAiImagesUrl(baseUrl)
        val requestDebugText = buildString {
            appendLine("POST $imagesUrl")
            appendLine("auth: ${apiKey.safeKeyFingerprint()}")
            append(requestBody)
        }

        val httpRequest = Request.Builder()
            .url(imagesUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            okHttpClient.newCall(httpRequest).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val responseDebugText = buildString {
                    appendLine(requestDebugText)
                    appendLine()
                    appendLine("response:")
                    appendLine("HTTP ${response.code}")
                    appendLine("content-type: ${response.header("Content-Type").orEmpty().ifBlank { "(empty)" }}")
                    appendLine(body.take(800).ifBlank { "(empty body)" })
                }
                if (!response.isSuccessful) {
                    return@withContext LiveTestOutput(
                        success = false,
                        summary = "HTTP ${response.code}",
                        responseText = body.ifBlank { "Empty error body" },
                        requestDebugText = responseDebugText
                    )
                }

                parseOpenAiCompatibleImageBody(body).copy(requestDebugText = responseDebugText)
            }
        } catch (e: Exception) {
            LiveTestOutput(
                success = false,
                summary = "请求异常",
                responseText = e.message ?: e.javaClass.simpleName,
                requestDebugText = requestDebugText
            )
        }
    }

    private fun parseOpenAiCompatibleImageBody(body: String): LiveTestOutput {
        val trimmed = body.trimStart()
        if (trimmed.startsWith("<!doctype", ignoreCase = true) || trimmed.startsWith("<html", ignoreCase = true)) {
            return LiveTestOutput(
                success = false,
                summary = "接口地址返回网页",
                responseText = "当前地址返回的是 HTML 网页，不是 OpenAI-compatible 图片生成响应。Base URL 可能少了 /v1，或该服务商没有开放 /images/generations。"
            )
        }

        val root = runCatching { json.parseToJsonElement(body).jsonObject }
            .getOrElse { error ->
                return LiveTestOutput(
                    success = false,
                    summary = "响应解析失败",
                    responseText = "无法解析图片生成响应：${error.message}\n\n${body.take(1200)}"
                )
            }
        root.openAiErrorMessage()?.let { message ->
            return LiveTestOutput(
                success = false,
                summary = "API 返回错误",
                responseText = message
            )
        }

        val imageObjects = mutableListOf<JsonObject>()
        runCatching {
            root["data"]?.jsonArray?.mapNotNullTo(imageObjects) { item ->
                runCatching { item.jsonObject }.getOrNull()
            }
        }
        runCatching {
            root["data"]?.jsonObject?.get("images")?.jsonArray?.mapNotNullTo(imageObjects) { item ->
                runCatching { item.jsonObject }.getOrNull()
            }
        }
        runCatching {
            root["images"]?.jsonArray?.mapNotNullTo(imageObjects) { item ->
                runCatching { item.jsonObject }.getOrNull()
            }
        }

        val firstImage = imageObjects.firstOrNull()
        val url = firstImage?.get("url")?.jsonPrimitive?.contentOrNull.orEmpty()
        val b64 = firstImage?.get("b64_json")?.jsonPrimitive?.contentOrNull.orEmpty()
        val generatedImageUrl = when {
            url.isNotBlank() -> url
            b64.isNotBlank() -> "data:image/png;base64,$b64"
            else -> null
        }
        val usageText = root.openAiUsageText()

        return LiveTestOutput(
            success = generatedImageUrl != null,
            summary = if (generatedImageUrl != null) "图片生成接口已返回结果" else "未收到图片结果",
            responseText = buildString {
                appendLine("images=${imageObjects.size}")
                if (url.isNotBlank()) appendLine("url=$url")
                if (b64.isNotBlank()) appendLine("b64_json length=${b64.length}")
                if (usageText.isNotBlank()) appendLine("usage: $usageText")
                if (generatedImageUrl == null) appendLine(body.take(1200))
            },
            usageText = usageText,
            imageUrl = generatedImageUrl
        )
    }
    private fun parseOpenAiCompatibleTestBody(body: String): LiveTestOutput {
        val trimmed = body.trimStart()
        if (trimmed.startsWith("<!doctype", ignoreCase = true) || trimmed.startsWith("<html", ignoreCase = true)) {
            return LiveTestOutput(
                success = false,
                summary = "接口地址返回网页",
                responseText = "当前地址返回的是 HTML 网页，不是 OpenAI-compatible JSON/SSE 响应。Base URL 可能少了 /v1；请按服务商文档填写 API 根地址，例如 https://fast.qianxing.pro/v1。"
            )
        }
        return if (trimmed.startsWith("data:")) {
            parseOpenAiCompatibleSseBody(body)
        } else {
            parseOpenAiCompatibleJsonBody(body)
        }
    }

    private fun parseOpenAiCompatibleJsonBody(body: String): LiveTestOutput {
        val root = json.parseToJsonElement(body).jsonObject
        root.openAiErrorMessage()?.let { message ->
            return LiveTestOutput(
                success = false,
                summary = "API 返回错误",
                responseText = message
            )
        }

        val content = root.extractOpenAiText().trim()
        val usageText = root.openAiUsageText()
        return LiveTestOutput(
            success = content.isNotBlank(),
            summary = if (content.isNotBlank()) "已收到模型响应" else "未收到模型文本",
            responseText = content.ifBlank { emptyOpenAiContentMessage(usageText, body) },
            usageText = usageText
        )
    }

    private fun parseOpenAiCompatibleSseBody(body: String): LiveTestOutput {
        val contentBuilder = StringBuilder()
        var usageText = ""

        body.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("data:") }
            .map { it.removePrefix("data:").trim() }
            .filter { it.isNotBlank() && it != "[DONE]" }
            .forEach { payload ->
                val root = runCatching { json.parseToJsonElement(payload).jsonObject }
                    .getOrElse { error ->
                        return LiveTestOutput(
                            success = false,
                            summary = "响应解析失败",
                            responseText = "无法解析流式片段：${error.message}\n\n${payload.take(1200)}"
                        )
                    }
                root.openAiErrorMessage()?.let { message ->
                    return LiveTestOutput(
                        success = false,
                        summary = "API 返回错误",
                        responseText = message
                    )
                }
                val delta = root.extractOpenAiText()
                if (delta.isNotBlank()) {
                    contentBuilder.append(delta)
                }
                if (usageText.isBlank()) {
                    usageText = root.openAiUsageText()
                }
            }

        val content = contentBuilder.toString().trim()
        return LiveTestOutput(
            success = content.isNotBlank(),
            summary = if (content.isNotBlank()) "已收到模型响应" else "未收到模型文本",
            responseText = content.ifBlank { emptyOpenAiContentMessage(usageText, body) },
            usageText = usageText
        )
    }

    private fun emptyOpenAiContentMessage(usageText: String, rawBody: String): String {
        return if (usageText.isNotBlank()) {
            "服务端已返回用量统计，但没有返回模型文本。请检查模型名称是否可用、该 Key 是否有此模型权限，或换一个可确认能输出文本的模型后再试。"
        } else {
            rawBody.take(1200)
        }
    }

    private fun String.safeKeyFingerprint(): String {
        val trimmed = trim()
        if (trimmed.isBlank()) return "(empty)"
        val tail = trimmed.takeLast(6)
        return "len=${trimmed.length}, tail=...$tail"
    }

    private fun JsonObject.openAiErrorMessage(): String? {
        val error = this["error"] ?: return null
        return runCatching {
            error.jsonObject["message"]?.jsonPrimitive?.contentOrNull
        }.getOrNull() ?: error.toString()
    }

    private fun JsonObject.openAiUsageText(): String {
        val usage = runCatching { this["usage"]?.jsonObject }.getOrNull() ?: return ""
        val prompt = usage["prompt_tokens"]?.jsonPrimitive?.intOrNull ?: 0
        val completion = usage["completion_tokens"]?.jsonPrimitive?.intOrNull ?: 0
        val total = usage["total_tokens"]?.jsonPrimitive?.intOrNull ?: 0
        return "prompt=$prompt, completion=$completion, total=$total"
    }

    private fun JsonObject.extractOpenAiText(): String {
        val choicesText = runCatching {
            this["choices"]?.jsonArray
                ?.mapNotNull { choice ->
                    val choiceObject = choice.jsonObject
                    listOfNotNull(
                        choiceObject["message"],
                        choiceObject["delta"],
                        choiceObject["text"]
                    ).asSequence()
                        .mapNotNull { it.extractTextValue() }
                        .firstOrNull { it.isNotBlank() }
                }
                ?.joinToString("")
        }.getOrNull().orEmpty()

        if (choicesText.isNotBlank()) {
            return choicesText
        }

        return listOfNotNull(
            this["output_text"]?.extractTextValue(),
            this["text"]?.extractTextValue(),
            this["message"]?.extractTextValue(),
            this["content"]?.extractTextValue(),
            this["output"]?.extractTextValue()
        ).firstOrNull { it.isNotBlank() }.orEmpty()
    }

    private fun JsonElement.extractTextValue(): String? {
        return when (this) {
            is JsonPrimitive -> contentOrNull
            is JsonObject -> listOfNotNull(
                this["content"],
                this["text"],
                this["output_text"]
            ).asSequence()
                .mapNotNull { it.extractTextValue() }
                .firstOrNull { it.isNotBlank() }
            is JsonArray -> mapNotNull { it.extractTextValue() }
                .joinToString("")
                .takeIf { it.isNotBlank() }
            else -> null
        }
    }

    private fun simpleChatRequest(modelName: String, withImage: Boolean = false): LlmRequest {
        return LlmRequest(
            model = modelName,
            messages = listOf(
                LlmMessage(
                    role = LlmRole.SYSTEM,
                    content = "You are a connectivity test assistant. Reply briefly."
                ),
                LlmMessage(
                    role = LlmRole.USER,
                    content = if (withImage) VISION_TEST_PROMPT else TEXT_TEST_PROMPT,
                    imageUrl = if (withImage) createTestImageBase64() else null
                )
            ),
            maxTokens = 128,
            temperature = 0.1f
        )
    }

    private fun responseToOutput(response: LlmResponse): LiveTestOutput {
        return when (response) {
            is LlmResponse.Complete -> LiveTestOutput(
                success = response.content.isNotBlank(),
                summary = if (response.content.isNotBlank()) "已收到模型响应" else "响应为空",
                responseText = response.content.ifBlank { "(空)" },
                usageText = response.usage.toUsageText()
            )
            is LlmResponse.Error -> LiveTestOutput(
                success = false,
                summary = "请求失败：${response.code}",
                responseText = response.message,
                requiresAllFilesAccess = response.code == "ALL_FILES_ACCESS_REQUIRED" ||
                    response.message.contains("ALL_FILES_ACCESS_REQUIRED")
            )
            is LlmResponse.StreamChunk -> LiveTestOutput(
                success = false,
                summary = "响应类型异常",
                responseText = "收到流式响应片段：${response.delta}"
            )
        }
    }

    private fun TokenUsage.toUsageText(): String {
        return "prompt=$promptTokens, completion=$completionTokens, total=$totalTokens"
    }

    private fun openAiChatUrl(baseUrl: String): String {
        val normalized = baseUrl.trim().trimEnd('/')
        return if (normalized.endsWith("/chat/completions")) {
            normalized
        } else {
            "$normalized/chat/completions"
        }
    }

    private fun openAiImagesUrl(baseUrl: String): String {
        val normalized = baseUrl.trim().trimEnd('/')
        return when {
            normalized.endsWith("/images/generations") -> normalized
            normalized.endsWith("/chat/completions") -> normalized.removeSuffix("/chat/completions") + "/images/generations"
            normalized.endsWith("/responses") -> normalized.removeSuffix("/responses") + "/images/generations"
            else -> "$normalized/images/generations"
        }
    }

    private fun testPromptFor(capability: AiCapability): String {
        return when (capability) {
            AiCapability.TEXT_CHAT -> TEXT_TEST_PROMPT
            AiCapability.VISION_CHAT -> VISION_TEST_PROMPT
            AiCapability.OCR -> OCR_TEST_PROMPT
            AiCapability.IMAGE_GENERATION -> "A tiny green check mark icon on a clean white background."
            AiCapability.VIDEO_GENERATION -> "Generate a short connectivity test video."
            AiCapability.ASR -> "Audio sample required."
            AiCapability.TTS -> "Speech synthesis sample required."
        }
    }

    private fun createTestImageBase64(): String {
        val bitmap = createTestBitmap()
        return try {
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
            Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        } finally {
            bitmap.recycle()
        }
    }

    private fun createTestBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(360, 180, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 34f
            isFakeBoldText = true
        }
        canvas.drawText("AI TEST 123", 42f, 96f, paint)
        paint.textSize = 22f
        paint.isFakeBoldText = false
        canvas.drawText("Zhiyan Social Communication Assistant", 42f, 132f, paint)
        return bitmap
    }

    private fun providerDisplayName(provider: AiProviderId, customLlmName: String): String {
        return if (provider == AiProviderId.CUSTOM_OPENAI && customLlmName.isNotBlank()) {
            customLlmName
        } else {
            provider.displayName
        }
    }

    private fun textTestPromptForModel(model: String): String {
        return if (model.trim().startsWith("gpt-5", ignoreCase = true)) {
            GPT5_TEXT_TEST_PROMPT
        } else {
            TEXT_TEST_PROMPT
        }
    }

    private data class LiveTestOutput(
        val success: Boolean,
        val summary: String,
        val responseText: String,
        val usageText: String = "",
        val requestDebugText: String = "",
        val traceId: String = "",
        val imageUrl: String? = null,
        val requiresAllFilesAccess: Boolean = false
    )

    companion object {
        const val DEFAULT_VIVO_BASE_URL = "https://api-ai.vivo.com.cn"
        const val DEFAULT_MIMO_BASE_URL = "https://api.xiaomimimo.com"
        const val DEFAULT_AGNES_BASE_URL = "https://apihub.agnes-ai.com/v1"
        private const val TEXT_TEST_PROMPT = "hi，请只用一句中文回复连接正常"
        private const val GPT5_TEXT_TEST_PROMPT = "Explain quantum entanglement in one paragraph."
        private const val VISION_TEST_PROMPT = "请识别这张测试图中的文字，并简短回复。"
        private const val OCR_TEST_PROMPT = "识别测试图片中的文字 AI TEST 123。"

        private const val KEY_VIVO_BASE_URL = "vivo_aigc_base_url"
        private const val KEY_VIVO_API_KEY = "vivo_aigc"

        private const val KEY_CUSTOM_NAME = "custom_llm_name"
        private const val KEY_CUSTOM_BASE_URL = "custom_llm_base_url"
        private const val KEY_CUSTOM_API_KEY = "custom_llm_api_key"
        private const val KEY_CUSTOM_MODEL = "custom_llm_default_model"

        private const val KEY_WENXIN_KEY = "wenxin"
        private const val KEY_WENXIN_SECRET = "wenxin_secret"
        private const val KEY_LANXIN_APP_ID = "lanxin_app_id"
        private const val KEY_LANXIN_APP_KEY = "lanxin_app_key"
        private const val KEY_TONGYI_KEY = "tongyi"
        private const val KEY_GEMINI_KEY = "gemini"
        private const val KEY_MIMO_KEY = "mimo"
        private const val KEY_MIMO_BASE_URL = "mimo_base_url"
        private const val KEY_AGNES_KEY = "agnes"
        private const val KEY_AGNES_BASE_URL = "agnes_base_url"
    }
}
