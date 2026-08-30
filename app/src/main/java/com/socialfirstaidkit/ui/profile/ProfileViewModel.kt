package com.socialfirstaidkit.ui.profile

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.socialfirstaidkit.core.data.local.SecureStorage
import com.socialfirstaidkit.core.data.local.dao.AppStatsDao
import com.socialfirstaidkit.core.data.local.dao.UserProfileDao
import com.socialfirstaidkit.core.data.local.entity.AppStatsEntity
import com.socialfirstaidkit.core.data.local.entity.UserProfileEntity
import com.socialfirstaidkit.core.data.personalexpression.PersonalExpressionProfile
import com.socialfirstaidkit.core.data.personalexpression.PersonalExpressionRepository
import com.socialfirstaidkit.core.data.personalexpression.PersonalExpressionUpdateProposal
import com.socialfirstaidkit.core.data.personalexpression.PersonalExpressionVersion
import com.socialfirstaidkit.core.data.personalexpression.LearningThreshold
import com.socialfirstaidkit.core.llm.api.MessageAssistClient
import com.socialfirstaidkit.core.llm.routing.AiFunctionSlot
import com.socialfirstaidkit.core.llm.routing.AiRouteStore
import com.socialfirstaidkit.core.network.imagegen.ImageGenerationClient
import com.socialfirstaidkit.feature.socialcopy.facedetection.FaceDetectionService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject

private const val TAG = "ComicAvatar"

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val application: Application,
    private val userProfileDao: UserProfileDao,
    private val appStatsDao: AppStatsDao,
    private val imageGenerationClient: ImageGenerationClient,
    private val faceDetectionService: FaceDetectionService,
    private val aiRouteStore: AiRouteStore,
    private val secureStorage: SecureStorage,
    private val messageAssistClient: MessageAssistClient,
    val expressionRepository: PersonalExpressionRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    data class ProfileUiState(
        val userProfile: UserProfileEntity? = null,
        val appStats: AppStatsEntity? = null,
        val showEditNicknameDialog: Boolean = false,
        val showGenderSelector: Boolean = false,
        val showAgeSelector: Boolean = false,
        val isLoading: Boolean = false,
        val appVersion: String = "1.0.0",
        val lastRefreshTime: Long = 0,
        val isGeneratingComicAvatar: Boolean = false,
        val comicAvatarError: String? = null,
        val showComicAvatarDialog: Boolean = false,
        val showAvatarPreviewDialog: Boolean = false,
        val showComicAvatarTypeSelector: Boolean = false,
        val showPureAnimeConfigDialog: Boolean = false,
        val pureAnimePrompt: String = "",
        val showPrototypeConfigDialog: Boolean = false,
        val prototypeImageUri: String? = null,
        val prototypePrompt: String = "",
        val isDetectingFace: Boolean = false,
        val faceDetectionError: String? = null,
        val cacheSize: String = "0 MB",
        val aiDefaultIdentity: String = DEFAULT_AI_IDENTITY,
        val aiDefaultTone: String = DEFAULT_AI_TONE,
        // 表达档案
        val expressionProfile: PersonalExpressionProfile? = null,
        val expressionPendingProposal: PersonalExpressionUpdateProposal? = null,
        val expressionActionMessage: String? = null,
        val isGeneratingExpressionUpdate: Boolean = false
    )

    init {
        viewModelScope.launch {
            combine(
                userProfileDao.getProfile(),
                appStatsDao.getStats()
            ) { profile, stats ->
                _uiState.value.copy(
                    userProfile = profile,
                    appStats = stats,
                    appVersion = getAppVersion(),
                    aiDefaultIdentity = getAiDefaultIdentity(),
                    aiDefaultTone = getAiDefaultTone()
                )
            }.collect { state ->
                _uiState.value = state
            }
        }

        viewModelScope.launch {
            expressionRepository.observeProfile().collect { profile ->
                _uiState.value = _uiState.value.copy(expressionProfile = profile)
            }
        }

        viewModelScope.launch {
            expressionRepository.observePendingProposal().collect { proposal ->
                _uiState.value = _uiState.value.copy(expressionPendingProposal = proposal)
            }
        }

        viewModelScope.launch {
            initializeDefaultData()
        }
    }

    private suspend fun initializeDefaultData() {
        val profile = userProfileDao.getProfileSync()
        if (profile == null) {
            userProfileDao.insert(UserProfileEntity())
        }

        val stats = appStatsDao.getStatsSync()
        if (stats == null) {
            appStatsDao.insert(AppStatsEntity())
        }
    }

    private fun getAppVersion(): String {
        return try {
            application.packageManager.getPackageInfo(application.packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    fun updateNickname(nickname: String) {
        viewModelScope.launch {
            userProfileDao.updateNickname(nickname)
            _uiState.value = _uiState.value.copy(showEditNicknameDialog = false)
        }
    }

    fun updateAvatar(uri: Uri?) {
        viewModelScope.launch {
            uri?.let { sourceUri ->
                val savedPath = copyImageToPrivateDir(sourceUri)
                userProfileDao.updateAvatar(savedPath)
            } ?: run {
                userProfileDao.updateAvatar(null)
            }
        }
    }

    private suspend fun copyImageToPrivateDir(sourceUri: Uri): String? {
        return try {
            val context = application.applicationContext
            val inputStream = context.contentResolver.openInputStream(sourceUri)
            val avatarDir = File(context.filesDir, "avatars")
            if (!avatarDir.exists()) {
                avatarDir.mkdirs()
            }

            val oldProfile = userProfileDao.getProfileSync()
            oldProfile?.avatarUri?.let { oldPath ->
                try {
                    File(oldPath).delete()
                } catch (e: Exception) {
                }
            }

            val fileName = "avatar_${UUID.randomUUID()}.jpg"
            val destFile = File(avatarDir, fileName)

            inputStream?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun updateGender(gender: String) {
        viewModelScope.launch {
            userProfileDao.updateGender(gender)
            _uiState.value = _uiState.value.copy(showGenderSelector = false)
        }
    }

    fun updateAge(age: Int) {
        viewModelScope.launch {
            userProfileDao.updateAge(age)
            _uiState.value = _uiState.value.copy(showAgeSelector = false)
        }
    }

    // ========== 表达档案操作 ==========

    fun toggleExpressionEnabled() {
        viewModelScope.launch {
            val profile = _uiState.value.expressionProfile
            if (profile == null) {
                expressionRepository.createDefaultProfile(enabled = true)
            } else {
                expressionRepository.setEnabled(!profile.enabled)
            }
        }
    }

    fun setExpressionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            expressionRepository.setEnabled(enabled)
        }
    }

    fun setAutoLearningEnabled(enabled: Boolean) {
        viewModelScope.launch {
            expressionRepository.setAutoLearningEnabled(enabled)
        }
    }

    fun setLearningThreshold(threshold: LearningThreshold) {
        viewModelScope.launch {
            val profile = expressionRepository.getProfile() ?: return@launch
            expressionRepository.saveEditedProfile(
                profile.copy(learningThreshold = threshold),
                reason = "调整学习频率"
            )
        }
    }

    fun completeQuestionnaire(
        answers: Map<String, List<String>>,
        enableAfter: Boolean = false,
        onCompleted: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val profile = expressionRepository.completeQuestionnaire(answers)
            _uiState.value = _uiState.value.copy(expressionProfile = profile)
            if (enableAfter) {
                expressionRepository.setEnabled(true)
                _uiState.value = _uiState.value.copy(expressionProfile = profile.copy(enabled = true))
            }
            onCompleted()
        }
    }

    fun saveQuestionnaireDraft(
        answers: Map<String, List<String>>,
        currentPage: Int
    ) {
        viewModelScope.launch {
            expressionRepository.saveQuestionnaireDraft(
                com.socialfirstaidkit.core.data.personalexpression.PersonalExpressionDraft(
                    answers = answers,
                    currentPage = currentPage
                )
            )
        }
    }

    suspend fun loadQuestionnaireDraft(): com.socialfirstaidkit.core.data.personalexpression.PersonalExpressionDraft {
        return expressionRepository.getQuestionnaireDraft()
    }

    fun clearQuestionnaireDraft() {
        viewModelScope.launch {
            expressionRepository.clearQuestionnaireDraft()
        }
    }

    fun saveEditedProfile(profile: PersonalExpressionProfile) {
        viewModelScope.launch {
            expressionRepository.saveEditedProfile(profile)
        }
    }

    fun createDefaultProfile(enabled: Boolean = false) {
        viewModelScope.launch {
            expressionRepository.createDefaultProfile(enabled = enabled)
        }
    }

    fun generateExpressionUpdateFromRecentUse() {
        viewModelScope.launch {
            val profile = expressionRepository.getProfile()
            if (profile == null || !profile.questionnaireCompleted) {
                _uiState.value = _uiState.value.copy(expressionActionMessage = "请先建立表达档案")
                return@launch
            }
            val threshold = profile.learningThreshold.sampleCount
            val samples = expressionRepository.getUnconsumedSamples(limit = 60)
            if (samples.size < threshold) {
                _uiState.value = _uiState.value.copy(expressionActionMessage = "需要积累 $threshold 条样本（当前 ${samples.size} 条）")
                return@launch
            }
            if (_uiState.value.expressionPendingProposal != null) {
                _uiState.value = _uiState.value.copy(expressionActionMessage = "已有待确认更新建议")
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                isGeneratingExpressionUpdate = true,
                expressionActionMessage = null
            )
            val proposal = runCatching {
                val aiSuggestion = runCatching {
                    messageAssistClient.summarizePersonalExpressionUpdate(profile, samples)
                }.getOrNull()
                expressionRepository.createLocalUpdateProposalWithLlmResult(aiSuggestion)
            }.getOrNull()

            _uiState.value = _uiState.value.copy(
                isGeneratingExpressionUpdate = false,
                expressionActionMessage = if (proposal != null) "已生成待确认更新建议" else "暂时无法生成更新建议"
            )
        }
    }

    fun clearExpressionActionMessage() {
        _uiState.value = _uiState.value.copy(expressionActionMessage = null)
    }

    fun applyPendingProposal() {
        viewModelScope.launch {
            expressionRepository.applyPendingProposal()
        }
    }

    fun ignorePendingProposal() {
        viewModelScope.launch {
            expressionRepository.ignorePendingProposal()
        }
    }

    fun restoreVersion(versionId: String) {
        viewModelScope.launch {
            expressionRepository.restoreVersion(versionId)
        }
    }

    fun deleteExpressionProfile() {
        viewModelScope.launch {
            expressionRepository.deleteAll()
        }
    }

    // ========== 漫画形象 ==========

    fun showPureAnimeConfigDialog() {
        viewModelScope.launch {
            val profile = userProfileDao.getProfileSync()
            val gender = profile?.gender ?: "保密"
            val genderDesc = when (gender) {
                "男" -> "男性"
                "女" -> "女性"
                else -> "中性"
            }
            _uiState.value = _uiState.value.copy(
                showComicAvatarTypeSelector = false,
                showPureAnimeConfigDialog = true,
                pureAnimePrompt = "生成一个精致、现代、温和的${genderDesc}数字形象，主体居中，背景干净，色彩明亮，适合作为个人中心专属形象展示"
            )
        }
    }

    fun hidePureAnimeConfigDialog() {
        _uiState.value = _uiState.value.copy(showPureAnimeConfigDialog = false)
    }

    fun updatePureAnimePrompt(prompt: String) {
        _uiState.value = _uiState.value.copy(pureAnimePrompt = prompt)
    }

    fun generatePureAnimeAvatar() {
        viewModelScope.launch {
            Log.d(TAG, "=== 开始生成纯二次元漫画形象 ===")

            _uiState.value = _uiState.value.copy(
                isGeneratingComicAvatar = true,
                comicAvatarError = null,
                showComicAvatarDialog = false,
                showPureAnimeConfigDialog = false
            )

            try {
                val prompt = _uiState.value.pureAnimePrompt
                val result = imageGenerationClient.generateDigitalAvatarImage(
                    originalImageBase64 = null,
                    prompt = prompt,
                    model = aiRouteStore.getRoute(AiFunctionSlot.PROFILE_AVATAR_IMAGE_GENERATION).modelName
                )

                result.onSuccess { comicUrl ->
                    userProfileDao.updateComicAvatar(comicUrl, System.currentTimeMillis())
                    _uiState.value = _uiState.value.copy(
                        isGeneratingComicAvatar = false,
                        showComicAvatarDialog = true
                    )
                }.onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isGeneratingComicAvatar = false,
                        comicAvatarError = error.message ?: "生成失败，请重试",
                        showComicAvatarDialog = true
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isGeneratingComicAvatar = false,
                    comicAvatarError = e.message ?: "生成失败，请重试",
                    showComicAvatarDialog = true
                )
            }
        }
    }

    fun showPrototypeConfigDialog() {
        _uiState.value = _uiState.value.copy(
            showComicAvatarTypeSelector = false,
            showPrototypeConfigDialog = true,
            prototypeImageUri = null,
            prototypePrompt = "基于照片中的人物生成精致、现代、温和的数字形象，保留主要面部特征、发型气质和整体辨识度，主体居中，背景干净，适合作为个人中心专属形象展示",
            faceDetectionError = null
        )
    }

    fun hidePrototypeConfigDialog() {
        _uiState.value = _uiState.value.copy(showPrototypeConfigDialog = false)
    }

    fun updatePrototypeImage(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDetectingFace = true,
                faceDetectionError = null,
                prototypeImageUri = null
            )

            try {
                val detectionResult = faceDetectionService.detectFacesForPreview(uri)

                detectionResult.onSuccess { faces ->
                    if (faces.isEmpty()) {
                        _uiState.value = _uiState.value.copy(
                            isDetectingFace = false,
                            faceDetectionError = "未识别到人脸，请重新上传包含人脸的照片",
                            prototypeImageUri = null
                        )
                    } else {
                        val context = application.applicationContext
                        val inputStream = context.contentResolver.openInputStream(uri)
                            ?: throw IllegalStateException("无法打开图片")

                        val tempFile = File(context.cacheDir, "prototype_${System.currentTimeMillis()}.jpg")
                        FileOutputStream(tempFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                        inputStream.close()

                        _uiState.value = _uiState.value.copy(
                            prototypeImageUri = tempFile.absolutePath,
                            isDetectingFace = false,
                            faceDetectionError = null
                        )
                    }
                }.onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isDetectingFace = false,
                        faceDetectionError = "人脸检测失败: ${error.message}",
                        prototypeImageUri = null
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDetectingFace = false,
                    faceDetectionError = "图片处理失败: ${e.message}",
                    prototypeImageUri = null
                )
            }
        }
    }

    fun updatePrototypePrompt(prompt: String) {
        _uiState.value = _uiState.value.copy(prototypePrompt = prompt)
    }

    fun generatePrototypeBasedComic() {
        viewModelScope.launch {
            val imageUri = _uiState.value.prototypeImageUri

            if (imageUri == null) {
                _uiState.value = _uiState.value.copy(faceDetectionError = "请先上传图片")
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                isGeneratingComicAvatar = true,
                comicAvatarError = null,
                showComicAvatarDialog = false,
                showPrototypeConfigDialog = false
            )

            try {
                val imageFile = File(imageUri)
                if (!imageFile.exists()) {
                    _uiState.value = _uiState.value.copy(
                        isGeneratingComicAvatar = false,
                        comicAvatarError = "图片文件不存在",
                        showComicAvatarDialog = true
                    )
                    return@launch
                }

                val base64Image = imageFile.readBytes().let { bytes ->
                    "data:image/jpeg;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                }

                val prompt = _uiState.value.prototypePrompt
                val result = imageGenerationClient.generateDigitalAvatarImage(
                    originalImageBase64 = base64Image,
                    prompt = prompt,
                    model = aiRouteStore.getRoute(AiFunctionSlot.PROFILE_AVATAR_IMAGE_GENERATION).modelName
                )

                result.onSuccess { comicUrl ->
                    userProfileDao.updateComicAvatar(comicUrl, System.currentTimeMillis())
                    _uiState.value = _uiState.value.copy(
                        isGeneratingComicAvatar = false,
                        showComicAvatarDialog = true
                    )
                }.onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isGeneratingComicAvatar = false,
                        comicAvatarError = error.message ?: "生成失败，请重试",
                        showComicAvatarDialog = true
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isGeneratingComicAvatar = false,
                    comicAvatarError = e.message ?: "生成失败，请重试",
                    showComicAvatarDialog = true
                )
            }
        }
    }

    fun showComicAvatarDialog() {
        _uiState.value = _uiState.value.copy(showComicAvatarDialog = true)
    }

    fun hideComicAvatarDialog() {
        _uiState.value = _uiState.value.copy(showComicAvatarDialog = false)
    }

    fun showComicAvatarTypeSelector() {
        _uiState.value = _uiState.value.copy(showComicAvatarTypeSelector = true)
    }

    fun hideComicAvatarTypeSelector() {
        _uiState.value = _uiState.value.copy(showComicAvatarTypeSelector = false)
    }

    fun showAvatarPreviewDialog() {
        _uiState.value = _uiState.value.copy(showAvatarPreviewDialog = true)
    }

    fun hideAvatarPreviewDialog() {
        _uiState.value = _uiState.value.copy(showAvatarPreviewDialog = false)
    }

    fun showEditNicknameDialog() {
        _uiState.value = _uiState.value.copy(showEditNicknameDialog = true)
    }

    fun hideEditNicknameDialog() {
        _uiState.value = _uiState.value.copy(showEditNicknameDialog = false)
    }

    fun showGenderSelector() {
        _uiState.value = _uiState.value.copy(showGenderSelector = true)
    }

    fun hideGenderSelector() {
        _uiState.value = _uiState.value.copy(showGenderSelector = false)
    }

    fun showAgeSelector() {
        _uiState.value = _uiState.value.copy(showAgeSelector = true)
    }

    fun hideAgeSelector() {
        _uiState.value = _uiState.value.copy(showAgeSelector = false)
    }

    fun clearAllData() {
        viewModelScope.launch {
            userProfileDao.delete()
            appStatsDao.delete()
            initializeDefaultData()
        }
    }

    fun refreshStats() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                initializeDefaultData()
                val latestProfile = userProfileDao.getProfileSync()
                val latestStats = appStatsDao.getStatsSync()
                val latestExpressionProfile = expressionRepository.getProfile()
                val latestCacheSize = calculateCurrentCacheSize()

                _uiState.value = _uiState.value.copy(
                    userProfile = latestProfile,
                    appStats = latestStats,
                    expressionProfile = latestExpressionProfile,
                    cacheSize = latestCacheSize,
                    appVersion = getAppVersion(),
                    aiDefaultIdentity = getAiDefaultIdentity(),
                    aiDefaultTone = getAiDefaultTone(),
                    isLoading = false,
                    lastRefreshTime = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    lastRefreshTime = System.currentTimeMillis()
                )
            }
        }
    }

    fun calculateCacheSize() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(cacheSize = calculateCurrentCacheSize())
        }
    }

    private fun calculateCurrentCacheSize(): String {
        val cacheDir = application.cacheDir
        val externalCacheDir = application.externalCacheDir
        val filesDir = application.filesDir

        val cacheSize = calculateDirSize(cacheDir) +
                (externalCacheDir?.let { calculateDirSize(it) } ?: 0) +
                calculateDirSize(filesDir)

        return formatFileSize(cacheSize)
    }

    private fun calculateDirSize(dir: File): Long {
        var size = 0L
        if (dir.exists()) {
            dir.listFiles()?.forEach { file ->
                size += if (file.isDirectory) {
                    calculateDirSize(file)
                } else {
                    file.length()
                }
            }
        }
        return size
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
            else -> "${size / (1024 * 1024 * 1024)} GB"
        }
    }

    fun clearCache(onComplete: (String) -> Unit) {
        viewModelScope.launch {
            try {
                application.cacheDir?.deleteRecursively()
                application.externalCacheDir?.deleteRecursively()
                application.filesDir.listFiles()?.forEach { file ->
                    if (file.name != "avatars") {
                        file.deleteRecursively()
                    }
                }
                calculateCacheSize()
                onComplete("缓存已清除")
            } catch (e: Exception) {
                onComplete("清除缓存失败: ${e.message}")
            }
        }
    }

    private fun getAiDefaultIdentity(): String {
        return secureStorage.getString(KEY_AI_DEFAULT_IDENTITY, DEFAULT_AI_IDENTITY)
            ?: DEFAULT_AI_IDENTITY
    }

    private fun getAiDefaultTone(): String {
        return secureStorage.getString(KEY_AI_DEFAULT_TONE, DEFAULT_AI_TONE)
            ?: DEFAULT_AI_TONE
    }

    private companion object {
        const val DEFAULT_AI_IDENTITY = "朋友"
        const val DEFAULT_AI_TONE = "自然、礼貌、有边界"
        const val KEY_AI_DEFAULT_IDENTITY = "profile_ai_default_identity"
        const val KEY_AI_DEFAULT_TONE = "profile_ai_default_tone"
    }
}
