package com.socialfirstaidkit.ui.settings

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.socialfirstaidkit.core.llm.impl.VivoOnDeviceProvider
import com.socialfirstaidkit.ui.theme.SocialFirstAidTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class VivoVisionProbeActivity : ComponentActivity() {

    @Inject
    lateinit var vivoOnDeviceProvider: VivoOnDeviceProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            runCatching {
                startActivity(
                    android.content.Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        }
        setContent {
            SocialFirstAidTheme {
                VivoVisionProbeScreen(
                    provider = vivoOnDeviceProvider,
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VivoVisionProbeScreen(
    provider: VivoOnDeviceProvider,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var prompt by remember {
        mutableStateOf("请详细描述这张图片，优先识别图中文字和聊天内容。")
    }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isRunning by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("请选择一张图片开始端侧识图验证") }
    var result by remember { mutableStateOf<VivoOnDeviceProvider.VisionProbeResult?>(null) }

    fun runProbe(image: Bitmap) {
        isRunning = true
        result = null
        status = "正在初始化多模态模型并执行 callVit..."
        scope.launch {
            val probeResult = runCatching {
                provider.describeBitmap(image, prompt)
            }.getOrElse { e ->
                Log.e("VivoVisionProbe", "probe failed", e)
                VivoOnDeviceProvider.VisionProbeResult(
                    success = false,
                    content = e.stackTraceToString(),
                    callVitMs = 0,
                    generationMs = 0,
                    totalMs = 0,
                    width = image.width,
                    height = image.height,
                    errorCode = "PROBE_EXCEPTION"
                )
            }
            result = probeResult
            status = if (probeResult.success) {
                "callVit 成功，已收到端侧模型描述"
            } else {
                "识图失败：${probeResult.errorCode ?: "UNKNOWN"}"
            }
            isRunning = false
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val decoded = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        }.getOrElse { e ->
            status = "图片读取失败：${e.message ?: e.javaClass.simpleName}"
            null
        }
        if (decoded != null) {
            bitmap = decoded
            runProbe(decoded)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("vivo 端侧识图测试", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                ),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("独立验证 callVit", fontWeight = FontWeight.Bold)
                    Text(
                        "这里直接使用官方 demo 的多模态 SDK 包装方式，不接入截图识别主流程。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        label = { Text("描述提示词") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { imagePicker.launch("image/*") },
                            enabled = !isRunning
                        ) {
                            Icon(Icons.Default.ImageSearch, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("选择图片")
                        }
                        OutlinedButton(
                            onClick = { bitmap?.let(::runProbe) },
                            enabled = bitmap != null && !isRunning
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("重新测试")
                        }
                    }
                }
            }

            bitmap?.let { image ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Image(
                        bitmap = image.asImageBitmap(),
                        contentDescription = "测试图片",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                ),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isRunning) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.size(8.dp))
                        }
                        Text(status, fontWeight = FontWeight.Medium)
                    }

                    result?.let { probe ->
                        Text("图片尺寸：${probe.width} x ${probe.height}")
                        Text("callVit 耗时：${probe.callVitMs} ms")
                        Text("生成耗时：${probe.generationMs} ms")
                        Text("总耗时：${probe.totalMs} ms")
                        if (probe.errorCode != null) {
                            Text("错误码：${probe.errorCode}", color = MaterialTheme.colorScheme.error)
                        }
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF171821)
                        ) {
                            Text(
                                text = probe.content.ifBlank { "模型没有返回文本" },
                                modifier = Modifier.padding(14.dp),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
