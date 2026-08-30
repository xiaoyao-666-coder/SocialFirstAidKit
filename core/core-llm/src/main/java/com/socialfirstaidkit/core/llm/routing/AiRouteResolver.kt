package com.socialfirstaidkit.core.llm.routing

import com.socialfirstaidkit.core.llm.api.LlmProvider
import com.socialfirstaidkit.core.llm.impl.VivoAigcProvider
import com.socialfirstaidkit.core.llm.impl.VivoOnDeviceProvider
import com.socialfirstaidkit.core.llm.impl.MimoProvider
import com.socialfirstaidkit.core.llm.impl.GeminiProvider
import com.socialfirstaidkit.core.llm.impl.WenxinProvider
import com.socialfirstaidkit.core.llm.impl.LanxinProvider
import com.socialfirstaidkit.core.llm.impl.TongyiProvider
import com.socialfirstaidkit.core.llm.impl.AgnesProvider
import com.socialfirstaidkit.core.llm.impl.CustomLlmProvider
import com.socialfirstaidkit.core.llm.model.LlmProviderType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiRouteResolver @Inject constructor(
    private val aiRouteStore: AiRouteStore,
    private val vivoAigcProvider: VivoAigcProvider,
    private val vivoOnDeviceProvider: VivoOnDeviceProvider,
    private val mimoProvider: MimoProvider,
    private val geminiProvider: GeminiProvider,
    private val wenxinProvider: WenxinProvider,
    private val lanxinProvider: LanxinProvider,
    private val tongyiProvider: TongyiProvider,
    private val agnesProvider: AgnesProvider,
    private val customLlmProvider: CustomLlmProvider
) {
    private val providers: Map<LlmProviderType, LlmProvider>
        get() = mapOf(
            LlmProviderType.VIVO to vivoAigcProvider,
            LlmProviderType.VIVO_ON_DEVICE to vivoOnDeviceProvider,
            LlmProviderType.MIMO to mimoProvider,
            LlmProviderType.GEMINI to geminiProvider,
            LlmProviderType.WENXIN to wenxinProvider,
            LlmProviderType.LANXIN to lanxinProvider,
            LlmProviderType.TONGYI to tongyiProvider,
            LlmProviderType.AGNES to agnesProvider,
            LlmProviderType.CUSTOM to customLlmProvider
        )

    fun resolve(slot: AiFunctionSlot): Pair<LlmProvider, String> {
        val route = aiRouteStore.getRoute(slot)
        val providerType = when (route.provider) {
            AiProviderId.VIVO -> LlmProviderType.VIVO
            AiProviderId.VIVO_ON_DEVICE -> LlmProviderType.VIVO_ON_DEVICE
            AiProviderId.MIMO -> LlmProviderType.MIMO
            AiProviderId.GEMINI -> LlmProviderType.GEMINI
            AiProviderId.WENXIN -> LlmProviderType.WENXIN
            AiProviderId.LANXIN -> LlmProviderType.LANXIN
            AiProviderId.TONGYI -> LlmProviderType.TONGYI
            AiProviderId.AGNES -> LlmProviderType.AGNES
            AiProviderId.CUSTOM_OPENAI -> LlmProviderType.CUSTOM
        }
        val provider = providers[providerType] ?: vivoAigcProvider
        val modelName = if (route.provider == AiProviderId.CUSTOM_OPENAI) {
            CustomOpenAiProfiles.encodeRoutedModel(route.modelId, route.modelName)
        } else {
            route.modelName
        }
        return Pair(provider, modelName)
    }
}
