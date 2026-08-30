package com.socialfirstaidkit.di

import com.socialfirstaidkit.core.data.local.SecureStorage
import com.socialfirstaidkit.core.llm.impl.VivoOnDeviceProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LlmKeyModule {

    // Competition credentials are intentionally absent. Downloaders must use
    // their own authorized providers; see docs/API_REPLACEMENT.md.
    private const val DEFAULT_TONGYI_API_KEY = ""
    private const val DEFAULT_VIVO_AIGC_BASE_URL = "https://api-ai.vivo.com.cn"
    private const val DEFAULT_VIVO_AIGC_API_KEY = ""
    private const val DEFAULT_AGNES_BASE_URL = "https://apihub.agnes-ai.com/v1"

    @Provides
    @Named("tongyi_api_key")
    fun provideTongyiApiKey(secureStorage: SecureStorage): String {
        return secureStorage.getLlmApiKey("tongyi") ?: DEFAULT_TONGYI_API_KEY
    }

    @Provides
    @Named("wenxin_api_key")
    fun provideWenxinApiKey(secureStorage: SecureStorage): String {
        return secureStorage.getLlmApiKey("wenxin") ?: ""
    }

    @Provides
    @Named("wenxin_secret_key")
    fun provideWenxinSecretKey(secureStorage: SecureStorage): String {
        return secureStorage.getLlmApiKey("wenxin_secret") ?: ""
    }

    @Provides
    @Named("lanxin_app_id")
    fun provideLanxinAppId(secureStorage: SecureStorage): String {
        return secureStorage.getLlmApiKey("lanxin_app_id") ?: ""
    }

    @Provides
    @Named("ali_nls_app_key")
    fun provideAliNlsAppKey(secureStorage: SecureStorage): String {
        return secureStorage.getLlmApiKey("ali_nls_app_key").orEmpty()
    }

    @Provides
    @Named("ali_nls_token")
    fun provideAliNlsToken(secureStorage: SecureStorage): String {
        return secureStorage.getLlmApiKey("ali_nls_token").orEmpty()
    }

    @Provides
    @Named("lanxin_app_key")
    fun provideLanxinAppKey(secureStorage: SecureStorage): String {
        return secureStorage.getLlmApiKey("lanxin_app_key") ?: ""
    }

    @Provides
    @Named("gemini_api_key")
    fun provideGeminiApiKey(secureStorage: SecureStorage): String {
        return secureStorage.getLlmApiKey("gemini").orEmpty()
    }

    @Provides
    @Named("vivo_aigc_base_url")
    fun provideVivoAigcBaseUrl(secureStorage: SecureStorage): String {
        return secureStorage.getLlmApiKey("vivo_aigc_base_url") ?: DEFAULT_VIVO_AIGC_BASE_URL
    }

    @Provides
    @Named("vivo_aigc_api_key")
    fun provideVivoAigcApiKey(secureStorage: SecureStorage): String {
        secureStorage.getLlmApiKey("vivo_aigc")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        return PortableDefaultSecrets.vivoAigcApiKey()
            .ifBlank { DEFAULT_VIVO_AIGC_API_KEY }
    }

    @Provides
    @Named("vivo_ondevice_model_path")
    fun provideVivoOnDeviceModelPath(secureStorage: SecureStorage): String {
        return secureStorage.getString("vivo_ondevice_model_path")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: VivoOnDeviceProvider.DEFAULT_MODEL_PATH
    }

    @Provides
    @Named("mimo_base_url")
    fun provideMimoBaseUrl(secureStorage: SecureStorage): String {
        return secureStorage.getLlmApiKey("mimo_base_url") ?: "https://api.xiaomimimo.com"
    }

    @Provides
    @Named("mimo_api_key")
    fun provideMimoApiKey(secureStorage: SecureStorage): String {
        secureStorage.getLlmApiKey("mimo")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        return PortableDefaultSecrets.mimoApiKey()
    }

    @Provides
    @Named("agnes_base_url")
    fun provideAgnesBaseUrl(secureStorage: SecureStorage): String {
        return secureStorage.getLlmApiKey("agnes_base_url") ?: DEFAULT_AGNES_BASE_URL
    }

    @Provides
    @Named("agnes_api_key")
    fun provideAgnesApiKey(secureStorage: SecureStorage): String {
        secureStorage.getLlmApiKey("agnes")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        return PortableDefaultSecrets.agnesApiKey()
    }

    @Provides
    @Named("iflytek_app_id")
    fun provideIflytekAppId(secureStorage: SecureStorage): String {
        return secureStorage.getLlmApiKey("iflytek_app_id").orEmpty()
    }

    @Provides
    @Named("iflytek_api_key")
    fun provideIflytekApiKey(secureStorage: SecureStorage): String {
        return secureStorage.getLlmApiKey("iflytek_api_key").orEmpty()
    }

    @Provides
    @Named("iflytek_api_secret")
    fun provideIflytekApiSecret(secureStorage: SecureStorage): String {
        return secureStorage.getLlmApiKey("iflytek_api_secret").orEmpty()
    }
}
