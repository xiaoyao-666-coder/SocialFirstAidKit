package com.socialfirstaidkit.ui.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.socialfirstaidkit.core.data.local.SecureStorage
import com.socialfirstaidkit.core.data.local.dao.MessageCacheDao
import com.socialfirstaidkit.core.data.personalexpression.PersonalExpressionRepository
import com.socialfirstaidkit.core.data.personalexpression.PersonalExpressionUsageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val application: Application,
    private val secureStorage: SecureStorage,
    private val messageCacheDao: MessageCacheDao,
    private val expressionRepository: PersonalExpressionRepository,
    private val expressionUsageRepository: PersonalExpressionUsageRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    data class SettingsUiState(
        val cacheSize: String = "0 MB",
        val accessibilityPermissionGranted: Boolean = false,
        val personalExpressionMasterEnabled: Boolean = false,
        val personalExpressionMessageAssistEnabled: Boolean = true,
        val personalExpressionSocialCopyEnabled: Boolean = true
    )

    init {
        loadSettings()
        observePersonalExpressionSettings()
    }

    private fun loadSettings() {
        _uiState.value = _uiState.value.copy(
            cacheSize = calculateCurrentCacheSize(),
            accessibilityPermissionGranted = checkAccessibilityPermission()
        )
    }

    fun refreshStatus() {
        _uiState.value = _uiState.value.copy(
            cacheSize = calculateCurrentCacheSize(),
            accessibilityPermissionGranted = checkAccessibilityPermission()
        )
    }

    fun setPersonalExpressionMasterEnabled(enabled: Boolean) {
        viewModelScope.launch {
            expressionRepository.setEnabled(enabled)
        }
    }

    fun setPersonalExpressionMessageAssistEnabled(enabled: Boolean) {
        expressionUsageRepository.setMessageAssistEnabled(enabled)
    }

    fun setPersonalExpressionSocialCopyEnabled(enabled: Boolean) {
        expressionUsageRepository.setSocialCopyEnabled(enabled)
    }

    fun clearAllData() {
        viewModelScope.launch {
            messageCacheDao.deleteAll()
            secureStorage.apply {
                removeLlmApiKey("tongyi")
                removeLlmApiKey("wenxin")
                removeLlmApiKey("lanxin")
            }
            refreshStatus()
        }
    }

    fun clearCache(onComplete: (String) -> Unit) {
        viewModelScope.launch {
            try {
                application.cacheDir.deleteRecursively()
                application.externalCacheDir?.deleteRecursively()

                application.filesDir.listFiles()?.forEach { file ->
                    if (file.name != "avatars") {
                        file.deleteRecursively()
                    }
                }

                _uiState.value = _uiState.value.copy(cacheSize = calculateCurrentCacheSize())
                onComplete("缓存已清除")
            } catch (e: Exception) {
                onComplete("清除缓存失败: ${e.message}")
            }
        }
    }

    private fun calculateCurrentCacheSize(): String {
        val cacheSize = calculateDirSize(application.cacheDir) +
                (application.externalCacheDir?.let { calculateDirSize(it) } ?: 0) +
                calculateDirSize(application.filesDir)
        return formatFileSize(cacheSize)
    }

    private fun calculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.listFiles()?.sumOf { file ->
            if (file.isDirectory) calculateDirSize(file) else file.length()
        } ?: 0L
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
            else -> "${size / (1024 * 1024 * 1024)} GB"
        }
    }

    private fun checkAccessibilityPermission(): Boolean {
        val am = application.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        return enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == application.packageName
        }
    }

    private fun observePersonalExpressionSettings() {
        viewModelScope.launch {
            expressionRepository.observeProfile().collect { profile ->
                _uiState.value = _uiState.value.copy(
                    personalExpressionMasterEnabled = profile?.enabled == true && profile.questionnaireCompleted
                )
            }
        }
        viewModelScope.launch {
            expressionUsageRepository.preferences.collect { preferences ->
                _uiState.value = _uiState.value.copy(
                    personalExpressionMessageAssistEnabled = preferences.messageAssistEnabled,
                    personalExpressionSocialCopyEnabled = preferences.socialCopyEnabled
                )
            }
        }
    }
}
