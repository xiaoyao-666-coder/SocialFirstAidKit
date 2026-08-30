package com.socialfirstaidkit.feature.chatassist.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.socialfirstaidkit.core.data.local.entity.ChatMessageEntity
import com.socialfirstaidkit.feature.chatassist.model.ContactWithLastMessage
import com.socialfirstaidkit.feature.chatassist.ui.newarch.ChatSessionSearchItem
import com.socialfirstaidkit.feature.chatassist.ui.newarch.NewChatViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSearchScreen(
    viewModel: NewChatViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onOpenSession: (String) -> Unit = {},
    onOpenContact: (String) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    val searchSessions by viewModel.allSessionsForSearch.collectAsState()
    val contacts by viewModel.contactsWithLastMessage.collectAsState()
    val query = searchQuery.trim()
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    LaunchedEffect(Unit) {
        viewModel.loadAllSessionsForSearch()
    }

    val results = remember(query, searchSessions, contacts) {
        buildSearchResults(query, searchSessions, contacts)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        placeholder = { Text("搜索联系人、话题和聊天记录") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                query.isBlank() -> {
                    EmptySearchState(
                        title = "输入关键词搜索",
                        subtitle = "支持英文和汉字，可搜索联系人、话题、发送者和消息内容。"
                    )
                }
                results.isEmpty() -> {
                    EmptySearchState(
                        title = "没有找到结果",
                        subtitle = "换个关键词再试试。"
                    )
                }
                else -> {
                    Text(
                        text = "搜索结果 ${results.size}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(
                            items = results,
                            key = { result ->
                                listOf(
                                    result.contactId,
                                    result.sessionId.orEmpty(),
                                    result.message?.messageId.orEmpty(),
                                    result.matchType.name
                                ).joinToString("_")
                            }
                        ) { result ->
                            SearchResultRow(
                                result = result,
                                query = query,
                                dateText = dateFormat.format(Date(result.timestamp)),
                                onClick = {
                                    result.sessionId?.let(onOpenSession) ?: onOpenContact(result.contactId)
                                }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 64.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySearchState(
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(title, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun SearchResultRow(
    result: ChatSearchResult,
    query: String,
    dateText: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .padding(end = 12.dp)
                .clip(RoundedCornerShape(10.dp)),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        ) {
            Icon(
                if (result.sessionId == null) Icons.Default.Person else Icons.Default.ChatBubbleOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(10.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HighlightedText(
                    text = result.title,
                    query = query,
                    fontSize = 15,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = dateText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
            if (!result.subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                HighlightedText(
                    text = result.subtitle,
                    query = query,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            HighlightedText(
                text = result.preview,
                query = query,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13
            )
        }
    }
}

@Composable
private fun HighlightedText(
    text: String,
    query: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontSize: Int = 14,
    fontWeight: FontWeight? = null
) {
    val highlightColor = MaterialTheme.colorScheme.primary
    val keyword = query.trim()
    val annotated = if (keyword.isBlank() || keyword.contains(' ')) {
        buildAnnotatedString { append(text) }
    } else {
        buildAnnotatedString {
            var currentIndex = 0
            while (currentIndex < text.length) {
                val matchIndex = text.indexOf(keyword, currentIndex, ignoreCase = true)
                if (matchIndex < 0) {
                    append(text.substring(currentIndex))
                    break
                }
                append(text.substring(currentIndex, matchIndex))
                withStyle(
                    SpanStyle(
                        color = highlightColor,
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append(text.substring(matchIndex, matchIndex + keyword.length))
                }
                currentIndex = matchIndex + keyword.length
            }
        }
    }
    Text(
        text = annotated,
        modifier = modifier,
        color = color,
        fontSize = fontSize.sp,
        fontWeight = fontWeight,
        maxLines = 2,
        lineHeight = (fontSize + 5).sp
    )
}

private enum class SearchMatchType {
    CONTACT,
    TOPIC,
    MESSAGE
}

private data class ChatSearchResult(
    val contactId: String,
    val sessionId: String?,
    val title: String,
    val subtitle: String?,
    val message: ChatMessageEntity?,
    val preview: String,
    val timestamp: Long,
    val matchType: SearchMatchType
)

private fun buildSearchResults(
    query: String,
    sessions: List<ChatSessionSearchItem>,
    contacts: List<ContactWithLastMessage>
): List<ChatSearchResult> {
    if (query.isBlank()) return emptyList()

    val results = mutableListOf<ChatSearchResult>()

    contacts.filter { contact ->
        contact.contact.displayName.matchesSearchQuery(query) ||
            contact.lastMessageText.matchesSearchQuery(query)
    }.forEach { contact ->
        results += ChatSearchResult(
            contactId = contact.contact.id,
            sessionId = null,
            title = contact.contact.displayName,
            subtitle = "联系人",
            message = null,
            preview = contact.lastMessageText.ifBlank { "联系人名称匹配" },
            timestamp = contact.lastMessageTime,
            matchType = SearchMatchType.CONTACT
        )
    }

    sessions.forEach { session ->
        val topicMatched = session.session.topicName.matchesSearchQuery(query)
        val messageMatches = session.messages.filter { message ->
            message.content.matchesSearchQuery(query) || message.senderName.matchesSearchQuery(query)
        }

        if (messageMatches.isNotEmpty()) {
            messageMatches.forEach { message ->
                val senderPrefix = message.senderName.takeIf { it.isNotBlank() }?.let { "$it: " }.orEmpty()
                results += ChatSearchResult(
                    contactId = session.session.contactId,
                    sessionId = session.session.sessionId,
                    title = session.contactName,
                    subtitle = session.session.topicName,
                    message = message,
                    preview = senderPrefix + message.content,
                    timestamp = message.timestamp,
                    matchType = SearchMatchType.MESSAGE
                )
            }
        } else if (topicMatched) {
            results += ChatSearchResult(
                contactId = session.session.contactId,
                sessionId = session.session.sessionId,
                title = session.contactName,
                subtitle = session.session.topicName,
                message = null,
                preview = "话题名称匹配",
                timestamp = session.messages.lastOrNull()?.timestamp ?: session.session.updatedAt,
                matchType = SearchMatchType.TOPIC
            )
        }
    }

    return results
        .distinctBy { listOf(it.contactId, it.sessionId.orEmpty(), it.message?.messageId.orEmpty(), it.matchType.name).joinToString("_") }
        .sortedByDescending { it.timestamp }
}

private fun String.matchesSearchQuery(query: String): Boolean {
    val target = normalizeSearchText()
    val tokens = query.normalizeSearchText()
        .split(' ')
        .filter { it.isNotBlank() }
    return tokens.isNotEmpty() && tokens.all { target.contains(it) }
}

private fun String.normalizeSearchText(): String =
    lowercase(Locale.getDefault())
        .replace(Regex("\\s+"), " ")
        .trim()