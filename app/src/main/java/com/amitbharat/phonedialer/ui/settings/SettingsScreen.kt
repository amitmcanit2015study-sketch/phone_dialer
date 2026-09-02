package com.amitbharat.phonedialer.ui.settings

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amitbharat.phonedialer.telecom.TelecomHelper
import com.amitbharat.phonedialer.utils.PreferencesManager
import com.amitbharat.phonedialer.utils.ThemeMode
import java.io.File

@Composable
fun SettingsScreen(
    onThemeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager.getInstance(context) }
    var currentTheme by remember { mutableStateOf(prefs.getThemeMode()) }
    var isAutoRecord by remember { mutableStateOf(prefs.isAutoCallRecordingEnabled()) }
    var isVibration by remember { mutableStateOf(prefs.isVibrationEnabled()) }
    var isSound by remember { mutableStateOf(prefs.isDialpadSoundEnabled()) }
    var showAboutDialog by remember { mutableStateOf(false) }

    fun requestDefaultDialer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            roleManager?.createRequestRoleIntent(RoleManager.ROLE_DIALER)?.let {
                context.startActivity(it)
            }
        } else {
            val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
            }
            context.startActivity(intent)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Default Phone App Setup Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Default Phone App",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Set Phone Dialer as your default calling app to enable in-call full screen, call recording, and incoming call alerts.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { requestDefaultDialer() }) {
                        Text("Set as Default")
                    }
                }
            }
        }

        // 2. Appearance & Themes
        item {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Appearance", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(10.dp))
                    listOf(
                        ThemeMode.SYSTEM to "System Default",
                        ThemeMode.LIGHT to "Light Mode",
                        ThemeMode.DARK to "Dark Mode",
                        ThemeMode.AMOLED to "AMOLED Pure Black"
                    ).forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentTheme = mode
                                    prefs.setThemeMode(mode)
                                    onThemeChange(mode)
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = currentTheme == mode, onClick = {
                                currentTheme = mode
                                prefs.setThemeMode(mode)
                                onThemeChange(mode)
                            })
                            Spacer(Modifier.width(8.dp))
                            Text(label, fontSize = 15.sp)
                        }
                    }
                }
            }
        }

        // 3. Call Settings
        item {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Calling & Feedback", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto Call Recording", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("Automatically record incoming and outgoing calls", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = isAutoRecord, onCheckedChange = {
                            isAutoRecord = it
                            prefs.setAutoCallRecordingEnabled(it)
                        })
                    }
                    Divider(modifier = Modifier.padding(vertical = 6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Dialpad Vibration Feedback", fontSize = 15.sp)
                        Switch(checked = isVibration, onCheckedChange = {
                            isVibration = it
                            prefs.setVibrationEnabled(it)
                        })
                    }
                    Divider(modifier = Modifier.padding(vertical = 6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Dialpad Audio Tone", fontSize = 15.sp)
                        Switch(checked = isSound, onCheckedChange = {
                            isSound = it
                            prefs.setDialpadSoundEnabled(it)
                        })
                    }
                }
            }
        }

        // 4. About & Developer Info
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().clickable { showAboutDialog = true }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("About Phone Dialer", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text("Developed by Amit Bharat • v1.0.0.1", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }
        }
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("Phone Dialer", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Developer: Amit Bharat", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text("Company: Rooys Soft Tech", fontSize = 14.sp)
                    Text("Email: rooyssofttech2020@gmail.com", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text("Version: v1.0.0.1 (Production Build)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("A modern, fast, and secure phone dialer featuring smart T9 search, contact management, speed dial, and call recording.", fontSize = 13.sp)
                }
            },
            confirmButton = {
                Button(onClick = { showAboutDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
