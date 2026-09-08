package com.amitbharat.phonedialer.recording

import android.content.Context
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class CallRecorder(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    var isRecording = false
        private set
    var currentFilePath: String? = null
        private set

    fun startRecording(number: String, contactName: String? = null): Boolean {
        if (isRecording) return false
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val cleanNum = number.replace("[^0-9+]".toRegex(), "")
            val cleanName = contactName?.trim()?.replace("[\\\\/:*?\"<>|]".toRegex(), "_")

            val fileName = if (!cleanName.isNullOrBlank() && cleanName != cleanNum && !cleanName.equals("Unknown", ignoreCase = true)) {
                "Call_${cleanName}_${cleanNum}_${timeStamp}.m4a"
            } else {
                "Call_${cleanNum}_${timeStamp}.m4a"
            }

            // Save to shared public Recordings/CallRecordings directory so Song Player & device scanners find it
            val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RECORDINGS)
            val recordDir = File(publicDir, "CallRecordings").takeIf { it.exists() || it.mkdirs() }
                ?: File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "CallRecordings").takeIf { it.exists() || it.mkdirs() }
                ?: File(context.getExternalFilesDir(null), "CallRecordings").apply { mkdirs() }

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

        // Broadcast to MediaStore so Song Player and other media apps can immediately see and index this recording
        if (path != null) {
            try {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(path),
                    arrayOf("audio/mp4", "audio/m4a"),
                    null
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return path
    }
}
