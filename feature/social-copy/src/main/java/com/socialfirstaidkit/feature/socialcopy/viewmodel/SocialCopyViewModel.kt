package com.socialfirstaidkit.feature.socialcopy.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.socialfirstaidkit.core.data.local.ApiCallTracker
import com.socialfirstaidkit.core.data.local.SecureStorage
import com.socialfirstaidkit.core.data.personalexpression.PersonalExpressionFeedbackInput
import com.socialfirstaidkit.core.data.personalexpression.PersonalExpressionPromptContext
import com.socialfirstaidkit.core.data.personalexpression.PersonalExpressionRepository
import com.socialfirstaidkit.core.data.personalexpression.PersonalExpressionUsageRepository
import com.socialfirstaidkit.core.data.stats.StatsManager
import com.socialfirstaidkit.core.llm.api.LlmClient
import com.socialfirstaidkit.core.llm.model.MomentCopySuggestion
import com.socialfirstaidkit.core.llm.model.MomentImageAnalysis
import com.socialfirstaidkit.core.llm.routing.AiFunctionSlot
import com.socialfirstaidkit.core.llm.routing.AiRouteStore
import com.socialfirstaidkit.core.llm.routing.AiModelRegistry
import com.socialfirstaidkit.core.llm.routing.AiModelRoute
import com.socialfirstaidkit.core.llm.routing.AiProviderId
import com.socialfirstaidkit.core.llm.routing.AiModelSpec
import com.socialfirstaidkit.core.llm.routing.CustomOpenAiProfiles
import com.socialfirstaidkit.core.network.imagegen.ImageGenerationClient
import com.socialfirstaidkit.feature.socialcopy.ImageAnalyzer
import com.socialfirstaidkit.feature.socialcopy.model.DEFAULT_MOMENT_IMAGE_STYLE_ID
import com.socialfirstaidkit.feature.socialcopy.model.DEFAULT_SOCIAL_COPY_STYLE_ID
import com.socialfirstaidkit.feature.socialcopy.model.MomentImageStyleOption
import com.socialfirstaidkit.feature.socialcopy.model.SocialCopyHistory
import com.socialfirstaidkit.feature.socialcopy.model.SocialCopyStyleOption
import com.socialfirstaidkit.feature.socialcopy.model.buildSocialCopyStyleConfig
import com.socialfirstaidkit.feature.socialcopy.model.defaultMomentImageStyleOptions
import com.socialfirstaidkit.feature.socialcopy.model.sortedForDisplay
import com.socialfirstaidkit.feature.socialcopy.model.sortedImageStylesForDisplay
import com.socialfirstaidkit.feature.socialcopy.repository.SocialCopyHistoryRepository
import com.socialfirstaidkit.feature.socialcopy.repository.SocialCopyStyleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File
import java.net.URL
import java.util.UUID
import javax.inject.Inject

private const val TAG = "SocialCopy"
private const val SOCIAL_COPY_IMAGE_SCOPE = "social_copy_image"

/**
 * 朋友圈创作 ViewModel
 */
@HiltViewModel
class SocialCopyViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageAnalyzer: ImageAnalyzer,
    private val llmClient: LlmClient,
    private val statsManager: StatsManager,
    private val imageGenerationClient: ImageGenerationClient,
    private val apiCallTracker: ApiCallTracker,
    private val aiRouteStore: AiRouteStore,
    private val styleRepository: SocialCopyStyleRepository,
    private val historyRepository: SocialCopyHistoryRepository,
    private val secureStorage: SecureStorage,
    private val expressionRepository: PersonalExpressionRepository,
    private val expressionUsageRepository: PersonalExpressionUsageRepository
) : ViewModel() {

    data class SocialCopyUiState(
        val imageUri: Uri? = null,
        val analysis: MomentImageAnalysis? = null,
        val suggestions: List<MomentCopySuggestion> = emptyList(),
        val selectedSuggestionIndex: Int = 0,
        val generatedImageUrl: String? = null,
        val isAnalyzingImage: Boolean = false,
        val isGeneratingCopy: Boolean = false,
        val isGeneratingImage: Boolean = false,
        val errorMessage: String? = null
    ) {
        val selectedSuggestion: MomentCopySuggestion?
            get() = suggestions.getOrNull(selectedSuggestionIndex) ?: suggestions.firstOrNull()
    }

    private val _uiState = MutableStateFlow(SocialCopyUiState())
    val uiState: StateFlow<SocialCopyUiState> = _uiState.asStateFlow()

    private var currentLocalAnalysis: ImageAnalyzer.ImageAnalysisResult? = null

    private val _styleOptions = MutableStateFlow<List<SocialCopyStyleOption>>(emptyList())
    val styleOptions: StateFlow<List<SocialCopyStyleOption>> = _styleOptions.asStateFlow()

    private val _deletedStyleOptions = MutableStateFlow<List<SocialCopyStyleOption>>(emptyList())
    val deletedStyleOptions: StateFlow<List<SocialCopyStyleOption>> = _deletedStyleOptions.asStateFlow()

    private val _selectedStyleId = MutableStateFlow(DEFAULT_SOCIAL_COPY_STYLE_ID)
    val selectedStyleId: StateFlow<String> = _selectedStyleId.asStateFlow()

    private val _imageStyleOptions = MutableStateFlow(defaultMomentImageStyleOptions())
    val imageStyleOptions: StateFlow<List<MomentImageStyleOption>> = _imageStyleOptions.asStateFlow()

    private val _deletedImageStyleOptions = MutableStateFlow<List<MomentImageStyleOption>>(emptyList())
    val deletedImageStyleOptions: StateFlow<List<MomentImageStyleOption>> = _deletedImageStyleOptions.asStateFlow()

    private val _selectedImageStyleId = MutableStateFlow(DEFAULT_MOMENT_IMAGE_STYLE_ID)
    val selectedImageStyleId: StateFlow<String> = _selectedImageStyleId.asStateFlow()

    private val _additionalInfo = MutableStateFlow("")
    val additionalInfo: StateFlow<String> = _additionalInfo.asStateFlow()
    private var isTextOnlyInputMode: Boolean = false
    private var savedImageAdditionalInfo: String = ""
    private var savedTextAdditionalInfo: String = ""

    private val _history = MutableStateFlow<List<SocialCopyHistory>>(emptyList())
    val history: StateFlow<List<SocialCopyHistory>> = _history.asStateFlow()

    private val _currentImageModel = MutableStateFlow(
        aiRouteStore.getRoute(AiFunctionSlot.MOMENT_SHARE_IMAGE_GENERATION).modelId
    )
    val currentImageModel: StateFlow<String> = _currentImageModel.asStateFlow()

    val availableImageModels: List<AiModelSpec>
        get() = AiModelRegistry.modelsForSlot(AiFunctionSlot.MOMENT_SHARE_IMAGE_GENERATION)

    fun updateImageModel(modelId: String) {
        if (aiRouteStore.saveRouteByModelId(AiFunctionSlot.MOMENT_SHARE_IMAGE_GENERATION, modelId)) {
            _currentImageModel.value = modelId
        }
    }

    // Saved state for Image Mode
    private var savedImageUri: Uri? = null
    private var savedAnalysis: MomentImageAnalysis? = null
    private var savedLocalAnalysis: ImageAnalyzer.ImageAnalysisResult? = null
    private var savedImageSuggestions: List<MomentCopySuggestion> = emptyList()
    private var savedImageSelectedSuggestionIndex: Int = 0
    private var savedGeneratedImageUrl: String? = null

    // Saved state for Text Mode
    private var savedTextSuggestions: List<MomentCopySuggestion> = emptyList()
    private var savedTextSelectedSuggestionIndex: Int = 0

    private var currentHistoryRecord: SocialCopyHistory? = null

    private fun saveCurrentImageModeState() {
        val state = _uiState.value
        savedImageUri = state.imageUri
        savedAnalysis = state.analysis
        savedLocalAnalysis = currentLocalAnalysis
        savedImageSuggestions = state.suggestions
        savedImageSelectedSuggestionIndex = state.selectedSuggestionIndex
        savedGeneratedImageUrl = state.generatedImageUrl
    }

    private fun saveCurrentTextModeState() {
        val state = _uiState.value
        savedTextSuggestions = state.suggestions
        savedTextSelectedSuggestionIndex = state.selectedSuggestionIndex
    }

    private fun restoreSavedImageModeState() {
        currentLocalAnalysis = savedLocalAnalysis
        _uiState.value = SocialCopyUiState(
            imageUri = savedImageUri,
            analysis = savedAnalysis,
            suggestions = savedImageSuggestions,
            selectedSuggestionIndex = savedImageSelectedSuggestionIndex,
            generatedImageUrl = savedGeneratedImageUrl
        )
    }

    private fun restoreSavedTextModeState() {
        currentLocalAnalysis = ImageAnalyzer.ImageAnalysisResult(
            ocrText = "",
            labels = emptyList(),
            description = "纯文字朋友圈文案生成，仅基于文字输入",
            imageBase64 = null
        )
        _uiState.value = SocialCopyUiState(
            imageUri = null,
            analysis = MomentImageAnalysis(
                subject = "文字生成",
                scene = "灵感随笔",
                mood = "自在",
                themes = listOf("纯文字创作"),
                insight = "用户未提供图片，完全基于文本信息和所选风格生成文案。"
            ),
            suggestions = savedTextSuggestions,
            selectedSuggestionIndex = savedTextSelectedSuggestionIndex,
            generatedImageUrl = null
        )
    }

    private fun clearAllSavedStates() {
        savedImageUri = null
        savedAnalysis = null
        savedLocalAnalysis = null
        savedImageSuggestions = emptyList()
        savedImageSelectedSuggestionIndex = 0
        savedGeneratedImageUrl = null

        savedTextSuggestions = emptyList()
        savedTextSelectedSuggestionIndex = 0

        savedImageAdditionalInfo = ""
        savedTextAdditionalInfo = ""
        isTextOnlyInputMode = false

        currentHistoryRecord = null
    }

    init {
        loadStyles()
        loadHistory()
    }

    fun processImage(imageUri: Uri) {
        viewModelScope.launch {
            currentLocalAnalysis = null
            _uiState.value = SocialCopyUiState(
                imageUri = imageUri,
                isAnalyzingImage = true
            )

            try {
                val localAnalysis = imageAnalyzer.analyzeImage(imageUri)
                currentLocalAnalysis = localAnalysis
                val momentAnalysis = llmClient.analyzeMomentImage(
                    imageDescription = localAnalysis.description,
                    imageBase64 = localAnalysis.imageBase64
                )

                _uiState.update {
                    it.copy(
                        analysis = momentAnalysis,
                        isAnalyzingImage = false,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "朋友圈图片分析失败", e)
                _uiState.update {
                    it.copy(
                        analysis = buildLocalFallbackAnalysis(),
                        isAnalyzingImage = false,
                        errorMessage = "图片识别不完整，已使用基础识别结果继续创作"
                    )
                }
            }
        }
    }

    fun setSelectedStyleId(styleId: String) {
        _selectedStyleId.value = styleId
        _uiState.update {
            it.copy(
                suggestions = emptyList(),
                selectedSuggestionIndex = 0,
                errorMessage = null
            )
        }
    }

    fun setSelectedImageStyleId(styleId: String) {
        _selectedImageStyleId.value = styleId
        _uiState.update {
            it.copy(
                generatedImageUrl = null,
                errorMessage = null
            )
        }
    }

    fun selectSuggestion(index: Int) {
        _uiState.update {
            it.copy(selectedSuggestionIndex = index.coerceIn(0, (it.suggestions.size - 1).coerceAtLeast(0)))
        }
    }

    fun setAdditionalInfo(info: String) {
        _additionalInfo.value = info
        if (isTextOnlyInputMode) {
            savedTextAdditionalInfo = info
        } else {
            savedImageAdditionalInfo = info
        }
    }

    fun generateMomentCopy() {
        viewModelScope.launch {
            val localAnalysis = currentLocalAnalysis
            val momentAnalysis = _uiState.value.analysis
            if (localAnalysis == null || momentAnalysis == null) {
                _uiState.update { it.copy(errorMessage = "请先选择一张图片并等待识别完成") }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isGeneratingCopy = true,
                    errorMessage = null
                )
            }

            try {
                val styleConfig = buildSocialCopyStyleConfig(_selectedStyleId.value, _styleOptions.value)
                val result = llmClient.generateMomentCopy(
                    imageDescription = localAnalysis.description,
                    analysis = momentAnalysis,
                    imageBase64 = localAnalysis.imageBase64,
                    styleName = styleConfig.styleName,
                    styleDescription = styleConfig.styleDescription,
                    additionalInfo = _additionalInfo.value.ifBlank { null },
                    personalExpression = currentPersonalExpressionContext()
                )
                val suggestions = result.suggestions
                    .filter { it.content.isNotBlank() }
                    .take(4)

                if (suggestions.isEmpty()) {
                    throw IllegalStateException("没有生成可用文案，请重试")
                }
                val failure = suggestions.firstOrNull()?.content?.startsWith("（生成失败") == true
                if (failure) {
                    throw IllegalStateException(suggestions.first().content)
                }

                statsManager.incrementCopiesGenerated()
                val savedRecord = historyRepository.saveHistory(
                    isTextOnly = _uiState.value.imageUri == null,
                    imageUriString = _uiState.value.imageUri?.toString(),
                    imageDescription = localAnalysis.description,
                    analysis = momentAnalysis,
                    styleName = styleConfig.styleName,
                    styleDescription = styleConfig.styleDescription,
                    additionalInfo = _additionalInfo.value,
                    suggestions = suggestions
                )
                currentHistoryRecord = savedRecord
                loadHistory()
                _uiState.update {
                    it.copy(
                        suggestions = suggestions,
                        selectedSuggestionIndex = 0,
                        isGeneratingCopy = false,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "朋友圈文案创作失败", e)
                _uiState.update {
                    it.copy(
                        isGeneratingCopy = false,
                        errorMessage = e.message ?: "文案创作失败，请重试"
                    )
                }
            }
        }
    }


    private data class ImageGenerationRouteConfig(
        val model: String,
        val openAiBaseUrl: String? = null,
        val openAiApiKey: String? = null
    )

    private fun AiModelRoute.toImageGenerationRouteConfig(): ImageGenerationRouteConfig {
        if (provider != AiProviderId.CUSTOM_OPENAI) {
            return ImageGenerationRouteConfig(model = modelName)
        }
        val profileId = CustomOpenAiProfiles.profileIdFromModelId(modelId)
        val profile = CustomOpenAiProfiles.find(secureStorage, profileId)
        return ImageGenerationRouteConfig(
            model = modelName.ifBlank { profile?.model.orEmpty() },
            openAiBaseUrl = profile?.baseUrl,
            openAiApiKey = profile?.apiKey
        )
    }
    fun generateStyledImage() {
        viewModelScope.launch {
            val localAnalysis = currentLocalAnalysis
            val momentAnalysis = _uiState.value.analysis
            val imageBase64 = localAnalysis?.imageBase64?.takeIf { it.isNotBlank() }
            if (imageBase64 == null || momentAnalysis == null) {
                _uiState.update { it.copy(errorMessage = "请先选择一张图片并等待识别完成") }
                return@launch
            }

            val imageStyle = _imageStyleOptions.value.firstOrNull { it.id == _selectedImageStyleId.value }
                ?: _imageStyleOptions.value.sortedImageStylesForDisplay().firstOrNull()
            if (imageStyle == null) {
                _uiState.update { it.copy(errorMessage = "请先新增一个图片加工风格") }
                return@launch
            }
            val (canCall, errorMsg) = apiCallTracker.canMakeCall(SOCIAL_COPY_IMAGE_SCOPE)
            if (!canCall) {
                _uiState.update {
                    it.copy(errorMessage = errorMsg?.ifBlank { "今日图片生成次数已达上限" } ?: "今日图片生成次数已达上限")
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isGeneratingImage = true,
                    errorMessage = null
                )
            }

            try {
                val imageRoute = aiRouteStore.getRoute(AiFunctionSlot.MOMENT_SHARE_IMAGE_GENERATION)
                val imageRouteConfig = imageRoute.toImageGenerationRouteConfig()
                val selectedCopy = _uiState.value.selectedSuggestion?.content?.takeIf { it.isNotBlank() }
                val result = imageGenerationClient.generateStyledMomentImage(
                    originalImageBase64 = imageBase64,
                    analysisSummary = momentAnalysis.toPromptSummary(),
                    additionalInfo = _additionalInfo.value.ifBlank { null },
                    copyText = selectedCopy,
                    styleName = imageStyle.name,
                    stylePrompt = imageStyle.prompt,
                    model = imageRouteConfig.model,
                    openAiBaseUrl = imageRouteConfig.openAiBaseUrl,
                    openAiApiKey = imageRouteConfig.openAiApiKey
                )

                val imageUrl = result.getOrElse { error ->
                    throw IllegalStateException(error.message ?: "Failed to generate share image", error)
                }
                val previewImageUrl = cacheGeneratedImageForPreview(imageUrl)
                apiCallTracker.recordCall(SOCIAL_COPY_IMAGE_SCOPE)
                _uiState.update {
                    it.copy(
                        generatedImageUrl = previewImageUrl,
                        isGeneratingImage = false,
                        errorMessage = null
                    )
                }
                currentHistoryRecord?.let { record ->
                    historyRepository.updateHistoryGeneratedImageUrl(record.id, previewImageUrl)
                    loadHistory()
                }
            } catch (e: Exception) {
                Log.e(TAG, "朋友圈分享图生成失败", e)
                _uiState.update {
                    it.copy(
                        isGeneratingImage = false,
                        errorMessage = e.message ?: "分享图生成失败，请重试"
                    )
                }
            }
        }
    }

    fun addCustomStyle(name: String, description: String) {
        val option = SocialCopyStyleOption(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            description = description.trim(),
            order = _styleOptions.value.size
        )
        val updated = (_styleOptions.value + option).mapIndexed { index, item -> item.copy(order = index) }
            .sortedForDisplay()
        _styleOptions.value = updated
        styleRepository.saveStyleOptions(updated)
        setSelectedStyleId(option.id)
    }

    fun updateCustomStyle(id: String, name: String, description: String) {
        val updated = _styleOptions.value.map { option ->
            if (option.id == id) {
                option.copy(name = name.trim(), description = description.trim())
            } else {
                option
            }
        }.sortedForDisplay()
        _styleOptions.value = updated
        styleRepository.saveStyleOptions(updated)
        if (_selectedStyleId.value == id) {
            setSelectedStyleId(id)
        }
    }

    fun deleteCustomStyle(id: String) {
        val deletingSelected = _selectedStyleId.value == id
        val deletingOption = _styleOptions.value.firstOrNull { it.id == id }
        val updated = _styleOptions.value.filterNot { it.id == id }
            .mapIndexed { index, option -> option.copy(order = index) }
            .sortedForDisplay()
        _styleOptions.value = updated
        styleRepository.saveStyleOptions(updated)
        if (deletingOption != null) {
            val deleted = (listOf(deletingOption.copy(createdAt = System.currentTimeMillis())) + _deletedStyleOptions.value)
                .distinctBy { it.id }
            _deletedStyleOptions.value = deleted
            styleRepository.saveDeletedStyleOptions(deleted)
        }
        if (deletingSelected) {
            val nextSelectedId = updated.firstOrNull()?.id ?: DEFAULT_SOCIAL_COPY_STYLE_ID
            setSelectedStyleId(nextSelectedId)
        }
    }

    fun restoreDeletedStyle(id: String) {
        val option = _deletedStyleOptions.value.firstOrNull { it.id == id } ?: return
        val active = (_styleOptions.value + option.copy(order = _styleOptions.value.size))
            .distinctBy { it.id }
            .sortedForDisplay()
        val deleted = _deletedStyleOptions.value.filterNot { it.id == id }
        _styleOptions.value = active
        _deletedStyleOptions.value = deleted
        styleRepository.saveStyleOptions(active)
        styleRepository.saveDeletedStyleOptions(deleted)
    }

    fun permanentlyDeleteStyle(id: String) {
        val deleted = _deletedStyleOptions.value.filterNot { it.id == id }
        _deletedStyleOptions.value = deleted
        styleRepository.saveDeletedStyleOptions(deleted)
    }

    fun moveStyleOption(fromIndex: Int, toIndex: Int) {
        val current = _styleOptions.value.sortedForDisplay()
        if (fromIndex !in current.indices || toIndex !in current.indices || fromIndex == toIndex) return

        val mutable = current.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        val updated = mutable.mapIndexed { index, option -> option.copy(order = index) }
        _styleOptions.value = updated
        styleRepository.saveStyleOptions(updated)
    }

    fun addCustomImageStyle(name: String, description: String, prompt: String) {
        val option = MomentImageStyleOption(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            description = description.trim(),
            prompt = prompt.trim(),
            order = _imageStyleOptions.value.size
        )
        val updated = (_imageStyleOptions.value + option)
            .mapIndexed { index, item -> item.copy(order = index) }
            .sortedImageStylesForDisplay()
        _imageStyleOptions.value = updated
        styleRepository.saveImageStyleOptions(updated)
        setSelectedImageStyleId(option.id)
    }

    fun updateCustomImageStyle(id: String, name: String, description: String, prompt: String) {
        val updated = _imageStyleOptions.value.map { option ->
            if (option.id == id) {
                option.copy(
                    name = name.trim(),
                    description = description.trim(),
                    prompt = prompt.trim()
                )
            } else {
                option
            }
        }.sortedImageStylesForDisplay()
        _imageStyleOptions.value = updated
        styleRepository.saveImageStyleOptions(updated)
        if (_selectedImageStyleId.value == id) {
            setSelectedImageStyleId(id)
        }
    }

    fun deleteCustomImageStyle(id: String) {
        val deletingSelected = _selectedImageStyleId.value == id
        val deletingOption = _imageStyleOptions.value.firstOrNull { it.id == id }
        val updated = _imageStyleOptions.value.filterNot { it.id == id }
            .mapIndexed { index, option -> option.copy(order = index) }
            .sortedImageStylesForDisplay()
        _imageStyleOptions.value = updated
        styleRepository.saveImageStyleOptions(updated)
        if (deletingOption != null) {
            val deleted = (listOf(deletingOption.copy(createdAt = System.currentTimeMillis())) + _deletedImageStyleOptions.value)
                .distinctBy { it.id }
            _deletedImageStyleOptions.value = deleted
            styleRepository.saveDeletedImageStyleOptions(deleted)
        }
        if (deletingSelected) {
            setSelectedImageStyleId(updated.firstOrNull()?.id ?: DEFAULT_MOMENT_IMAGE_STYLE_ID)
        }
    }

    fun restoreDeletedImageStyle(id: String) {
        val option = _deletedImageStyleOptions.value.firstOrNull { it.id == id } ?: return
        val active = (_imageStyleOptions.value + option.copy(order = _imageStyleOptions.value.size))
            .distinctBy { it.id }
            .sortedImageStylesForDisplay()
        val deleted = _deletedImageStyleOptions.value.filterNot { it.id == id }
        _imageStyleOptions.value = active
        _deletedImageStyleOptions.value = deleted
        styleRepository.saveImageStyleOptions(active)
        styleRepository.saveDeletedImageStyleOptions(deleted)
    }

    fun permanentlyDeleteImageStyle(id: String) {
        val deleted = _deletedImageStyleOptions.value.filterNot { it.id == id }
        _deletedImageStyleOptions.value = deleted
        styleRepository.saveDeletedImageStyleOptions(deleted)
    }

    fun moveImageStyleOption(fromIndex: Int, toIndex: Int) {
        val current = _imageStyleOptions.value.sortedImageStylesForDisplay()
        if (fromIndex !in current.indices || toIndex !in current.indices || fromIndex == toIndex) return

        val mutable = current.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        val updated = mutable.mapIndexed { index, option -> option.copy(order = index) }
        _imageStyleOptions.value = updated
        styleRepository.saveImageStyleOptions(updated)
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun reset() {
        setupImageMode(preserveInput = false)
    }

    fun setupImageMode(preserveInput: Boolean = true) {
        if (!preserveInput) {
            clearAllSavedStates()
            _additionalInfo.value = ""
            _selectedStyleId.value = DEFAULT_SOCIAL_COPY_STYLE_ID
            _selectedImageStyleId.value = DEFAULT_MOMENT_IMAGE_STYLE_ID
            currentLocalAnalysis = null
            _uiState.value = SocialCopyUiState()
        } else {
            if (isTextOnlyInputMode) savedTextAdditionalInfo = _additionalInfo.value
            saveCurrentTextModeState()
            isTextOnlyInputMode = false
            _additionalInfo.value = savedImageAdditionalInfo
            restoreSavedImageModeState()
        }
    }

    fun setupTextOnlyMode(preserveInput: Boolean = true) {
        if (!preserveInput) {
            clearAllSavedStates()
            _additionalInfo.value = ""
            isTextOnlyInputMode = true
            restoreSavedTextModeState()
        } else {
            if (!isTextOnlyInputMode) savedImageAdditionalInfo = _additionalInfo.value
            saveCurrentImageModeState()
            isTextOnlyInputMode = true
            _additionalInfo.value = savedTextAdditionalInfo
            restoreSavedTextModeState()
        }
    }

    fun updateSuggestionContent(index: Int, newContent: String) {
        _uiState.update { state ->
            val updatedList = state.suggestions.mapIndexed { i, item ->
                if (i == index) item.copy(content = newContent) else item
            }
            state.copy(suggestions = updatedList)
        }
    }

    fun recordCopiedSuggestion(copiedText: String) {
        if (copiedText.isBlank() || !expressionUsageRepository.isSocialCopyEnabled()) return
        val state = _uiState.value
        val analysis = state.analysis ?: return
        val selected = state.selectedSuggestion
        val styleConfig = buildSocialCopyStyleConfig(_selectedStyleId.value, _styleOptions.value)

        viewModelScope.launch {
            val profile = expressionRepository.getProfile()
            if (profile?.enabled != true || !profile.questionnaireCompleted) return@launch
            expressionRepository.recordFeedbackSample(
                PersonalExpressionFeedbackInput(
                    source = "social_copy",
                    relation = "moments",
                    style = styleConfig.styleName,
                    scene = analysis.scene,
                    suggestionSlot = selected?.styleName ?: "copy",
                    copiedText = copiedText,
                    sourceMessage = buildString {
                        append(analysis.subject)
                        if (_additionalInfo.value.isNotBlank()) {
                            append(" / ")
                            append(_additionalInfo.value)
                        }
                    }
                )
            )
        }
    }

    fun deleteHistory(history: SocialCopyHistory) {
        viewModelScope.launch {
            historyRepository.deleteHistory(history.id)
            loadHistory()
        }
    }

    fun deleteHistories(histories: List<SocialCopyHistory>) {
        viewModelScope.launch {
            histories.forEach { history ->
                historyRepository.deleteHistory(history.id)
            }
            loadHistory()
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyRepository.clearHistory()
            loadHistory()
        }
    }

    fun updateImageAnalysis(newAnalysis: MomentImageAnalysis) {
        _uiState.update { it.copy(analysis = newAnalysis) }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            _history.value = historyRepository.getHistory()
        }
    }

    private suspend fun cacheGeneratedImageForPreview(imageValue: String): String {
        val original = imageValue.trim()
        if (original.startsWith("file:", ignoreCase = true) || original.startsWith("content:", ignoreCase = true)) {
            return original
        }

        return runCatching {
            withContext(Dispatchers.IO) {
                val bytes = when {
                    original.startsWith("data:image/", ignoreCase = true) -> {
                        val payload = original.substringAfter(',', missingDelimiterValue = "")
                        if (payload.isBlank()) return@withContext original
                        Base64.decode(payload, Base64.DEFAULT)
                    }
                    original.startsWith("http://", ignoreCase = true) || original.startsWith("https://", ignoreCase = true) -> {
                        val connection = URL(original).openConnection().apply {
                            connectTimeout = 30_000
                            readTimeout = 90_000
                        }
                        connection.getInputStream().use { it.readBytes() }
                    }
                    else -> return@withContext original
                }

                val cacheDir = File(context.cacheDir, "moment_share_images").apply { mkdirs() }
                val file = File(cacheDir, "moment_share_${System.currentTimeMillis()}.${imageExtensionFor(original)}")
                file.writeBytes(bytes)
                file.toURI().toString()
            }
        }.getOrElse { error ->
            Log.w(TAG, "Failed to cache share image, using original URL", error)
            original
        }
    }

    private fun imageExtensionFor(value: String): String {
        return when {
            value.startsWith("data:image/png", ignoreCase = true) || value.contains(".png", ignoreCase = true) -> "png"
            value.startsWith("data:image/webp", ignoreCase = true) || value.contains(".webp", ignoreCase = true) -> "webp"
            else -> "jpg"
        }
    }
    private suspend fun currentPersonalExpressionContext(): PersonalExpressionPromptContext? {
        if (!expressionUsageRepository.isSocialCopyEnabled()) return null
        return expressionRepository.getPromptContext("朋友圈创作")
    }

    private fun loadStyles() {
        val styles = styleRepository.getStyleOptions()
        val imageStyles = styleRepository.getImageStyleOptions()
        _styleOptions.value = styles
        _imageStyleOptions.value = imageStyles
        _deletedStyleOptions.value = styleRepository.getDeletedStyleOptions()
        _deletedImageStyleOptions.value = styleRepository.getDeletedImageStyleOptions()
        if (styles.none { it.id == _selectedStyleId.value }) {
            _selectedStyleId.value = styles.firstOrNull()?.id ?: DEFAULT_SOCIAL_COPY_STYLE_ID
        }
        if (imageStyles.none { it.id == _selectedImageStyleId.value }) {
            _selectedImageStyleId.value = imageStyles.firstOrNull()?.id ?: DEFAULT_MOMENT_IMAGE_STYLE_ID
        }
    }

    private fun buildLocalFallbackAnalysis(): MomentImageAnalysis {
        val local = currentLocalAnalysis
        val normalizedLabels = local?.labels
            ?.mapNotNull { normalizeLocalVisualLabel(it) }
            ?.distinct()
            .orEmpty()
        val hasFoodSignal = normalizedLabels.any { it == "食物" || it == "餐食" || it == "蔬菜" }
        val displayLabels = normalizedLabels.filterNot {
            it in setOf("金属", "物体", "未知内容", "生活照片") ||
                (hasFoodSignal && it == "植物")
        }
        val subject = when {
            hasFoodSignal -> "餐食内容"
            else -> displayLabels.firstOrNull() ?: "画面内容"
        }
        val themes = if (hasFoodSignal) {
            listOf("美食", "日常记录")
        } else {
            displayLabels.take(3).ifEmpty { listOf("日常记录") }
        }
        return MomentImageAnalysis(
            subject = subject,
            scene = "生活场景",
            mood = "自然、真实",
            themes = themes,
            insight = "这张图片适合先确认画面主体后，再用自然的方式记录当下。"
        )
    }

    private fun normalizeLocalVisualLabel(label: String): String? {
        val value = label.trim()
        if (value.isBlank()) return null
        return when (value.lowercase()) {
            "food", "cuisine", "dish", "meal", "vegetable", "vegetables", "produce" -> "食物"
            "metal" -> "金属"
            "animal", "animals", "pet", "pets" -> "动物"
            "fish", "goldfish", "koi", "carp" -> "鱼"
            "plant", "plants", "flower", "flowers" -> "植物"
            "water" -> "水面"
            "sky" -> "天空"
            "tree", "trees" -> "树木"
            "person", "people", "human", "face" -> "人物"
            "vehicle", "car", "transport" -> "交通工具"
            "building", "architecture" -> "建筑"
            "object", "objects" -> "物体"
            "tire", "tyre", "wheel" -> null
            "unknown", "unidentified" -> "未知内容"
            else -> value
        }
    }

    private fun MomentImageAnalysis.toPromptSummary(): String {
        return buildString {
            appendLine("主体：$subject")
            appendLine("场景：$scene")
            appendLine("氛围：$mood")
            appendLine("主题：${themes.joinToString("、").ifBlank { "日常记录" }}")
            append("发布理解：$insight")
        }
    }
}
