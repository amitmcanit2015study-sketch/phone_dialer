package com.amitbharat.phonedialer.ui.settings

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.amitbharat.phonedialer.R
import com.amitbharat.phonedialer.utils.LocaleHelper
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val currentLang = remember { LocaleHelper.getLanguage(context) }
    var selectedLanguage by remember { mutableStateOf(if (currentLang == "hi") "hi" else "en") }

    val appAboutText = """
        Phone Dialer - Modern, Fast & Secure Phone App
        
        Phone Dialer is a modern, fast, and secure Android phone application designed for effortless calling, smart T9 search, contact management, speed dial, call history, and call recording.
        
        • Developed by: Amit Bharat
        • Company: Rooys Soft Tech
        • Contact: rooyssofttech2020@gmail.com
        • Version: 1.0.1
        
        Install the attached APK to get started!
    """.trimIndent()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_about), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Icon
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = null,
                    modifier = Modifier.size(54.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.app_name),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = stringResource(R.string.app_version_name),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Main Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.about_description),
                        fontSize = 14.5.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Text(
                        text = stringResource(R.string.developed_by_full),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = stringResource(R.string.company_name_full),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Email: rooyssofttech2020@gmail.com",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { sendFeedbackEmail(context) }
                            .padding(vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Language Toggle Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "App Language / ऐप की भाषा",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                selectedLanguage = "en"
                                if (LocaleHelper.getLanguage(context) != "en") {
                                    LocaleHelper.setLocale(context, "en")
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selectedLanguage == "en") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Text("English", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                selectedLanguage = "hi"
                                if (LocaleHelper.getLanguage(context) != "hi") {
                                    LocaleHelper.setLocale(context, "hi")
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selectedLanguage == "hi") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Text("हिंदी", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    onClick = { shareAppApk(context, appAboutText) },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.share_app), fontWeight = FontWeight.Bold)
                }

                FilledTonalButton(
                    onClick = { downloadAppApk(context) },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.download_apk), fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { sendFeedbackEmail(context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.send_feedback), fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.about_footer),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun sendFeedbackEmail(context: Context) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:rooyssofttech2020@gmail.com")
        putExtra(Intent.EXTRA_EMAIL, arrayOf("rooyssofttech2020@gmail.com"))
        putExtra(Intent.EXTRA_SUBJECT, "Phone Dialer - Feedback & Support")
        val body = "Hello Rooys Soft Tech Team,\n\n" +
                "Feedback / Feature Request / Bug Report:\n\n\n" +
                "------------------------------\n" +
                "Device: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                "App Version: 1.0.1\n"
        putExtra(Intent.EXTRA_TEXT, body)
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Send Email"))
    } catch (e: Exception) {
        Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show()
    }
}

private fun downloadAppApk(context: Context) {
    Toast.makeText(context, "Downloading APK to Downloads folder...", Toast.LENGTH_SHORT).show()
    Executors.newSingleThreadExecutor().execute {
        try {
            val appInfo: ApplicationInfo = context.applicationInfo
            val originalApk = File(appInfo.sourceDir)
            val fileName = "phonedialer-amit-bharat.apk"
            var success = false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(originalApk).use { input ->
                            input.copyTo(out)
                        }
                    }
                    success = true
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val destApk = File(downloadsDir, fileName)
                FileInputStream(originalApk).use { input ->
                    FileOutputStream(destApk).use { output ->
                        input.copyTo(output)
                    }
                }
                success = true
            }

            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (success) {
                    Toast.makeText(context, "Saved to Downloads: $fileName", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Failed to download APK", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

private fun shareAppApk(context: Context, appAboutText: String) {
    Toast.makeText(context, "Preparing Phone Dialer APK to share...", Toast.LENGTH_SHORT).show()
    Executors.newSingleThreadExecutor().execute {
        try {
            val appInfo: ApplicationInfo = context.applicationInfo
            val originalApk = File(appInfo.sourceDir)

            val shareDir = File(context.cacheDir, "shared_apk")
            if (!shareDir.exists()) shareDir.mkdirs()
            val targetApk = File(shareDir, "phonedialer-amit-bharat.apk")

            FileInputStream(originalApk).use { input ->
                FileOutputStream(targetApk).use { output ->
                    input.copyTo(output)
                }
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                targetApk
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, apkUri)
                putExtra(Intent.EXTRA_SUBJECT, "Phone Dialer APK - by Amit Bharat (Rooys Soft Tech)")
                putExtra(Intent.EXTRA_TEXT, appAboutText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            android.os.Handler(android.os.Looper.getMainLooper()).post {
                context.startActivity(Intent.createChooser(shareIntent, "Share Phone Dialer APK & Details"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Phone Dialer - Modern Phone App")
                    putExtra(Intent.EXTRA_TEXT, appAboutText)
                }
                context.startActivity(Intent.createChooser(intent, "Share Phone Dialer App"))
            }
        }
    }
}
