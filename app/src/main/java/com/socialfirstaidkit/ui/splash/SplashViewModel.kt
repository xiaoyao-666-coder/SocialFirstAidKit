package com.socialfirstaidkit.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.socialfirstaidkit.core.data.stats.StatsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val statsManager: StatsManager
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress

    init {
        viewModelScope.launch {
            // 记录应用打开次数
            statsManager.incrementAppOpenCount()
            // 模拟初始化任务
            simulateLoadingTasks()
        }
    }

    private suspend fun simulateLoadingTasks() {
        // 模拟各种初始化任务
        val tasks = listOf(
            "初始化配置...",
            "加载AI模型...",
            "检查权限...",
            "准备就绪"
        )

        val progressStep = 1f / tasks.size

        tasks.forEachIndexed { index, _ ->
            delay(400) // 模拟每个任务的耗时
            _progress.value = (index + 1) * progressStep
        }

        delay(200) // 稍微停顿
        _isLoading.value = false
    }

    fun markLoadingComplete() {
        _isLoading.value = false
    }
}
