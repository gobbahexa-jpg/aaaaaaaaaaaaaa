package com.example.core

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.PlaybackParams
import android.os.Build
import android.util.Base64
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

data class VoiceRecordingState(
    val recording: Boolean = false,
    val paused: Boolean = false,
    val elapsedMs: Long = 0L,
    val waveform: List<Int> = emptyList(),
    val error: String? = null
)

data class RecordedVoiceClip(
    val base64Audio: String?,
    val durationMs: Long,
    val waveform: List<Int>,
    val fileSizeBytes: Long,
    val exceedsInlineLimit: Boolean
)

class VoiceRecorderController(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(VoiceRecordingState())
    val state: StateFlow<VoiceRecordingState> = _state.asStateFlow()

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var meterJob: Job? = null

    fun startRecording(): Boolean {
        stopInternal(deleteFile = true)
        return try {
            val dir = File(context.cacheDir, "dl_voice_notes").apply { mkdirs() }
            val file = File(dir, "dl_vn_${System.currentTimeMillis()}.m4a")
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioEncodingBitRate(32_000)
            mr.setAudioSamplingRate(16_000)
            mr.setOutputFile(file.absolutePath)
            mr.prepare()
            mr.start()

            recorder = mr
            outputFile = file
            _state.value = VoiceRecordingState(recording = true, paused = false, elapsedMs = 0L, waveform = emptyList())
            startMeterLoop()
            true
        } catch (_: Exception) {
            _state.value = VoiceRecordingState(error = context.getString(R.string.voice_mic_unavailable))
            stopInternal(deleteFile = true)
            false
        }
    }

    fun togglePauseResume() {
        val mr = recorder ?: return
        val current = _state.value
        if (!current.recording) return
        try {
            if (current.paused) {
                mr.resume()
                _state.value = current.copy(paused = false)
            } else {
                mr.pause()
                _state.value = current.copy(paused = true)
            }
        } catch (e: Exception) {
            _state.value = current.copy(error = "Pause/resume failed: ${e.message}")
        }
    }

    fun cancelRecording() {
        stopInternal(deleteFile = true)
        _state.value = VoiceRecordingState()
    }

    fun finishRecording(): RecordedVoiceClip? {
        val snapshot = _state.value
        val file = outputFile
        stopInternal(deleteFile = false)
        _state.value = VoiceRecordingState()
        if (file == null || !file.exists()) return null

        return try {
            val bytes = file.readBytes()
            val exceeds = bytes.size > MAX_INLINE_AUDIO_BYTES
            val b64 = if (!exceeds) Base64.encodeToString(bytes, Base64.NO_WRAP) else null
            RecordedVoiceClip(
                base64Audio = b64,
                durationMs = snapshot.elapsedMs.coerceAtLeast(500L),
                waveform = snapshot.waveform.takeLast(36).ifEmpty { listOf(20, 45, 70, 50, 30) },
                fileSizeBytes = bytes.size.toLong(),
                exceedsInlineLimit = exceeds
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun startMeterLoop() {
        meterJob?.cancel()
        meterJob = scope.launch(Dispatchers.Main) {
            while (_state.value.recording) {
                delay(150L)
                val cur = _state.value
                if (!cur.paused) {
                    val amp = try {
                        recorder?.maxAmplitude ?: 0
                    } catch (_: Exception) {
                        0
                    }
                    val normalized = ((amp / 32767f) * 100f).toInt().coerceIn(8, 100)
                    _state.value = cur.copy(
                        elapsedMs = cur.elapsedMs + 150L,
                        waveform = (cur.waveform + normalized).takeLast(36)
                    )
                }
            }
        }
    }

    private fun stopInternal(deleteFile: Boolean) {
        meterJob?.cancel()
        meterJob = null
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        if (deleteFile) {
            outputFile?.delete()
            outputFile = null
        }
    }

    companion object {
        const val MAX_INLINE_AUDIO_BYTES = 280_000
    }
}

data class VoicePlaybackState(
    val activeMessageId: String? = null,
    val playing: Boolean = false,
    val speed: Float = 1.0f,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
    val error: String? = null
)

class VoicePlaybackController(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(VoicePlaybackState())
    val state: StateFlow<VoicePlaybackState> = _state.asStateFlow()

    private var player: MediaPlayer? = null
    private var progressJob: Job? = null

    fun togglePlay(messageId: String, base64Audio: String?) {
        val current = _state.value
        if (current.activeMessageId == messageId && player != null) {
            val mp = player!!
            if (mp.isPlaying) {
                mp.pause()
                _state.value = current.copy(playing = false)
            } else {
                mp.start()
                _state.value = current.copy(playing = true)
                startProgressLoop()
            }
            return
        }

        stop()
        if (base64Audio.isNullOrBlank()) {
            _state.value = VoicePlaybackState(
                activeMessageId = messageId,
                error = context.getString(R.string.voice_audio_too_large)
            )
            return
        }

        try {
            val bytes = Base64.decode(base64Audio, Base64.DEFAULT)
            val cacheDir = File(context.cacheDir, "dl_voice_playback").apply { mkdirs() }
            val tempFile = File(cacheDir, "$messageId.m4a")
            FileOutputStream(tempFile).use { it.write(bytes) }

            val mp = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                prepare()
                playbackParams = PlaybackParams().setSpeed(current.speed)
                setOnCompletionListener {
                    stop()
                }
                start()
            }
            player = mp
            _state.value = VoicePlaybackState(
                activeMessageId = messageId,
                playing = true,
                speed = current.speed,
                positionMs = 0,
                durationMs = mp.duration.coerceAtLeast(1)
            )
            startProgressLoop()
        } catch (_: Exception) {
            _state.value = VoicePlaybackState(
                activeMessageId = messageId,
                error = context.getString(R.string.voice_playback_failed)
            )
            stop()
        }
    }

    fun cycleSpeed() {
        val nextSpeed = when (_state.value.speed) {
            1.0f -> 1.5f
            1.5f -> 2.0f
            else -> 1.0f
        }
        try {
            player?.playbackParams = PlaybackParams().setSpeed(nextSpeed)
        } catch (_: Exception) {
        }
        _state.value = _state.value.copy(speed = nextSpeed)
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch(Dispatchers.Main) {
            while (player?.isPlaying == true) {
                val mp = player ?: break
                _state.value = _state.value.copy(
                    positionMs = mp.currentPosition,
                    durationMs = mp.duration.coerceAtLeast(1)
                )
                delay(120L)
            }
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        _state.value = VoicePlaybackState(speed = _state.value.speed)
    }
}
