package com.socialfirstaidkit.ui.profile

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.socialfirstaidkit.core.data.personalexpression.*
import com.socialfirstaidkit.core.ui.theme.GlassCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressionEditorScreen(
    onBackClick: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val profile = uiState.expressionProfile
    val scrollState = rememberScrollState()
    val previewScope = rememberCoroutineScope()
    var previewText by remember { mutableStateOf("点任一分区的“看效果”，这里会展示调整后的表达效果。") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("编辑表达档案", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        if (profile == null || !profile.questionnaireCompleted) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("暂无档案数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        var editableProfile by remember(profile) { mutableStateOf(profile) }
        var preferredDraft by remember(profile) { mutableStateOf("") }
        var avoidedDraft by remember(profile) { mutableStateOf("") }

        fun showPreview(text: String) {
            previewText = text
            previewScope.launch {
                scrollState.animateScrollTo(scrollState.maxValue)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(bottom = 32.dp)
        ) {
            GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("表达画像", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(editableProfile.readableSummary(), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        editableProfile.summaryTags().forEach { tag ->
                            AssistChip(onClick = {}, label = { Text(tag, fontSize = 11.sp) })
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SectionCard(
                title = "整体语气",
                onTry = {
                    showPreview(buildPreview(editableProfile, "整体语气会更接近${warmthLabel(editableProfile.coreStyle.warmth)}、${directnessLabel(editableProfile.coreStyle.directness)}。"))
                }
            ) {
                SliderRow("直接程度", editableProfile.coreStyle.directness) { editableProfile = editableProfile.copy(coreStyle = editableProfile.coreStyle.copy(directness = it)) }
                SliderRow("温暖程度", editableProfile.coreStyle.warmth) { editableProfile = editableProfile.copy(coreStyle = editableProfile.coreStyle.copy(warmth = it)) }
                SliderRow("正式程度", editableProfile.coreStyle.formality) { editableProfile = editableProfile.copy(coreStyle = editableProfile.coreStyle.copy(formality = it)) }
                SliderRow("句子长度", editableProfile.coreStyle.sentenceLength) { editableProfile = editableProfile.copy(coreStyle = editableProfile.coreStyle.copy(sentenceLength = it)) }
                SliderRow("幽默程度", editableProfile.coreStyle.humor) { editableProfile = editableProfile.copy(coreStyle = editableProfile.coreStyle.copy(humor = it)) }
            }

            SectionCard(
                title = "场景偏好",
                onTry = {
                    showPreview(buildPreview(editableProfile, editableProfile.sampleReplies["comfort"].orEmpty()))
                }
            ) {
                EnumChips("安慰方式", ComfortStyle.entries, editableProfile.sceneStyles.comfortStyle) {
                    editableProfile = editableProfile.copy(sceneStyles = editableProfile.sceneStyles.copy(comfortStyle = it))
                }
                EnumChips("拒绝方式", RefusalStyle.entries, editableProfile.sceneStyles.refusalStyle) {
                    editableProfile = editableProfile.copy(sceneStyles = editableProfile.sceneStyles.copy(refusalStyle = it))
                }
                EnumChips("道歉方式", ApologyStyle.entries, editableProfile.sceneStyles.apologyStyle) {
                    editableProfile = editableProfile.copy(sceneStyles = editableProfile.sceneStyles.copy(apologyStyle = it))
                }
                EnumChips("冲突处理", ConflictStyle.entries, editableProfile.sceneStyles.conflictStyle) {
                    editableProfile = editableProfile.copy(sceneStyles = editableProfile.sceneStyles.copy(conflictStyle = it))
                }
            }

            SectionCard(
                title = "关系差异",
                onTry = {
                    showPreview(buildPreview(editableProfile, "对熟人更自然，对职场和陌生关系保留一点分寸。"))
                }
            ) {
                RelationshipOffsetEditor(editableProfile.relationshipOffsets) { next ->
                    editableProfile = editableProfile.copy(relationshipOffsets = next)
                }
            }

            SectionCard(
                title = "语言细节",
                onTry = {
                    showPreview(buildPreview(editableProfile, "语气词和 emoji 会按这里的倾向轻量加入。"))
                }
            ) {
                Text("emoji 使用", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    EmojiUsage.entries.forEach { usage ->
                        FilterChip(
                            selected = editableProfile.languageMarkers.emojiUsage == usage,
                            onClick = { editableProfile = editableProfile.copy(languageMarkers = editableProfile.languageMarkers.copy(emojiUsage = usage)) },
                            label = { Text(usage.label, fontSize = 12.sp) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                SliderRow("语气词频率", editableProfile.languageMarkers.fillerWords) { editableProfile = editableProfile.copy(languageMarkers = editableProfile.languageMarkers.copy(fillerWords = it)) }
                SliderRow("网络用语", editableProfile.languageMarkers.networkSlang) { editableProfile = editableProfile.copy(languageMarkers = editableProfile.languageMarkers.copy(networkSlang = it)) }
            }

            ExpressionListSection(
                title = "常用表达",
                iconColor = MaterialTheme.colorScheme.primary,
                values = editableProfile.preferredExpressions,
                draft = preferredDraft,
                onDraftChange = { preferredDraft = it },
                onAdd = {
                    val text = preferredDraft.trim()
                    if (text.isNotBlank()) {
                        editableProfile = editableProfile.copy(preferredExpressions = (editableProfile.preferredExpressions + text).distinct().take(16))
                        preferredDraft = ""
                    }
                },
                onRemove = { item ->
                    editableProfile = editableProfile.copy(preferredExpressions = editableProfile.preferredExpressions - item)
                },
                onTry = {
                    showPreview(buildPreview(editableProfile, editableProfile.preferredExpressions.firstOrNull() ?: "这里会优先参考你常用的说法。"))
                }
            )

            ExpressionListSection(
                title = "禁用表达",
                iconColor = MaterialTheme.colorScheme.error,
                values = editableProfile.avoidedExpressions,
                draft = avoidedDraft,
                onDraftChange = { avoidedDraft = it },
                onAdd = {
                    val text = avoidedDraft.trim()
                    if (text.isNotBlank()) {
                        editableProfile = editableProfile.copy(avoidedExpressions = (editableProfile.avoidedExpressions + text).distinct().take(16))
                        avoidedDraft = ""
                    }
                },
                onRemove = { item ->
                    editableProfile = editableProfile.copy(avoidedExpressions = editableProfile.avoidedExpressions - item)
                },
                onTry = {
                    showPreview(buildPreview(editableProfile, "会避开这些表达，不把回复写成你不想要的样子。"))
                }
            )

            GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("表达效果预览", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(previewText, fontSize = 14.sp, lineHeight = 21.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.saveEditedProfile(editableProfile); onBackClick() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("保存修改", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    onTry: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = onTry) {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("看效果", fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, onValueChange: (Float) -> Unit) {
    val coercedValue = value.coerceIn(1f, 5f)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, fontSize = 13.sp, modifier = Modifier.width(80.dp))
        Slider(
            value = coercedValue,
            onValueChange = {
                val rounded = Math.round(it * 10f) / 10f
                onValueChange(rounded)
            },
            valueRange = 1f..5f,
            modifier = Modifier.weight(1f)
        )
        Text(String.format("%.1f", coercedValue), fontSize = 12.sp, modifier = Modifier.width(34.dp))
    }
}

@Composable
private fun <T> EnumChips(
    label: String,
    values: Iterable<T>,
    selected: T,
    onSelected: (T) -> Unit
) where T : Enum<T> {
    Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    Spacer(modifier = Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        values.forEach { value ->
            val text = when (value) {
                is ComfortStyle -> value.label
                is RefusalStyle -> value.label
                is ApologyStyle -> value.label
                is ConflictStyle -> value.label
                else -> value.name
            }
            FilterChip(selected = selected == value, onClick = { onSelected(value) }, label = { Text(text, fontSize = 11.sp) })
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun RelationshipOffsetEditor(
    offsets: Map<String, ExpressionRelationshipOffset>,
    onChange: (Map<String, ExpressionRelationshipOffset>) -> Unit
) {
    val labels = mapOf(
        "friend" to "朋友",
        "family" to "家人",
        "romantic" to "亲密关系",
        "colleague" to "同事",
        "leader" to "领导",
        "stranger" to "陌生人"
    )
    offsets.forEach { (key, offset) ->
        Text(labels[key] ?: key, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        SliderRow("正式度", offset.formalityOffset + 3f) {
            onChange(offsets + (key to offset.copy(formalityOffset = it - 3f)))
        }
        SliderRow("直接度", offset.directnessOffset + 3f) {
            onChange(offsets + (key to offset.copy(directnessOffset = it - 3f)))
        }
        SliderRow("温暖度", offset.warmthOffset + 3f) {
            onChange(offsets + (key to offset.copy(warmthOffset = it - 3f)))
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun ExpressionListSection(
    title: String,
    iconColor: Color,
    values: List<String>,
    draft: String,
    onDraftChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onTry: () -> Unit
) {
    SectionCard(title = title, onTry = onTry) {
        values.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (title == "禁用表达") Icons.Default.Block else Icons.Default.AutoAwesome, contentDescription = null, tint = iconColor, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(item, fontSize = 13.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { onRemove(item) }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "删除", modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("新增$title") }
            )
            IconButton(onClick = onAdd, enabled = draft.isNotBlank()) {
                Icon(Icons.Default.Add, contentDescription = "添加")
            }
        }
    }
}

private fun buildPreview(profile: PersonalExpressionProfile, seed: String): String {
    val base = seed.ifBlank { profile.sampleReplies["comfort"].orEmpty().ifBlank { "这件事听起来确实不太舒服，先别太怪自己。" } }
    val withFiller = if (profile.languageMarkers.fillerWords > 3.6f && !base.endsWith("呀。")) {
        base.removeSuffix("。") + "呀。"
    } else {
        base
    }
    return when (profile.languageMarkers.emojiUsage) {
        EmojiUsage.NONE -> withFiller
        EmojiUsage.LOW -> withFiller + " 🙂"
        EmojiUsage.MEDIUM -> withFiller + " 🙂"
        EmojiUsage.HIGH -> withFiller + " 🙂✨"
    }
}
