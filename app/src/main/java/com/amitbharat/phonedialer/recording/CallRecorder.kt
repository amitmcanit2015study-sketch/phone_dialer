package com.amitbharat.phonedialer.recording

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class CallRecorder(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    var isRecording = false
        private set
    var currentFilePath: String? = null
        private set

    fun startRecording(number: String): Boolean {
        if (isRecording) return false
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val cleanNum = number.replace("[^0-9+]".toRegex(), "")
            val fileName = "REC_${cleanNum}_${timeStamp}.m4a"

            val recordDir = File(context.getExternalFilesDir(null), "Recordings").apply { mkdirs() }
            val file = File(recordDir, fileName)
            currentFilePath = file.absolutePath

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            isRecording = false
            currentFilePath = null
            return false
        }
    }

    fun stopRecording(): String? {
        if (!isRecording) return null
        val path = currentFilePath
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaRecorder = null
        isRecording = false
        return path
    }
}
