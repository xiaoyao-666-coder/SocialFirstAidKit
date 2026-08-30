package com.socialfirstaidkit.feature.chatassist.ui.newarch

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.socialfirstaidkit.core.data.local.dao.UserProfileDao
import com.socialfirstaidkit.core.data.model.OcrResult
import com.socialfirstaidkit.core.data.personalexpression.PersonalExpressionProfile
import com.socialfirstaidkit.core.data.local.entity.ContactEntity
import com.socialfirstaidkit.core.data.local.entity.ChatSessionEntity
import com.socialfirstaidkit.core.data.local.entity.ChatMessageEntity
import com.socialfirstaidkit.feature.chatassist.model.ContactWithLastMessage
import com.socialfirstaidkit.feature.chatassist.model.DuplicateCheckResult
import com.socialfirstaidkit.feature.chatassist.model.OcrHistory
import com.socialfirstaidkit.feature.chatassist.model.SessionWithInfo
import com.socialfirstaidkit.feature.chatassist.model.SingleReplyHistory
import com.socialfirstaidkit.feature.chatassist.repository.NewChatRepository
import com.socialfirstaidkit.feature.chatassist.repository.MessageAssistCustomizationRepository
import com.socialfirstaidkit.feature.chatassist.repository.OcrHistoryRepository
import com.socialfirstaidkit.feature.chatassist.repository.SingleReplyHistoryRepository
import com.socialfirstaidkit.feature.chatassist.model.CustomMessageAssistOption
import com.socialfirstaidkit.feature.chatassist.model.DEFAULT_RELATION_ID
import com.socialfirstaidkit.feature.chatassist.model.DEFAULT_STYLE_ID
import com.socialfirstaidkit.feature.chatassist.model.buildRelationPromptContext
import com.socialfirstaidkit.feature.chatassist.model.buildStyleConfig
import com.socialfirstaidkit.feature.chatassist.model.sortedForDisplay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

import com.socialfirstaidkit.feature.chatassist.ocr.GptScreenshotOcrProcessor
import com.socialfirstaidkit.core.llm.api.MessageAssistClient
import com.socialfirstaidkit.core.llm.api.ReplyStyle
import com.socialfirstaidkit.core.llm.api.ScreenshotAnalysisResult
import com.socialfirstaidkit.core.llm.api.SingleMessageContextAnalysis
import com.socialfirstaidkit.core.llm.model.ChatSession
import com.socialfirstaidkit.core.llm.model.ChatMessage
import com.socialfirstaidkit.core.llm.model.ReplySuggestions
import com.socialfirstaidkit.core.llm.model.SenderType
import com.socialfirstaidkit.feature.chatassist.viewmodel.MessageAssistUiState
import com.socialfirstaidkit.feature.chatassist.viewmodel.ConversationContextAnalysis
import com.socialfirstaidkit.feature.chatassist.viewmodel.ReplyMode
import com.socialfirstaidkit.core.data.personalexpression.PersonalExpressionFeedbackInput
import com.socialfirstaidkit.core.data.stats.StatsManager
import com.socialfirstaidkit.core.data.personalexpression.PersonalExpressionRepository
import com.socialfirstaidkit.core.data.personalexpression.PersonalExpressionUsageRepository
import com.socialfirstaidkit.feature.chatassist.voice.ShortVoiceAudioRecorder
import com.socialfirstaidkit.feature.chatassist.voice.ShortVoiceInputResult
import com.socialfirstaidkit.feature.chatassist.voice.VivoShortVoiceInputEngine

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NewChatViewModel @Inject constructor(
    private val repository: NewChatRepository,
    private val customizationRepository: MessageAssistCustomizationRepository,
    private val screenshotOcrProcessor: GptScreenshotOcrProcessor,
    private val messageAssistClient: MessageAssistClient,
    private val statsManager: StatsManager,
    private val expressionRepository: PersonalExpressionRepository,
    private val expressionUsageRepository: PersonalExpressionUsageRepository,
    private val singleReplyHistoryRepository: SingleReplyHistoryRepository,
    private val ocrHistoryRepository: OcrHistoryRepository,
    private val userProfileDao: UserProfileDao,
    private val voiceInputEngine: VivoShortVoiceInputEngine,
    private val voiceAudioRecorder: ShortVoiceAudioRecorder
) : ViewModel() {

    private companion object {
        const val OCR_HISTORY_MAX_SIZE = 10
        const val OCR_SIMILARITY_THRESHOLD = 0.7f
    }

    // 1. 联系人列表流 (包含最后一条消息)
    val contactsWithLastMessage: StateFlow<List<ContactWithLastMessage>> = repository.getAllContactsFlow()
        .flatMapLatest { contacts ->
            if (contacts.isEmpty()) return@flatMapLatest flowOf(emptyList())
            val flows = contacts.map { contact ->
                repository.getSessionsByContact(contact.id).map { sessions ->
                    val lastSession = sessions.firstOrNull()
                    val lastMessage = lastSession?.let { repository.getMessagesSuspend(it.sessionId).lastOrNull() }
                    ContactWithLastMessage(
                        contact = contact,
                        lastMessageText = lastMessage?.content ?: "暂无会话消息",
                        lastMessageTime = lastMessage?.timestamp ?: contact.lastInteraction
                    )
                }
            }
            combine(flows) { it.toList().sortedByDescending { it.lastMessageTime } }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // 2. 当前选中的联系人 ID
    private val _selectedContactId = MutableStateFlow<String?>(null)
    val selectedContactId: StateFlow<String?> = _selectedContactId.asStateFlow()

    val currentContact: StateFlow<ContactEntity?> = _selectedContactId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else repository.getContactFlow(id)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    // 3. 当前联系人的所有会话列表
    val sessionsForSelectedContact: StateFlow<List<SessionWithInfo>> = _selectedContactId
        .flatMapLatest { contactId ->
            if (contactId == null) flowOf(emptyList())
            else repository.getSessionsByContact(contactId).flatMapLatest { sessions ->
                if (sessions.isEmpty()) return@flatMapLatest flowOf(emptyList())
                val flows = sessions.map { session ->
                    flowOf(Unit).map {
                        val messages = repository.getMessagesSuspend(session.sessionId)
                        val lastMessage = messages.lastOrNull()
                        SessionWithInfo(
                            session = session,
                            messageCount = messages.size,
                            lastMessageText = lastMessage?.content ?: "暂无消息"
                        )
                    }
                }
                combine(flows) { it.toList() }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // 4. 当前选中的会话 ID
    private val _selectedSessionId = MutableStateFlow<String?>(null)
    val selectedSessionId: StateFlow<String?> = _selectedSessionId.asStateFlow()

    val currentSession: StateFlow<ChatSessionEntity?> = _selectedSessionId
        .flatMapLatest { sid ->
            if (sid == null) flowOf(null)
            else flowOf(repository.getSession(sid))
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    // 5. 当前会话的所有消息气泡流
    val messagesForSelectedSession: StateFlow<List<ChatMessageEntity>> = _selectedSessionId
        .flatMapLatest { sessionId ->
            if (sessionId == null) flowOf(emptyList())
            else repository.getMessagesFlow(sessionId)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // 6. 配置项、首页单文本与历史状态
    private val _customRelationOptions = MutableStateFlow<List<CustomMessageAssistOption>>(emptyList())
    val customRelationOptions: StateFlow<List<CustomMessageAssistOption>> = _customRelationOptions.asStateFlow()

    private val _deletedRelationOptions = MutableStateFlow<List<CustomMessageAssistOption>>(emptyList())
    val deletedRelationOptions: StateFlow<List<CustomMessageAssistOption>> = _deletedRelationOptions.asStateFlow()

    private val _customStyleOptions = MutableStateFlow<List<CustomMessageAssistOption>>(emptyList())
    val customStyleOptions: StateFlow<List<CustomMessageAssistOption>> = _customStyleOptions.asStateFlow()

    private val _deletedStyleOptions = MutableStateFlow<List<CustomMessageAssistOption>>(emptyList())
    val deletedStyleOptions: StateFlow<List<CustomMessageAssistOption>> = _deletedStyleOptions.asStateFlow()

    private val _replyMode = MutableStateFlow(ReplyMode.SINGLE_TEXT)
    val replyMode: StateFlow<ReplyMode> = _replyMode.asStateFlow()

    private val _selectedRelationId = MutableStateFlow(DEFAULT_RELATION_ID)
    val selectedRelationId: StateFlow<String> = _selectedRelationId.asStateFlow()

    private val _selectedStyleId = MutableStateFlow(DEFAULT_STYLE_ID)
    val selectedStyleId: StateFlow<String> = _selectedStyleId.asStateFlow()

    private val _replyStyle = MutableStateFlow<ReplyStyle?>(null)
    val replyStyle: StateFlow<ReplyStyle?> = _replyStyle.asStateFlow()

    private val _customStyleDescription = MutableStateFlow("")
    val customStyleDescription: StateFlow<String> = _customStyleDescription.asStateFlow()

    private val _additionalNotes = MutableStateFlow("")
    val additionalNotes: StateFlow<String> = _additionalNotes.asStateFlow()
    private var singleTextAdditionalNotes: String = ""
    private var contextualAdditionalNotes: String = ""

    private val _singleTextMessage = MutableStateFlow("")
    val singleTextMessage: StateFlow<String> = _singleTextMessage.asStateFlow()

    private val _hasGeneratedReply = MutableStateFlow(false)
    val hasGeneratedReply: StateFlow<Boolean> = _hasGeneratedReply.asStateFlow()

    private val _singleReplyHistory = MutableStateFlow<List<SingleReplyHistory>>(emptyList())
    val singleReplyHistory: StateFlow<List<SingleReplyHistory>> = _singleReplyHistory.asStateFlow()

    private val _singleReplyVersions = MutableStateFlow<List<ReplySuggestions>>(emptyList())
    val singleReplyVersions: StateFlow<List<ReplySuggestions>> = _singleReplyVersions.asStateFlow()

    private val _singleReplyVersionIndex = MutableStateFlow(0)
    val singleReplyVersionIndex: StateFlow<Int> = _singleReplyVersionIndex.asStateFlow()
    private var lastSingleReplyMessage: String = ""

    private val _ocrHistory = MutableStateFlow<List<OcrHistory>>(emptyList())
    val ocrHistory: StateFlow<List<OcrHistory>> = _ocrHistory.asStateFlow()

    private val _expressionProfile = MutableStateFlow<PersonalExpressionProfile?>(null)
    val expressionProfile: StateFlow<PersonalExpressionProfile?> = _expressionProfile.asStateFlow()

    private val _userProfile = MutableStateFlow<com.socialfirstaidkit.core.data.local.entity.UserProfileEntity?>(null)
    val userProfile: StateFlow<com.socialfirstaidkit.core.data.local.entity.UserProfileEntity?> = _userProfile.asStateFlow()
    private val _voiceInputState = MutableStateFlow<VoiceInputState>(VoiceInputState.Idle)
    val voiceInputState: StateFlow<VoiceInputState> = _voiceInputState.asStateFlow()
    private var voiceInputJob: Job? = null
    private var voiceRecordJob: Job? = null
    private var voiceFinalizeJob: Job? = null
    private var voiceInputTextBuffer = ""
    private var voiceInputPartialText = ""
    private var voiceInputFinishing = false
    private var voiceLastFinalNormalizedText = ""
    init {
        loadCustomizations()
        loadSingleReplyHistory()
        loadOcrHistory()
        viewModelScope.launch {
            expressionRepository.observeProfile().collect { _expressionProfile.value = it }
        }
        viewModelScope.launch {
            userProfileDao.getProfile().collect { _userProfile.value = it }
        }
    }

    fun setReplyMode(mode: ReplyMode) {
        if (_replyMode.value == mode) return
        saveAdditionalNotesForCurrentMode()
        _replyMode.value = mode
        _uiState.value = MessageAssistUiState.Idle
        _additionalNotes.value = when (mode) {
            ReplyMode.SINGLE_TEXT -> singleTextAdditionalNotes
            ReplyMode.CONTEXTUAL -> contextualAdditionalNotes
        }
        _hasGeneratedReply.value = false
        if (mode == ReplyMode.CONTEXTUAL) clearSingleReplyVersions()
    }

    fun setSingleTextMessage(message: String) {
        _singleTextMessage.value = message
        clearSingleReplyVersions()
        resetGeneratedFlag()
    }

    fun setRelation(relation: String) {
        _selectedRelationId.value = relation
        clearSingleReplyVersions()
        resetGeneratedFlag()
    }

    fun setReplyStyle(styleId: String) {
        _selectedStyleId.value = styleId
        applySelectedStyle(styleId)
        clearSingleReplyVersions()
        resetGeneratedFlag()
    }

    fun setAdditionalNotes(notes: String) {
        _additionalNotes.value = notes
        when (_replyMode.value) {
            ReplyMode.SINGLE_TEXT -> singleTextAdditionalNotes = notes
            ReplyMode.CONTEXTUAL -> contextualAdditionalNotes = notes
        }
        clearSingleReplyVersions()
        resetGeneratedFlag()
    }

    fun clearAllTextInputs() {
        singleTextAdditionalNotes = ""
        contextualAdditionalNotes = ""
        _additionalNotes.value = ""
        _singleTextMessage.value = ""
        _uiState.value = MessageAssistUiState.Idle
        clearSingleReplyVersions()
        resetGeneratedFlag()
    }

    private fun saveAdditionalNotesForCurrentMode() {
        when (_replyMode.value) {
            ReplyMode.SINGLE_TEXT -> singleTextAdditionalNotes = _additionalNotes.value
            ReplyMode.CONTEXTUAL -> contextualAdditionalNotes = _additionalNotes.value
        }
    }

    private fun loadCustomizations() {
        _customRelationOptions.value = customizationRepository.getRelationOptions()
        _deletedRelationOptions.value = customizationRepository.getDeletedRelationOptions()
        _customStyleOptions.value = customizationRepository.getStyleOptions()
        _deletedStyleOptions.value = customizationRepository.getDeletedStyleOptions()
        if (_customRelationOptions.value.none { it.id == _selectedRelationId.value }) {
            _selectedRelationId.value = _customRelationOptions.value.firstOrNull()?.id ?: DEFAULT_RELATION_ID
        }
        if (_customStyleOptions.value.none { it.id == _selectedStyleId.value }) {
            _selectedStyleId.value = _customStyleOptions.value.firstOrNull()?.id ?: DEFAULT_STYLE_ID
        }
        applySelectedStyle(_selectedStyleId.value)
    }

    fun addCustomRelation(name: String, description: String) {
        val option = CustomMessageAssistOption(UUID.randomUUID().toString(), name.trim(), description.trim(), _customRelationOptions.value.size)
        val updated = (_customRelationOptions.value + option).sortedForDisplay()
        _customRelationOptions.value = updated
        customizationRepository.saveRelationOptions(updated)
    }

    fun updateCustomRelation(optionId: String, name: String, description: String) {
        val updated = _customRelationOptions.value.map { if (it.id == optionId) it.copy(name = name.trim(), description = description.trim()) else it }.sortedForDisplay()
        _customRelationOptions.value = updated
        customizationRepository.saveRelationOptions(updated)
        resetGeneratedFlag()
    }

    fun deleteCustomRelation(optionId: String) {
        val deleting = _customRelationOptions.value.firstOrNull { it.id == optionId }
        val updated = _customRelationOptions.value.filterNot { it.id == optionId }.sortedForDisplay()
        _customRelationOptions.value = updated
        customizationRepository.saveRelationOptions(updated)
        if (deleting != null) {
            val deleted = (listOf(deleting.copy(createdAt = System.currentTimeMillis())) + _deletedRelationOptions.value).distinctBy { it.id }
            _deletedRelationOptions.value = deleted
            customizationRepository.saveDeletedRelationOptions(deleted)
        }
        if (_selectedRelationId.value == optionId) _selectedRelationId.value = updated.firstOrNull()?.id ?: DEFAULT_RELATION_ID
        resetGeneratedFlag()
    }

    fun restoreDeletedRelation(optionId: String) {
        val restoring = _deletedRelationOptions.value.firstOrNull { it.id == optionId } ?: return
        val active = (_customRelationOptions.value + restoring.copy(order = _customRelationOptions.value.size)).distinctBy { it.id }.sortedForDisplay()
        val deleted = _deletedRelationOptions.value.filterNot { it.id == optionId }
        _customRelationOptions.value = active
        _deletedRelationOptions.value = deleted
        customizationRepository.saveRelationOptions(active)
        customizationRepository.saveDeletedRelationOptions(deleted)
    }

    fun permanentlyDeleteRelation(optionId: String) {
        val deleted = _deletedRelationOptions.value.filterNot { it.id == optionId }
        _deletedRelationOptions.value = deleted
        customizationRepository.saveDeletedRelationOptions(deleted)
    }

    fun addCustomStyle(name: String, description: String) {
        val option = CustomMessageAssistOption(UUID.randomUUID().toString(), name.trim(), description.trim(), _customStyleOptions.value.size)
        val updated = (_customStyleOptions.value + option).sortedForDisplay()
        _customStyleOptions.value = updated
        customizationRepository.saveStyleOptions(updated)
    }

    fun updateCustomStyle(optionId: String, name: String, description: String) {
        val updated = _customStyleOptions.value.map { if (it.id == optionId) it.copy(name = name.trim(), description = description.trim()) else it }.sortedForDisplay()
        _customStyleOptions.value = updated
        customizationRepository.saveStyleOptions(updated)
        if (_selectedStyleId.value == optionId) applySelectedStyle(optionId)
        resetGeneratedFlag()
    }

    fun deleteCustomStyle(optionId: String) {
        val deleting = _customStyleOptions.value.firstOrNull { it.id == optionId }
        val updated = _customStyleOptions.value.filterNot { it.id == optionId }.sortedForDisplay()
        _customStyleOptions.value = updated
        customizationRepository.saveStyleOptions(updated)
        if (deleting != null) {
            val deleted = (listOf(deleting.copy(createdAt = System.currentTimeMillis())) + _deletedStyleOptions.value).distinctBy { it.id }
            _deletedStyleOptions.value = deleted
            customizationRepository.saveDeletedStyleOptions(deleted)
        }
        if (_selectedStyleId.value == optionId) {
            _selectedStyleId.value = updated.firstOrNull()?.id ?: DEFAULT_STYLE_ID
            applySelectedStyle(_selectedStyleId.value)
        }
        resetGeneratedFlag()
    }

    fun restoreDeletedStyle(optionId: String) {
        val restoring = _deletedStyleOptions.value.firstOrNull { it.id == optionId } ?: return
        val active = (_customStyleOptions.value + restoring.copy(order = _customStyleOptions.value.size)).distinctBy { it.id }.sortedForDisplay()
        val deleted = _deletedStyleOptions.value.filterNot { it.id == optionId }
        _customStyleOptions.value = active
        _deletedStyleOptions.value = deleted
        customizationRepository.saveStyleOptions(active)
        customizationRepository.saveDeletedStyleOptions(deleted)
    }

    fun permanentlyDeleteStyle(optionId: String) {
        val deleted = _deletedStyleOptions.value.filterNot { it.id == optionId }
        _deletedStyleOptions.value = deleted
        customizationRepository.saveDeletedStyleOptions(deleted)
    }

    fun moveRelationOption(fromIndex: Int, toIndex: Int) {
        val updated = moveOption(_customRelationOptions.value, fromIndex, toIndex)
        _customRelationOptions.value = updated
        customizationRepository.saveRelationOptions(updated)
    }

    fun moveStyleOption(fromIndex: Int, toIndex: Int) {
        val updated = moveOption(_customStyleOptions.value, fromIndex, toIndex)
        _customStyleOptions.value = updated
        customizationRepository.saveStyleOptions(updated)
    }

    suspend fun generateDescription(name: String, isRelationMode: Boolean): String =
        messageAssistClient.generateIdentityDescription(name, isRelationMode)

    private fun applySelectedStyle(styleId: String) {
        val styleConfig = buildStyleConfig(styleId, _customStyleOptions.value)
        _replyStyle.value = styleConfig.replyStyle
        _customStyleDescription.value = styleConfig.customStyleDescription
    }

    private fun moveOption(source: List<CustomMessageAssistOption>, fromIndex: Int, toIndex: Int): List<CustomMessageAssistOption> {
        if (fromIndex == toIndex || fromIndex !in source.indices || toIndex !in source.indices) return source.sortedForDisplay()
        val mutable = source.sortedForDisplay().toMutableList()
        val moved = mutable.removeAt(fromIndex)
        mutable.add(toIndex, moved)
        return mutable.mapIndexed { index, option -> option.copy(order = index) }
    }

    // --- 业务操作方法 ---

    fun selectContact(contactId: String) {
        _selectedContactId.value = contactId
    }

    fun selectSession(sessionId: String) {
        _selectedSessionId.value = sessionId
    }

    // 1. 联系人管理
    fun addContact(name: String, relationType: String = "friend") {
        viewModelScope.launch {
            val contact = ContactEntity(
                id = UUID.randomUUID().toString(),
                displayName = name,
                relationType = relationType,
                intimacyScore = 5,
                lastInteraction = System.currentTimeMillis()
            )
            repository.saveContact(contact)
        }
    }

    fun updateContactProfile(name: String, avatarUri: Uri?) {
        viewModelScope.launch {
            val contactId = _selectedContactId.value ?: return@launch
            val contact = repository.getContact(contactId) ?: return@launch
            var finalAvatarPath = contact.avatarUri
            if (avatarUri != null) {
                finalAvatarPath = repository.saveContactAvatar(contactId, avatarUri, contact.avatarUri)
            }
            val updated = contact.copy(
                displayName = name,
                avatarUri = finalAvatarPath,
                lastInteraction = System.currentTimeMillis()
            )
            repository.saveContact(updated)
        }
    }

    fun updateContactPreferences(relationId: String, styleId: String) {
        viewModelScope.launch {
            val contactId = _selectedContactId.value ?: return@launch
            val contact = contactDaoGet(contactId) ?: return@launch
            val updated = contact.copy(
                defaultRelationId = relationId,
                defaultStyleId = styleId,
                lastInteraction = System.currentTimeMillis()
            )
            repository.saveContact(updated)
        }
    }

    private suspend fun contactDaoGet(contactId: String): ContactEntity? {
        return repository.getContact(contactId)
    }

    fun deleteContact(contact: ContactEntity) {
        viewModelScope.launch {
            repository.deleteContact(contact)
            if (_selectedContactId.value == contact.id) {
                _selectedContactId.value = null
                _selectedSessionId.value = null
            }
        }
    }

    fun deleteContacts(contactIds: List<String>) {
        if (contactIds.isEmpty()) return
        viewModelScope.launch {
            val idSet = contactIds.toSet()
            idSet.forEach { id ->
                repository.getContact(id)?.let { repository.deleteContact(it) }
            }
            if (_selectedContactId.value in idSet) {
                _selectedContactId.value = null
                _selectedSessionId.value = null
            }
        }
    }

    // 2. 会话管理
    fun createSessionTopic(topicName: String) {
        viewModelScope.launch {
            val contactId = _selectedContactId.value ?: return@launch
            val session = repository.createSession(contactId, topicName)
            _selectedSessionId.value = session.sessionId
        }
    }

    fun updateSessionTopic(topicName: String) {
        viewModelScope.launch {
            val sessionId = _selectedSessionId.value ?: return@launch
            val session = repository.getSession(sessionId) ?: return@launch
            val updated = session.copy(topicName = topicName, updatedAt = System.currentTimeMillis())
            repository.updateSession(updated)
        }
    }

    fun updateSessionPreferences(relationId: String?, styleId: String?) {
        viewModelScope.launch {
            val sessionId = _selectedSessionId.value ?: return@launch
            val session = repository.getSession(sessionId) ?: return@launch
            val updated = session.copy(
                preferredRelationId = relationId,
                preferredStyleId = styleId,
                updatedAt = System.currentTimeMillis()
            )
            repository.updateSession(updated)
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_selectedSessionId.value == sessionId) {
                _selectedSessionId.value = null
            }
        }
    }

    // 3. 消息管理
    fun sendMessage(content: String, senderType: String = "USER", senderName: String = "我") {
        viewModelScope.launch {
            val sessionId = _selectedSessionId.value ?: return@launch
            repository.addMessage(sessionId, senderType, senderName, content)
        }
    }

    fun clearSessionMessages() {
        viewModelScope.launch {
            val sessionId = _selectedSessionId.value ?: return@launch
            repository.clearMessages(sessionId)
        }
    }

    fun importScreenshot(imageUri: Uri, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val sessionId = _selectedSessionId.value ?: return@launch
            try {
                val ocrResult = screenshotOcrProcessor.recognizeText(imageUri)
                val contactName = currentContact.value?.displayName?.takeIf { it.isNotBlank() } ?: "聊天好友"

                if (ocrResult.messages.isNotEmpty()) {
                    val imported = ocrResult.messages
                        .filter { !shouldIgnoreImportedScreenshotContent(it.content) }
                        .onEach { msg ->
                            val senderType = when (msg.senderType) {
                                com.socialfirstaidkit.core.data.model.SenderType.USER -> "USER"
                                com.socialfirstaidkit.core.data.model.SenderType.CONTACT -> "CONTACT"
                                com.socialfirstaidkit.core.data.model.SenderType.UNKNOWN -> "CONTACT"
                            }
                            repository.addMessage(
                                sessionId = sessionId,
                                senderType = senderType,
                                senderName = if (senderType == "USER") "我" else contactName,
                                content = msg.content
                            )
                        }
                    if (imported.isNotEmpty()) {
                        onSuccess()
                    } else {
                        onError("未从截图中识别到有效聊天消息")
                    }
                } else {
                    val analysisResult = messageAssistClient.analyzeScreenshot(ocrResult.text)
                    if (analysisResult.success && analysisResult.messages.isNotEmpty()) {
                        val imported = analysisResult.messages
                            .filter { !shouldIgnoreImportedScreenshotContent(it.content) }
                            .onEach { msg ->
                                val senderType = inferReliableSenderTypeFromOcr(msg.content, ocrResult)
                                    ?: when (msg.senderType) {
                                        com.socialfirstaidkit.core.llm.api.SenderType.USER -> "USER"
                                        com.socialfirstaidkit.core.llm.api.SenderType.CONTACT -> "CONTACT"
                                    }
                                repository.addMessage(
                                    sessionId = sessionId,
                                    senderType = senderType,
                                    senderName = if (senderType == "USER") "我" else contactName,
                                    content = msg.content
                                )
                            }
                        if (imported.isNotEmpty()) {
                            onSuccess()
                        } else {
                            onError("未从截图中识别到有效聊天消息")
                        }
                        return@launch
                    }

                    val fallbackMessages = fallbackMessagesFromOcrText(ocrResult.text)
                    if (fallbackMessages.isNotEmpty()) {
                        fallbackMessages.forEach { content ->
                            repository.addMessage(
                                sessionId = sessionId,
                                senderType = "CONTACT",
                                senderName = contactName,
                                content = content
                            )
                        }
                        onSuccess()
                    } else {
                        onError(analysisResult.errorMessage ?: "Screenshot analysis failed")
                    }
                }
            } catch (e: Exception) {
                onError(e.message ?: "Failed to process screenshot")
            }
        }
    }

    private fun fallbackMessagesFromOcrText(text: String): List<String> {
        return text.lineSequence()
            .map { it.trim() }
            .filter { line ->
                !shouldIgnoreImportedScreenshotContent(line) &&
                    !line.startsWith("\u804a\u5929\u5bf9\u8c61") &&
                    !line.equals("USER", ignoreCase = true) &&
                    !line.equals("CONTACT", ignoreCase = true) &&
                    !line.equals("UNKNOWN", ignoreCase = true)
            }
            .map { line ->
                line.removePrefix("\u6211:").removePrefix("\u6211\uff1a")
                    .removePrefix("\u5bf9\u65b9:").removePrefix("\u5bf9\u65b9\uff1a")
                    .removePrefix("USER:").removePrefix("USER\uff1a")
                    .removePrefix("CONTACT:").removePrefix("CONTACT\uff1a")
                    .trim()
            }
            .filter { it.isNotBlank() }
            .take(80)
            .toList()
    }

    private fun inferReliableSenderTypeFromOcr(content: String, ocrResult: OcrResult): String? {
        val imageWidth = ocrResult.imageWidth.takeIf { it > 0 } ?: return null
        val normalizedContent = content.trim().replace(Regex("\\s+"), "")
        if (normalizedContent.isBlank()) return null

        val match = ocrResult.messages.firstOrNull { message ->
            val candidate = message.content.trim().replace(Regex("\\s+"), "")
            candidate == normalizedContent &&
                message.detectionMethod in setOf(
                    com.socialfirstaidkit.core.data.model.DetectionMethod.POSITION,
                    com.socialfirstaidkit.core.data.model.DetectionMethod.BUBBLE,
                    com.socialfirstaidkit.core.data.model.DetectionMethod.AVATAR
                )
        } ?: return null

        val box = match.boundingBox
        val centerX = (box.left + box.right) / 2f
        return when {
            centerX >= imageWidth * 0.58f -> "USER"
            centerX <= imageWidth * 0.42f -> "CONTACT"
            else -> null
        }
    }

    private fun shouldIgnoreImportedScreenshotContent(content: String): Boolean {
        val normalized = content.trim().replace(Regex("\\s+"), "")
        if (normalized.isBlank()) return true
        if (normalized.matches(Regex("^(昨天|今天|上午|下午|晚上)?\\d{1,2}:\\d{2}$"))) return true
        if (normalized.matches(Regex("^(Contact|Me|USER|CONTACT|UNKNOWN)[·.。:：]?\\d{1,2}:\\d{2}$", RegexOption.IGNORE_CASE))) return true
        if (normalized.contains(Regex("(4G|5G|Wi[-]?Fi|KB/s|MB/s|电量|电池)", RegexOption.IGNORE_CASE))) return true
        if (normalized.contains("微信聊天") || normalized.contains("条消息")) return true
        if (normalized.contains("默认会话话题")) return true
        if (normalized in setOf("我", "你", "对方", "图片", "[图片]", "表情", "[表情]")) return true
        if (looksLikeStatusBarNoise(normalized)) return true
        return false
    }

    private fun looksLikeStatusBarNoise(text: String): Boolean {
        val chineseCount = text.count { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }
        val letterOrSymbolCount = text.count { it.isLetterOrDigit() || !it.isWhitespace() } - chineseCount
        if (text.length <= 14 && chineseCount <= 1 && letterOrSymbolCount >= 3) return true
        if (text.length <= 18 && chineseCount <= 2 && text.count { it in "%/.,。:+-_" } >= 2) return true
        return false
    }

    // 4. AI 回复生成与 UI 状态
    private val _uiState = MutableStateFlow<MessageAssistUiState>(MessageAssistUiState.Idle)
    val uiState: StateFlow<MessageAssistUiState> = _uiState.asStateFlow()

    private var lastSelectedMessageIdsForGeneration: List<String>? = null
    private var generateJob: kotlinx.coroutines.Job? = null
    private var isReplyGenerationInFlight = false

    fun generateReply(message: String? = null) {
        when (_replyMode.value) {
            ReplyMode.SINGLE_TEXT -> {
                val source = message?.takeIf { it.isNotBlank() } ?: _singleTextMessage.value
                if (source.isBlank()) {
                    _uiState.value = MessageAssistUiState.Error("请输入消息内容")
                    return
                }
                generateSingleReply(source)
            }
            ReplyMode.CONTEXTUAL -> generateContextualReply(_additionalNotes.value)
        }
    }

    fun generateSingleReply(message: String) {
        if (isReplyGenerationInFlight) return
        isReplyGenerationInFlight = true
        _uiState.value = MessageAssistUiState.Generating
        generateJob?.cancel()
        generateJob = viewModelScope.launch {
            try {
                val relationPrompt = buildRelationPromptContext(_selectedRelationId.value, _customRelationOptions.value)
                val styleConfig = buildStyleConfig(_selectedStyleId.value, _customStyleOptions.value)
                val contextAnalysis = messageAssistClient.analyzeSingleMessageContext(
                    message = message,
                    relation = relationPrompt,
                    style = styleConfig.replyStyle,
                    customStyleDescription = styleConfig.customStyleDescription,
                    additionalNotes = _additionalNotes.value
                ).toConversationContextAnalysis()
                _uiState.value = MessageAssistUiState.ConversationAnalyzed(contextAnalysis)
                val shouldAppendVersion = _singleReplyVersions.value.isNotEmpty() && lastSingleReplyMessage == message
                val suggestions = messageAssistClient.generateSingleReply(
                    message = message,
                    relation = relationPrompt,
                    style = styleConfig.replyStyle,
                    customStyleDescription = styleConfig.customStyleDescription,
                    additionalNotes = _additionalNotes.value,
                    personalExpression = currentPersonalExpressionContext(relationPrompt)
                )
                statsManager.incrementMessagesAnalyzed()
                statsManager.incrementRepliesGenerated()
                _hasGeneratedReply.value = true
                singleReplyHistoryRepository.saveHistory(message, suggestions)
                loadSingleReplyHistory()
                val updatedVersions = if (shouldAppendVersion) _singleReplyVersions.value + suggestions else listOf(suggestions)
                _singleReplyVersions.value = updatedVersions
                _singleReplyVersionIndex.value = updatedVersions.lastIndex
                lastSingleReplyMessage = message
                _uiState.value = MessageAssistUiState.SuggestionsReady(
                    suggestions = suggestions,
                    mode = ReplyMode.SINGLE_TEXT,
                    sourceMessage = message,
                    replyStyle = styleConfig.replyStyle,
                    contextAnalysis = contextAnalysis
                )
            } catch (e: CancellationException) {
                _uiState.value = MessageAssistUiState.Idle
            } catch (e: Exception) {
                _uiState.value = MessageAssistUiState.Error(e.message ?: "生成回复建议失败")
            } finally {
                isReplyGenerationInFlight = false
            }
        }
    }

    fun reset() {
        _uiState.value = MessageAssistUiState.Idle
    }

    fun resetGeneratedFlag() {
        _hasGeneratedReply.value = false
    }

    fun showPreviousSingleReplyVersion() {
        _singleReplyVersionIndex.value = (_singleReplyVersionIndex.value - 1).coerceAtLeast(0)
    }

    fun showNextSingleReplyVersion() {
        _singleReplyVersionIndex.value = (_singleReplyVersionIndex.value + 1).coerceAtMost((_singleReplyVersions.value.size - 1).coerceAtLeast(0))
    }

    fun deleteSingleReplyHistory(history: SingleReplyHistory) {
        viewModelScope.launch {
            singleReplyHistoryRepository.deleteHistory(history.id)
            loadSingleReplyHistory()
        }
    }

    fun deleteSingleReplyHistories(histories: List<SingleReplyHistory>) {
        viewModelScope.launch {
            histories.forEach { singleReplyHistoryRepository.deleteHistory(it.id) }
            loadSingleReplyHistory()
        }
    }

    fun clearSingleReplyHistory() {
        viewModelScope.launch {
            singleReplyHistoryRepository.clearHistory()
            loadSingleReplyHistory()
        }
    }

    private fun loadSingleReplyHistory() {
        viewModelScope.launch {
            _singleReplyHistory.value = singleReplyHistoryRepository.getHistory()
        }
    }

    private fun clearSingleReplyVersions() {
        _singleReplyVersions.value = emptyList()
        _singleReplyVersionIndex.value = 0
        lastSingleReplyMessage = ""
    }

    fun clearOcrHistory() {
        _ocrHistory.value = emptyList()
        viewModelScope.launch { ocrHistoryRepository.clearHistory() }
    }

    private fun loadOcrHistory() {
        viewModelScope.launch { _ocrHistory.value = ocrHistoryRepository.getHistory(OCR_HISTORY_MAX_SIZE) }
    }

    fun checkDuplicate(ocrResult: OcrResult): DuplicateCheckResult {
        val currentText = ocrResult.text
        if (currentText.isBlank() || _ocrHistory.value.isEmpty()) return DuplicateCheckResult.NoDuplicate(0f)
        var maxSimilarity = 0f
        var mostSimilarHistory: OcrHistory? = null
        _ocrHistory.value.forEach { history ->
            val similarity = calculateTextSimilarity(currentText, history.ocrResult.text)
            if (similarity > maxSimilarity) {
                maxSimilarity = similarity
                mostSimilarHistory = history
            }
        }
        return if (maxSimilarity >= OCR_SIMILARITY_THRESHOLD && mostSimilarHistory != null) {
            DuplicateCheckResult.Duplicate(mostSimilarHistory!!.id, maxSimilarity, mostSimilarHistory!!)
        } else {
            DuplicateCheckResult.NoDuplicate(maxSimilarity)
        }
    }

    fun addToOcrHistory(ocrResult: OcrResult, contactName: String) {
        val history = OcrHistory(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            ocrResult = ocrResult,
            contactName = contactName,
            messageCount = ocrResult.messages.size
        )
        _ocrHistory.value = (listOf(history) + _ocrHistory.value).take(OCR_HISTORY_MAX_SIZE)
        viewModelScope.launch {
            ocrHistoryRepository.saveHistory(history, OCR_HISTORY_MAX_SIZE)
            loadOcrHistory()
        }
    }

    private fun calculateTextSimilarity(text1: String, text2: String): Float {
        val words1 = text1.split(Regex("\\s+")).filter { it.length > 1 }.toSet()
        val words2 = text2.split(Regex("\\s+")).filter { it.length > 1 }.toSet()
        if (words1.isEmpty() && words2.isEmpty()) return 1f
        if (words1.isEmpty() || words2.isEmpty()) return 0f
        return words1.intersect(words2).size.toFloat() / words1.union(words2).size.toFloat()
    }

    private fun SingleMessageContextAnalysis.toConversationContextAnalysis(): ConversationContextAnalysis {
        return ConversationContextAnalysis(scene, tone, emotion, intent, strategy)
    }
    fun generateContextualReply(additionalNotes: String) {
        if (isReplyGenerationInFlight) return
        isReplyGenerationInFlight = true
        _uiState.value = MessageAssistUiState.Generating

        generateJob?.cancel()
        generateJob = viewModelScope.launch {
            try {
                lastSelectedMessageIdsForGeneration = null
                val sessionEntity = currentSession.value
                val contactEntity = currentContact.value
                val messageEntities = messagesForSelectedSession.value

                if (sessionEntity == null) {
                    _uiState.value = MessageAssistUiState.Error("请先选择或创建一个会话")
                    return@launch
                }

                val contactName = contactEntity?.displayName ?: "聊天好友"
                val chatSession = ChatSession(
                    sessionId = sessionEntity.sessionId,
                    contactName = contactName,
                    contactAvatarUri = contactEntity?.avatarUri,
                    preferredRelationId = sessionEntity.preferredRelationId ?: contactEntity?.defaultRelationId ?: com.socialfirstaidkit.feature.chatassist.model.DEFAULT_RELATION_ID,
                    preferredStyleId = sessionEntity.preferredStyleId ?: contactEntity?.defaultStyleId ?: com.socialfirstaidkit.feature.chatassist.model.DEFAULT_STYLE_ID,
                    messages = messageEntities.map { msg ->
                        ChatMessage(
                            id = msg.messageId,
                            senderType = if (msg.senderType == "USER") SenderType.USER else SenderType.CONTACT,
                            senderName = msg.senderName,
                            content = msg.content,
                            timestamp = msg.timestamp
                        )
                    },
                    createdAt = sessionEntity.createdAt,
                    updatedAt = sessionEntity.updatedAt
                )

                val relOptions = customRelationOptions.value
                val styOptions = customStyleOptions.value

                val relationPrompt = com.socialfirstaidkit.feature.chatassist.model.buildRelationPromptContext(chatSession.preferredRelationId, relOptions)
                val styleConfig = com.socialfirstaidkit.feature.chatassist.model.buildStyleConfig(chatSession.preferredStyleId, styOptions)

                val contextAnalysis = analyzeConversationContext(chatSession)
                _uiState.value = MessageAssistUiState.ConversationAnalyzed(contextAnalysis)

                val suggestions = messageAssistClient.generateContextualReply(
                    session = chatSession,
                    relation = relationPrompt,
                    style = styleConfig.replyStyle,
                    customStyleDescription = styleConfig.customStyleDescription,
                    additionalNotes = additionalNotes,
                    personalExpression = currentPersonalExpressionContext(relationPrompt)
                )

                // 统计
                statsManager.incrementMessagesAnalyzed()
                statsManager.incrementRepliesGenerated()

                val lastMessage = chatSession.messages.lastOrNull()
                _uiState.value = MessageAssistUiState.SuggestionsReady(
                    suggestions = suggestions,
                    mode = ReplyMode.CONTEXTUAL,
                    sourceMessage = lastMessage?.content ?: "",
                    replyStyle = styleConfig.replyStyle,
                    contextAnalysis = contextAnalysis
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                _uiState.value = MessageAssistUiState.Idle
            } catch (e: Exception) {
                _uiState.value = MessageAssistUiState.Error(
                    e.message ?: "生成回复建议失败"
                )
            } finally {
                isReplyGenerationInFlight = false
            }
        }
    }

    fun generateReplyFromSelectedMessages(messageIds: List<String>, additionalNotes: String) {
        if (isReplyGenerationInFlight) return
        isReplyGenerationInFlight = true
        _uiState.value = MessageAssistUiState.Generating

        generateJob?.cancel()
        generateJob = viewModelScope.launch {
            try {
                lastSelectedMessageIdsForGeneration = messageIds
                val sessionEntity = currentSession.value
                val contactEntity = currentContact.value
                val allMessageEntities = messagesForSelectedSession.value

                if (sessionEntity == null) {
                    _uiState.value = MessageAssistUiState.Error("请先选择或创建一个会话")
                    return@launch
                }

                val selectedMessageEntities = allMessageEntities
                    .filter { messageIds.contains(it.messageId) }
                    .sortedBy { it.timestamp }

                if (selectedMessageEntities.isEmpty()) {
                    _uiState.value = MessageAssistUiState.Error("没有选择任何消息")
                    return@launch
                }

                val contactName = contactEntity?.displayName ?: "聊天好友"
                val chatSession = ChatSession(
                    sessionId = sessionEntity.sessionId,
                    contactName = contactName,
                    contactAvatarUri = contactEntity?.avatarUri,
                    preferredRelationId = sessionEntity.preferredRelationId ?: contactEntity?.defaultRelationId ?: com.socialfirstaidkit.feature.chatassist.model.DEFAULT_RELATION_ID,
                    preferredStyleId = sessionEntity.preferredStyleId ?: contactEntity?.defaultStyleId ?: com.socialfirstaidkit.feature.chatassist.model.DEFAULT_STYLE_ID,
                    messages = selectedMessageEntities.map { msg ->
                        ChatMessage(
                            id = msg.messageId,
                            senderType = if (msg.senderType == "USER") SenderType.USER else SenderType.CONTACT,
                            senderName = msg.senderName,
                            content = msg.content,
                            timestamp = msg.timestamp
                        )
                    },
                    createdAt = sessionEntity.createdAt,
                    updatedAt = sessionEntity.updatedAt
                )

                val relOptions = customRelationOptions.value
                val styOptions = customStyleOptions.value

                val relationPrompt = com.socialfirstaidkit.feature.chatassist.model.buildRelationPromptContext(chatSession.preferredRelationId, relOptions)
                val styleConfig = com.socialfirstaidkit.feature.chatassist.model.buildStyleConfig(chatSession.preferredStyleId, styOptions)

                val contextAnalysis = analyzeConversationContext(chatSession)
                _uiState.value = MessageAssistUiState.ConversationAnalyzed(contextAnalysis)

                val suggestions = messageAssistClient.generateContextualReply(
                    session = chatSession,
                    relation = relationPrompt,
                    style = styleConfig.replyStyle,
                    customStyleDescription = styleConfig.customStyleDescription,
                    additionalNotes = additionalNotes,
                    personalExpression = currentPersonalExpressionContext(relationPrompt)
                )

                // 统计
                statsManager.incrementMessagesAnalyzed()
                statsManager.incrementRepliesGenerated()

                val lastMessage = chatSession.messages.lastOrNull()
                _uiState.value = MessageAssistUiState.SuggestionsReady(
                    suggestions = suggestions,
                    mode = ReplyMode.CONTEXTUAL,
                    sourceMessage = lastMessage?.content ?: "",
                    replyStyle = styleConfig.replyStyle,
                    contextAnalysis = contextAnalysis
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                _uiState.value = MessageAssistUiState.Idle
            } catch (e: Exception) {
                _uiState.value = MessageAssistUiState.Error(
                    e.message ?: "生成回复建议失败"
                )
            } finally {
                isReplyGenerationInFlight = false
            }
        }
    }

    fun regenerateLastContextualReply(additionalNotes: String) {
        val selectedIds = lastSelectedMessageIdsForGeneration
        if (selectedIds != null) {
            generateReplyFromSelectedMessages(selectedIds, additionalNotes)
        } else {
            generateContextualReply(additionalNotes)
        }
    }

    private fun analyzeConversationContext(session: ChatSession): ConversationContextAnalysis {
        val recentMessages = session.messages.takeLast(12)
        val combinedText = recentMessages.joinToString(" ") { it.content }
        val lastContactMessage = recentMessages.lastOrNull { it.senderType == SenderType.CONTACT }?.content.orEmpty()
        val questionCount = recentMessages.count { it.content.contains("?") || it.content.contains("？") }

        val scene = when {
            combinedText.contains("谢谢") || combinedText.contains("麻烦") || combinedText.contains("帮") -> "请求协助或人情沟通"
            combinedText.contains("抱歉") || combinedText.contains("不好意思") || combinedText.contains("对不起") -> "道歉、解释或关系修复"
            combinedText.contains("几点") || combinedText.contains("什么时候") || combinedText.contains("安排") || combinedText.contains("时间") -> "时间安排与事项推进"
            questionCount >= 2 -> "信息确认或持续追问"
            else -> "日常聊天或一般社交回应"
        }

        val tone = when {
            combinedText.contains("哈哈") || combinedText.contains("笑死") || combinedText.contains("喜") -> "轻松幽默"
            combinedText.contains("嗯") || combinedText.contains("哦") || combinedText.contains("好的") -> "平淡克制"
            combinedText.contains("哇") || combinedText.contains("太棒了") || combinedText.contains("厉害") -> "热情积极"
            combinedText.contains("烦") || combinedText.contains("唉") || combinedText.contains("累") -> "焦虑疲惫"
            else -> "温和理智"
        }

        val emotion = when {
            combinedText.contains("生气") || combinedText.contains("怒") -> "有些愤怒"
            combinedText.contains("难过") || combinedText.contains("伤心") || combinedText.contains("哭") -> "情绪低落"
            combinedText.contains("开心") || combinedText.contains("快乐") || combinedText.contains("高兴") -> "心情愉悦"
            combinedText.contains("怕") || combinedText.contains("担心") || combinedText.contains("紧张") -> "有些不安"
            else -> "平静稳定"
        }

        val intent = when {
            lastContactMessage.contains("吗") || lastContactMessage.contains("？") || lastContactMessage.contains("?") -> "向我询问或核实情况"
            lastContactMessage.contains("吧") || lastContactMessage.contains("觉得") -> "征求我的意见或看法"
            lastContactMessage.contains("快点") || lastContactMessage.contains("赶紧") -> "督促或要求我采取行动"
            lastContactMessage.isBlank() -> "暂无消息"
            else -> "分享日常或表达个人观点"
        }

        val strategy = when (scene) {
            "请求协助或人情沟通" -> "积极回应，承诺落实，表现出乐意帮忙的态度，并简要说明计划。"
            "道歉、解释或关系修复" -> "真诚接纳，表达理解与宽容，或诚恳说明情况，淡化矛盾，重构信任。"
            "时间安排与事项推进" -> "明确时间节点，清晰列出接下来的步骤，不拖泥带水，确保信息闭环。"
            "信息确认或持续追问" -> "针对疑问逐条清晰解答，避免含糊其辞，必要时反问以确保双方理解一致。"
            else -> "顺应对方话题，给予共情反馈，或抛出相关开放式问题，保持良性互动节奏。"
        }

        return ConversationContextAnalysis(
            scene = scene,
            tone = tone,
            emotion = emotion,
            intent = intent,
            strategy = strategy
        )
    }

    fun updateContextAnalysis(newAnalysis: ConversationContextAnalysis) {
        val currentState = _uiState.value
        when (currentState) {
            is MessageAssistUiState.ConversationAnalyzed -> {
                _uiState.value = MessageAssistUiState.ConversationAnalyzed(newAnalysis)
            }
            is MessageAssistUiState.SuggestionsReady -> {
                _uiState.value = currentState.copy(contextAnalysis = newAnalysis)
            }
            else -> {}
        }
    }

    fun cancelGeneration() {
        generateJob?.cancel()
        generateJob = null
        isReplyGenerationInFlight = false
        _uiState.value = MessageAssistUiState.Idle
    }

    fun recordCopiedSuggestion(copiedText: String) {
        if (copiedText.isBlank() || !expressionUsageRepository.isMessageAssistEnabled()) return
        val readyState = _uiState.value as? MessageAssistUiState.SuggestionsReady ?: return

        viewModelScope.launch {
            val profile = expressionRepository.getProfile()
            if (profile?.enabled != true || !profile.questionnaireCompleted) return@launch
            
            val sessionEntity = currentSession.value
            val contactEntity = currentContact.value
            val preferredRelationId = sessionEntity?.preferredRelationId ?: contactEntity?.defaultRelationId ?: com.socialfirstaidkit.feature.chatassist.model.DEFAULT_RELATION_ID
            val relOptions = customRelationOptions.value
            val relationPrompt = com.socialfirstaidkit.feature.chatassist.model.buildRelationPromptContext(preferredRelationId, relOptions)

            expressionRepository.recordFeedbackSample(
                PersonalExpressionFeedbackInput(
                    source = "message_assist_contextual",
                    relation = relationPrompt,
                    style = currentStyleLabel(readyState.replyStyle),
                    scene = readyState.contextAnalysis?.scene ?: "contextual",
                    suggestionSlot = resolveSuggestionSlot(copiedText, readyState.suggestions),
                    copiedText = copiedText,
                    sourceMessage = readyState.sourceMessage
                )
            )
        }
    }

    private suspend fun currentPersonalExpressionContext(relationPrompt: String): com.socialfirstaidkit.core.data.personalexpression.PersonalExpressionPromptContext? {
        if (!expressionUsageRepository.isMessageAssistEnabled()) return null
        return expressionRepository.getPromptContext(relationPrompt)
    }

    private fun currentStyleLabel(style: com.socialfirstaidkit.core.llm.api.ReplyStyle?): String {
        return when (style) {
            com.socialfirstaidkit.core.llm.api.ReplyStyle.FORMAL -> "formal"
            com.socialfirstaidkit.core.llm.api.ReplyStyle.CASUAL -> "casual"
            com.socialfirstaidkit.core.llm.api.ReplyStyle.HUMOROUS -> "humorous"
            com.socialfirstaidkit.core.llm.api.ReplyStyle.EMPATHETIC -> "empathetic"
            com.socialfirstaidkit.core.llm.api.ReplyStyle.ASSERTIVE -> "assertive"
            com.socialfirstaidkit.core.llm.api.ReplyStyle.DAI_ZHOU_LI -> "daizhou_li"
            com.socialfirstaidkit.core.llm.api.ReplyStyle.OTHER -> "custom"
            null -> "default"
        }
    }

    private fun resolveSuggestionSlot(copiedText: String, suggestions: com.socialfirstaidkit.core.llm.model.ReplySuggestions): String {
        return when (copiedText) {
            suggestions.formal -> "formal"
            suggestions.casual -> "casual"
            suggestions.humorous -> "humorous"
            else -> "copy"
        }
    }

    // 5. 消息多选、删除与转发
    fun deleteMessages(messageIds: List<String>) {
        viewModelScope.launch {
            messageIds.forEach { id ->
                repository.deleteMessage(id)
            }
        }
    }

    fun updateMessage(messageId: String, content: String, senderType: String, senderName: String) {
        viewModelScope.launch {
            repository.updateMessage(messageId, content, senderType, senderName)
        }
    }

    fun insertMessageAfter(targetMessageId: String, content: String, senderType: String, senderName: String) {
        viewModelScope.launch {
            repository.addMessageAfter(targetMessageId, senderType, senderName, content)
        }
    }

    fun startVoiceInput() {
        if (voiceInputState.value is VoiceInputState.Listening) return

        voiceInputTextBuffer = ""
        voiceInputPartialText = ""
        voiceInputFinishing = false
        voiceLastFinalNormalizedText = ""
        _voiceInputState.value = VoiceInputState.Listening()
        voiceInputJob?.cancel()
        voiceRecordJob?.cancel()
        voiceFinalizeJob?.cancel()

        voiceInputJob = viewModelScope.launch {
            voiceInputEngine.startListening().collect { result ->
                when (result) {
                    is ShortVoiceInputResult.Partial -> {
                        updateVoiceInputPartialText(result.text)
                    }
                    is ShortVoiceInputResult.Final -> {
                        updateVoiceInputFinalText(result.text, result.isLast)
                        if (voiceInputFinishing) {
                            publishVoiceInputResult()
                        }
                    }
                    is ShortVoiceInputResult.Error -> {
                        _voiceInputState.value = VoiceInputState.Error(result.message)
                        closeVoiceInput(keepState = true)
                    }
                }
            }
        }

        voiceRecordJob = viewModelScope.launch {
            try {
                var speechStarted = false
                var lastSpeechAt = 0L
                var noiseFloor = INITIAL_NOISE_FLOOR
                var lastVadLogAt = 0L
                var consecutiveSpeechFrames = 0
                val startedAt = System.currentTimeMillis()
                voiceAudioRecorder.startRecording().collect { audioData ->
                    voiceInputEngine.sendAudioData(audioData)
                    val now = System.currentTimeMillis()
                    val elapsedMs = now - startedAt
                    val amplitude = averageAmplitude(audioData)
                    if (!speechStarted && elapsedMs <= NOISE_CALIBRATION_MS) {
                        noiseFloor = updateNoiseFloor(noiseFloor, amplitude)
                    }
                    val speechThreshold = maxOf(
                        MIN_SPEECH_AMPLITUDE_THRESHOLD,
                        (noiseFloor * SPEECH_THRESHOLD_MULTIPLIER).toLong()
                    )
                    val silenceThreshold = maxOf(
                        MIN_SILENCE_AMPLITUDE_THRESHOLD,
                        (noiseFloor * SILENCE_THRESHOLD_MULTIPLIER).toLong()
                    ).coerceAtMost(speechThreshold - 1)
                    val hasSpeech = amplitude >= speechThreshold
                    if (hasSpeech) {
                        consecutiveSpeechFrames += 1
                        if (!speechStarted && consecutiveSpeechFrames >= SPEECH_START_FRAMES_REQUIRED) {
                            speechStarted = true
                        }
                        if (speechStarted) {
                            lastSpeechAt = now
                        }
                    } else if (speechStarted && amplitude > silenceThreshold) {
                        lastSpeechAt = now
                    } else if (amplitude <= silenceThreshold) {
                        consecutiveSpeechFrames = 0
                    }
                    if (now - lastVadLogAt >= VAD_LOG_INTERVAL_MS) {
                        Log.d(
                            TAG,
                            "Voice VAD amplitude=$amplitude noise=$noiseFloor speechThreshold=$speechThreshold silenceThreshold=$silenceThreshold speechStarted=$speechStarted"
                        )
                        lastVadLogAt = now
                    }
                    val silenceAfterSpeechMs = if (speechStarted) now - lastSpeechAt else 0L
                    when {
                        speechStarted && silenceAfterSpeechMs >= AUTO_STOP_SILENCE_MS -> {
                            Log.d(TAG, "Auto stop voice input after silence=${silenceAfterSpeechMs}ms")
                            finishVoiceInput(waitForMoreResult = true)
                            return@collect
                        }
                        elapsedMs >= MAX_RECORDING_MS -> {
                            Log.d(TAG, "Auto stop voice input by max duration=${elapsedMs}ms")
                            finishVoiceInput(waitForMoreResult = true)
                            return@collect
                        }
                        !speechStarted && elapsedMs >= NO_SPEECH_TIMEOUT_MS -> {
                            Log.d(TAG, "Auto stop voice input because no speech was detected")
                            finishVoiceInput(waitForMoreResult = false)
                            return@collect
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _voiceInputState.value = VoiceInputState.Error(e.message ?: "无法启动麦克风录音")
                closeVoiceInput(keepState = true)
            }
        }
    }

    fun stopVoiceInput(keepState: Boolean = false) {
        finishVoiceInput(waitForMoreResult = !keepState)
    }

    private fun closeVoiceInput(keepState: Boolean = false) {
        voiceRecordJob?.cancel()
        voiceRecordJob = null
        voiceInputJob?.cancel()
        voiceInputJob = null
        voiceFinalizeJob?.cancel()
        voiceFinalizeJob = null
        voiceAudioRecorder.stopRecording()
        voiceInputEngine.stopListening()
        if (!keepState) {
            _voiceInputState.value = VoiceInputState.Idle
        }
    }

    fun clearVoiceInputState() {
        voiceFinalizeJob?.cancel()
        voiceFinalizeJob = null
        voiceInputTextBuffer = ""
        voiceInputPartialText = ""
        voiceInputFinishing = false
        voiceLastFinalNormalizedText = ""
        _voiceInputState.value = VoiceInputState.Idle
    }

    private fun finishVoiceInput(waitForMoreResult: Boolean) {
        if (voiceInputFinishing) return
        voiceInputFinishing = true
        voiceRecordJob?.cancel()
        voiceRecordJob = null
        voiceAudioRecorder.stopRecording()
        voiceInputEngine.finishAudioInput()
        val previewText = currentVoiceInputPreviewText()
        if (previewText.isNotBlank()) {
            if (waitForMoreResult) {
                _voiceInputState.value = VoiceInputState.Listening(previewText)
                voiceFinalizeJob?.cancel()
                voiceFinalizeJob = viewModelScope.launch {
                    delay(FINAL_RESULT_WAIT_MS)
                    publishVoiceInputResult()
                }
            } else {
                publishVoiceInputResult()
            }
        } else if (waitForMoreResult) {
            _voiceInputState.value = VoiceInputState.Listening()
            voiceFinalizeJob?.cancel()
            voiceFinalizeJob = viewModelScope.launch {
                delay(FINAL_RESULT_WAIT_MS)
                publishVoiceInputResult()
            }
        } else {
            closeVoiceInput()
        }
    }

    private fun publishVoiceInputResult() {
        voiceFinalizeJob?.cancel()
        voiceFinalizeJob = null
        val finalText = voiceInputTextBuffer.trim()
        val partialText = voiceInputPartialText.trim()
        val rawText = finalText.ifBlank { partialText }
        val text = cleanVoiceInputText(rawText)
        closeVoiceInput(keepState = true)
        if (finalText.isBlank() && partialText.isNotBlank()) {
            Log.d(TAG, "Use partial-only ASR text: $partialText")
        } else if (rawText != text) {
            Log.d(TAG, "Clean ASR text raw=$rawText cleaned=$text")
        }
        _voiceInputState.value = if (text.isBlank() || text in IGNORED_VOICE_RESULTS) {
            VoiceInputState.Idle
        } else {
            VoiceInputState.Recognized(text)
        }
    }

    private fun updateVoiceInputPartialText(text: String) {
        val cleanedText = text.trim()
        if (cleanedText.isBlank() || cleanedText in IGNORED_VOICE_RESULTS) return
        voiceInputPartialText = cleanedText
        if (!voiceInputFinishing) {
            _voiceInputState.value = VoiceInputState.Listening(currentVoiceInputPreviewText())
        }
    }

    private fun updateVoiceInputFinalText(text: String, isLast: Boolean) {
        val cleanedText = text.trim()
        if (cleanedText.isBlank() || cleanedText in IGNORED_VOICE_RESULTS) return
        val normalizedText = normalizeVoiceText(cleanedText)
        if (normalizedText.isBlank()) return
        if (normalizedText == voiceLastFinalNormalizedText) {
            Log.d(TAG, "Drop duplicated final ASR text: $cleanedText")
            return
        }
        voiceLastFinalNormalizedText = normalizedText
        voiceInputTextBuffer = chooseBetterVoiceInputText(voiceInputTextBuffer, cleanedText, isLast)
        voiceInputPartialText = ""
        if (!voiceInputFinishing) {
            _voiceInputState.value = VoiceInputState.Listening(currentVoiceInputPreviewText())
        }
    }

    private fun chooseBetterVoiceInputText(current: String, next: String, isLast: Boolean): String {
        val normalizedCurrent = normalizeVoiceText(current)
        val normalizedNext = normalizeVoiceText(next)
        if (normalizedNext.isBlank()) return current
        if (current.isBlank()) return next
        if (current in IGNORED_VOICE_RESULTS && next !in IGNORED_VOICE_RESULTS) return next
        if (next in IGNORED_VOICE_RESULTS) return current
        if (normalizedNext == normalizedCurrent || normalizedCurrent.endsWith(normalizedNext)) return current
        if (normalizedNext.length > normalizedCurrent.length && normalizedNext.contains(normalizedCurrent)) return next
        if (normalizedCurrent.contains(normalizedNext)) return current
        return if (isLast && normalizedNext.length >= normalizedCurrent.length) next else current
    }

    private fun currentVoiceInputPreviewText(): String =
        voiceInputTextBuffer.ifBlank { voiceInputPartialText }.trim()

    private fun normalizeVoiceText(text: String): String =
        text.filterNot { it.isWhitespace() || it in VOICE_TEXT_PUNCTUATION }

    private fun cleanVoiceInputText(text: String): String {
        val segments = splitVoiceTextSegments(text)
        if (segments.isEmpty()) return ""

        val seen = mutableSetOf<String>()
        val cleanedSegments = segments.filter { segment ->
            val normalized = normalizeVoiceText(segment)
            when {
                normalized.isBlank() -> false
                normalized in IGNORED_VOICE_RESULTS -> false
                normalized in VOICE_PROCESS_ARTIFACTS -> false
                segments.size > 1 && normalized.length <= 1 -> false
                !seen.add(normalized) -> false
                else -> true
            }
        }
        return cleanedSegments.joinToString("").trim()
    }

    private fun splitVoiceTextSegments(text: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        text.forEach { char ->
            current.append(char)
            if (char in VOICE_SENTENCE_PUNCTUATION) {
                current.toString().trim().takeIf { it.isNotBlank() }?.let(result::add)
                current.clear()
            }
        }
        current.toString().trim().takeIf { it.isNotBlank() }?.let(result::add)
        return result
    }

    private fun averageAmplitude(audioData: ByteArray): Long {
        if (audioData.size < 2) return 0L
        var sum = 0L
        var index = 0
        var samples = 0
        while (index + 1 < audioData.size) {
            val sample = ((audioData[index + 1].toInt() shl 8) or (audioData[index].toInt() and 0xFF)).toShort().toInt()
            sum += kotlin.math.abs(sample)
            samples++
            index += 2
        }
        if (samples == 0) return 0L
        return sum / samples
    }

    private fun updateNoiseFloor(currentNoiseFloor: Long, amplitude: Long): Long {
        val boundedAmplitude = amplitude.coerceIn(0L, MAX_NOISE_FLOOR_SAMPLE)
        return ((currentNoiseFloor * 0.85f) + (boundedAmplitude * 0.15f)).toLong()
    }

    fun forwardMessagesToSession(
        targetSessionId: String,
        messages: List<ChatMessageEntity>,
        merge: Boolean
    ) {
        if (messages.isEmpty()) return
        viewModelScope.launch {
            if (merge) {
                val mergedContent = buildString {
                    appendLine("[合并转发] 聊天记录")
                    messages.sortedBy { it.timestamp }.forEach { message ->
                        appendLine("${message.senderName}: ${message.content}")
                    }
                }.trim()
                repository.addMessage(
                    sessionId = targetSessionId,
                    senderType = "USER",
                    senderName = "我",
                    content = mergedContent
                )
            } else {
                messages.sortedBy { it.timestamp }.forEach { message ->
                    repository.addMessage(
                        sessionId = targetSessionId,
                        senderType = message.senderType,
                        senderName = message.senderName,
                        content = message.content
                    )
                }
            }
        }
    }


    private val _allSessionsForSearch = MutableStateFlow<List<ChatSessionSearchItem>>(emptyList())
    val allSessionsForSearch: StateFlow<List<ChatSessionSearchItem>> = _allSessionsForSearch.asStateFlow()

    fun loadAllSessionsForSearch() {
        viewModelScope.launch {
            _allSessionsForSearch.value = repository.getAllSessions().map { session ->
                val contact = repository.getContact(session.contactId)
                ChatSessionSearchItem(
                    session = session,
                    contactName = contact?.displayName ?: "聊天好友",
                    messages = repository.getMessagesSuspend(session.sessionId)
                )
            }.sortedByDescending { item -> item.messages.lastOrNull()?.timestamp ?: item.session.updatedAt }
        }
    }

    private val _allSessionsForForward = MutableStateFlow<List<SessionWithContactName>>(emptyList())
    val allSessionsForForward: StateFlow<List<SessionWithContactName>> = _allSessionsForForward.asStateFlow()

    fun loadAllSessionsForForward() {
        viewModelScope.launch {
            val sessions = repository.getAllSessions()
            val list = sessions.map { session ->
                val contact = repository.getContact(session.contactId)
                val messages = repository.getMessagesSuspend(session.sessionId)
                SessionWithContactName(
                    sessionId = session.sessionId,
                    contactName = contact?.displayName ?: "聊天好友",
                    topicName = session.topicName,
                    messageCount = messages.size
                )
            }
            _allSessionsForForward.value = list
        }
    }

    override fun onCleared() {
        closeVoiceInput()
        super.onCleared()
    }
}

private const val TAG = "NewChatVoiceInput"
private const val AUTO_STOP_SILENCE_MS = 2400L
private const val NO_SPEECH_TIMEOUT_MS = 8000L
private const val MAX_RECORDING_MS = 15000L
private const val FINAL_RESULT_WAIT_MS = 800L
private const val NOISE_CALIBRATION_MS = 500L
private const val VAD_LOG_INTERVAL_MS = 800L
private const val INITIAL_NOISE_FLOOR = 120L
private const val SPEECH_START_FRAMES_REQUIRED = 3
private const val MIN_SPEECH_AMPLITUDE_THRESHOLD = 650L
private const val MIN_SILENCE_AMPLITUDE_THRESHOLD = 420L
private const val MAX_NOISE_FLOOR_SAMPLE = 1200L
private const val SPEECH_THRESHOLD_MULTIPLIER = 2.6f
private const val SILENCE_THRESHOLD_MULTIPLIER = 1.8f
private val IGNORED_VOICE_RESULTS = setOf("啊", "嗯", "呃", "额", "嗯哼", "呃呃")
private val VOICE_PROCESS_ARTIFACTS = setOf("识别好了", "识别好啦", "识别完成", "语音识别好了", "语音识别完成")
private val VOICE_TEXT_PUNCTUATION = setOf('。', '，', ',', '.', '！', '!', '？', '?', '、', '；', ';', '：', ':')
private val VOICE_SENTENCE_PUNCTUATION = setOf('。', '！', '!', '？', '?', '；', ';')

sealed class VoiceInputState {
    data object Idle : VoiceInputState()
    data class Listening(val partialText: String = "") : VoiceInputState()
    data class Recognized(val text: String) : VoiceInputState()
    data class Error(val message: String) : VoiceInputState()
}

data class ChatSessionSearchItem(
    val session: ChatSessionEntity,
    val contactName: String,
    val messages: List<ChatMessageEntity>)

data class SessionWithContactName(
    val sessionId: String,
    val contactName: String,
    val topicName: String,
    val messageCount: Int
)









