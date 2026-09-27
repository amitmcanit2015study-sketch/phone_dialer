package com.amitbharat.phonedialer.recording

import android.content.Context
import android.media.AudioManager
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class CallRecorder(private val context: Context) {

    companion object {
        private const val TAG = "CallRecorder"
    }

    private var mediaRecorder: MediaRecorder? = null
    var isRecording = false
        private set
    var currentFilePath: String? = null
        private set

    fun startRecording(number: String, contactName: String? = null): Boolean {
        if (isRecording) return true
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val cleanNum = number.replace("[^0-9+]".toRegex(), "")
            val cleanName = contactName?.trim()?.replace("[\\\\/:*?\"<>|]".toRegex(), "_")

            val baseName = if (!cleanName.isNullOrBlank() && cleanName != cleanNum && !cleanName.equals("Unknown", ignoreCase = true)) {
                "Call_${cleanName}_${cleanNum}_${timeStamp}"
            } else {
                "Call_${cleanNum}_${timeStamp}"
            }

            // Always write to app-accessible recordings directory which works without Scoped Storage failures
            val recordDir = File(context.getExternalFilesDir(Environment.DIRECTORY_RECORDINGS) ?: context.filesDir, "CallRecordings")
            if (!recordDir.exists()) {
                recordDir.mkdirs()
            }

            // Try primary recording setup: VOICE_COMMUNICATION with MPEG_4 / AAC
            // Fallback setup: MIC with THREE_GPP / AMR_NB (widely supported across all Android chipsets)
            val audioSources = listOf(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.MIC
            )

            var recorder: MediaRecorder? = null
            var targetFile: File? = null

            for (source in audioSources) {
                try {
                    val file = File(recordDir, "$baseName.m4a")
                    val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        MediaRecorder(context)
                    } else {
                        @Suppress("DEPRECATION")
                        MediaRecorder()
                    }

                    mr.setAudioSource(source)
                    mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    mr.setAudioEncodingBitRate(128000)
                    mr.setAudioSamplingRate(44100)
                    mr.setOutputFile(file.absolutePath)
                    mr.prepare()
                    mr.start()

                    recorder = mr
                    targetFile = file
                    Log.d(TAG, "Recording successfully started with source: $source, file: ${file.absolutePath}")
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "Audio source $source failed: ${e.message}, trying next...")
                }
            }

            // If MPEG_4 failed on both sources, try 3GP AMR_NB fallback
            if (recorder == null) {
                try {
                    val file = File(recordDir, "$baseName.3gp")
                    val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        MediaRecorder(context)
                    } else {
                        @Suppress("DEPRECATION")
                        MediaRecorder()
                    }

                    mr.setAudioSource(MediaRecorder.AudioSource.MIC)
                    mr.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                    mr.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                    mr.setOutputFile(file.absolutePath)
                    mr.prepare()
                    mr.start()

                    recorder = mr
                    targetFile = file
                    Log.d(TAG, "Recording started with 3GP/AMR fallback")
                } catch (ex: Exception) {
                    Log.e(TAG, "Fallback recording also failed: ${ex.message}")
                }
            }

            if (recorder != null && targetFile != null) {
                mediaRecorder = recorder
                currentFilePath = targetFile.absolutePath
                isRecording = true
                return true
            }

            isRecording = false
            currentFilePath = null
            return false
        } catch (e: Exception) {
            Log.e(TAG, "startRecording exception", e)
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
            Log.e(TAG, "stopRecording exception", e)
        }
        mediaRecorder = null
        isRecording = false

        // Broadcast to MediaStore if possible
        if (path != null) {
            try {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(path),
                    arrayOf("audio/mp4", "audio/m4a", "audio/3gpp"),
                    null
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return path
    }
}
