package com.socialfirstaidkit.feature.chatassist.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShortVoiceAudioRecorder @Inject constructor() {

    companion object {
        private const val TAG = "ShortVoiceRecorder"
        private const val SAMPLE_RATE = 16000
        private const val FRAME_DURATION_MS = 40
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private val BUFFER_SIZE = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        ).coerceAtLeast(SAMPLE_RATE * 2 * FRAME_DURATION_MS / 1000)
    }

    private val isRecording = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null

    fun startRecording(): Flow<ByteArray> = flow {
        if (isRecording.get()) return@flow

        cleanup()
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            BUFFER_SIZE
        )

        val recorder = audioRecord
        if (recorder?.state != AudioRecord.STATE_INITIALIZED) {
            cleanup()
            throw IllegalStateException("AudioRecord 初始化失败")
        }

        isRecording.set(true)
        recorder.startRecording()

        val buffer = ByteArray(SAMPLE_RATE * 2 * FRAME_DURATION_MS / 1000)
        try {
            while (isRecording.get()) {
                val readSize = recorder.read(buffer, 0, buffer.size)
                if (readSize > 0) {
                    emit(buffer.copyOf(readSize))
                }
            }
        } finally {
            cleanup()
        }
    }.flowOn(Dispatchers.IO)

    fun stopRecording() {
        if (!isRecording.get()) return
        isRecording.set(false)
    }

    fun hasRecordPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun cleanup() {
        val recorder = audioRecord
        audioRecord = null
        try {
            if (recorder != null && recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                recorder.stop()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Recorder was already stopped", e)
        }
        try {
            recorder?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release recorder", e)
        }
        isRecording.set(false)
    }
}
