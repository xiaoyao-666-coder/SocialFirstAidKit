package com.socialfirstaidkit.ui.profile

import androidx.compose.animation.*
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.socialfirstaidkit.core.data.personalexpression.PersonalExpressionQuestionnaire

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressionQuestionnaireScreen(
    onBackClick: () -> Unit = {},
    onComplete: (Map<String, List<String>>) -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val questions = PersonalExpressionQuestionnaire.questions
    val totalPages = 4
    var currentPage by remember { mutableIntStateOf(0) }
    val answers = remember { mutableStateMapOf<String, MutableList<String>>() }
    var draftLoaded by remember { mutableStateOf(false) }

    val questionsPerPage = listOf(
        questions.subList(0, 4),
        questions.subList(4, 8),
        questions.subList(8, 11),
        questions.subList(11, 13)
    )

    val isLastPage = currentPage == totalPages - 1

    fun snapshotAnswers(): Map<String, List<String>> = answers.mapValues { it.value.toList() }

    fun saveDraft(page: Int = currentPage) {
        if (draftLoaded) {
            viewModel.saveQuestionnaireDraft(snapshotAnswers(), page.coerceIn(0, totalPages - 1))
        }
    }

    LaunchedEffect(Unit) {
        val draft = viewModel.loadQuestionnaireDraft()
        answers.clear()
        draft.answers.forEach { (key, value) ->
            answers[key] = value.toMutableList()
        }
        currentPage = draft.currentPage.coerceIn(0, totalPages - 1)
        draftLoaded = true
    }

    BackHandler {
        saveDraft()
        onBackClick()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("表达习惯问卷", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        saveDraft()
                        onBackClick()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 进度条
            LinearProgressIndicator(
                progress = { (currentPage + 1).toFloat() / totalPages },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "第 ${currentPage + 1}/$totalPages 页",
                modifier = Modifier.align(Alignment.CenterHorizontally),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 问题区域（固定高度，内部滚动）
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = currentPage,
                    transitionSpec = {
                        val enter = if (targetState > initialState) slideInHorizontally { it } else slideInHorizontally { -it }
                        val exit = if (targetState > initialState) slideOutHorizontally { -it } else slideOutHorizontally { it }
                        ContentTransform(enter, exit)
                    },
                    label = "page"
                ) { page ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        questionsPerPage[page].forEach { question ->
                            QuestionCard(
                                question = question,
                                selectedIds = answers[question.id].orEmpty(),
                                onToggle = { optionId ->
                                    val current = answers[question.id]?.toMutableList() ?: mutableListOf()
                                    if (question.maxSelections > 1) {
                                        if (optionId in current) {
                                            current.remove(optionId)
                                        } else if (current.size < question.maxSelections) {
                                            current.add(optionId)
                                        }
                                    } else {
                                        current.clear()
                                        current.add(optionId)
                                    }
                                    answers[question.id] = current
                                    saveDraft(page)
                                }
                            )
                        }
                    }
                }
            }

            // 底部按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (currentPage > 0) {
                    OutlinedButton(
                        onClick = {
                            val nextPage = currentPage - 1
                            currentPage = nextPage
                            saveDraft(nextPage)
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("上一页")
                    }
                }

                Button(
                    onClick = {
                        if (isLastPage) {
                            val result = answers.mapValues { it.value.toList() }
                            viewModel.completeQuestionnaire(result, enableAfter = false) {
                                onComplete(result)
                            }
                        } else {
                            val nextPage = currentPage + 1
                            currentPage = nextPage
                            saveDraft(nextPage)
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLastPage) {
                        Text("完成", fontWeight = FontWeight.Bold)
                    } else {
                        Text("下一页")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionCard(
    question: com.socialfirstaidkit.core.data.personalexpression.PersonalExpressionQuestion,
    selectedIds: List<String>,
    onToggle: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(question.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(question.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (question.maxSelections > 1) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("最多选 ${question.maxSelections} 项", fontSize = 11.sp, color = MaterialTheme.colorScheme.tertiary)
            }
            Spacer(modifier = Modifier.height(12.dp))

            question.options.forEach { option ->
                val isSelected = option.id in selectedIds
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onToggle(option.id) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(option.text, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal)
                            Text(option.hint, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}
