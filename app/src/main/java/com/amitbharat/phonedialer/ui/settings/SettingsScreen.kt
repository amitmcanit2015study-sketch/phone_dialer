package com.amitbharat.phonedialer.ui.settings

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amitbharat.phonedialer.R
import com.amitbharat.phonedialer.utils.PreferencesManager
import com.amitbharat.phonedialer.utils.ThemeMode

@Composable
fun SettingsScreen(
    onThemeChange: (ThemeMode) -> Unit,
    onOpenAbout: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager.getInstance(context) }

    var isAutoRecordAll by remember { mutableStateOf(prefs.isAutoCallRecordingEnabled()) }
    var isVibration by remember { mutableStateOf(prefs.isVibrationEnabled()) }
    var isSound by remember { mutableStateOf(prefs.isDialpadSoundEnabled()) }
    var currentTheme by remember { mutableStateOf(prefs.getThemeMode()) }

    fun requestDefaultDialer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Could not open default dialer prompt", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Could not open default dialer prompt", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Default Dialer Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.set_default_dialer_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.set_default_dialer_desc),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { requestDefaultDialer() }) {
                        Text(stringResource(R.string.btn_set_default))
                    }
                }
            }
        }

        // 2. Call Recording Controls
        item {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🎙️ Call Recording", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Record All Calls Automatically", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("Automatically record every incoming and outgoing call", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isAutoRecordAll,
                            onCheckedChange = {
                                isAutoRecordAll = it
                                prefs.setAutoCallRecordingEnabled(it)
                                Toast.makeText(context, if (it) "Auto recording enabled" else "Auto recording disabled", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }

        // 3. Sound & Vibration Feedback
        item {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🔔 Sound & Haptics", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Keypad Vibration Feedback", fontSize = 15.sp)
                        Switch(checked = isVibration, onCheckedChange = {
                            isVibration = it
                            prefs.setVibrationEnabled(it)
                        })
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Keypad Audio Tones", fontSize = 15.sp)
                        Switch(checked = isSound, onCheckedChange = {
                            isSound = it
                            prefs.setDialpadSoundEnabled(it)
                        })
                    }
                }
            }
        }

        // 4. Themes & Display
        item {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🎨 Appearance & Themes", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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

        // 5. About & Developer Info
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenAbout?.invoke() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.action_about), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text("Developer: Amit Bharat   v1.0.1", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }
        }
    }
}
