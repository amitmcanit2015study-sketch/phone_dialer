package com.amitbharat.phonedialer.ui.settings

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amitbharat.phonedialer.ui.theme.AccentGreen
import com.amitbharat.phonedialer.ui.theme.AccentRed
import com.amitbharat.phonedialer.utils.PreferencesManager
import com.amitbharat.phonedialer.utils.ThemeMode

enum class SettingsSection {
    CALL,
    MESSAGE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onThemeChange: (ThemeMode) -> Unit,
    onOpenAbout: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager.getInstance(context) }
    var selectedSection by remember { mutableStateOf(SettingsSection.CALL) }

    // Dialog state for Call Settings
    var showCallerIdSpamDialog by remember { mutableStateOf(false) }
    var showAssistedDialingDialog by remember { mutableStateOf(false) }
    var showBlockedNumbersDialog by remember { mutableStateOf(false) }
    var showCallRecordingDialog by remember { mutableStateOf(false) }
    var showDisplayOptionsDialog by remember { mutableStateOf(false) }
    var showIncomingGestureDialog by remember { mutableStateOf(false) }
    var showSoundsVibrationDialog by remember { mutableStateOf(false) }
    var showVoicemailDialog by remember { mutableStateOf(false) }
    var showCallingCardDialog by remember { mutableStateOf(false) }
    var showCallerIdAnnounceDialog by remember { mutableStateOf(false) }
    var showFlipToSilenceDialog by remember { mutableStateOf(false) }

    // State bindings
    var isCallerIdSpamEnabled by remember { mutableStateOf(prefs.isBlockSpamCallsEnabled()) }
    var isAssistedDialingEnabled by remember { mutableStateOf(prefs.isAssistedDialingEnabled()) }
    var isBlockUnknown by remember { mutableStateOf(prefs.isBlockUnknownCallsEnabled()) }
    var blockedNumbers by remember { mutableStateOf(prefs.getBlockedNumbers().toList()) }
    var newBlockNumberInput by remember { mutableStateOf("") }
    var callRecordingMode by remember { mutableStateOf(prefs.getCallRecordingMode()) }
    var isFlipToSilence by remember { mutableStateOf(prefs.isFlipToSilenceEnabled()) }
    var isCallerIdAnnounce by remember { mutableStateOf(prefs.isCallerIdAnnouncementEnabled()) }
    var isDialpadSound by remember { mutableStateOf(prefs.isDialpadSoundEnabled()) }
    var isDialpadVibrate by remember { mutableStateOf(prefs.isVibrationEnabled()) }

    // Message Settings State
    var useSimpleChars by remember { mutableStateOf(prefs.isUseSimpleCharactersEnabled()) }
    var smsDeliveryReports by remember { mutableStateOf(prefs.isSmsDeliveryReportsEnabled()) }
    var showIphoneReactions by remember { mutableStateOf(prefs.isShowIphoneReactionsEnabled()) }
    var smsNotifications by remember { mutableStateOf(prefs.isSmsNotificationsEnabled()) }
    var smsSound by remember { mutableStateOf(prefs.isSmsSoundEnabled()) }
    var smsVibration by remember { mutableStateOf(prefs.isSmsVibrationEnabled()) }
    var autoRetrieveMms by remember { mutableStateOf(prefs.isAutoRetrieveMmsEnabled()) }
    var groupMessaging by remember { mutableStateOf(prefs.isGroupMessagingEnabled()) }
    var autoDeleteOldMessages by remember { mutableStateOf(prefs.isAutoDeleteOldMessagesEnabled()) }

    var showQuickRepliesDialog by remember { mutableStateOf(false) }
    var quickReplies by remember { mutableStateOf(prefs.getQuickResponses()) }
    var newReplyText by remember { mutableStateOf("") }

    // Carrier info detection
    val telephonyManager = remember { context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager }
    val simName = remember {
        val op = telephonyManager?.simOperatorName?.takeIf { it.isNotBlank() } ?: "Jio"
        "$op 6574"
    }
    val simNumber = remember {
        telephonyManager?.line1Number?.takeIf { it.isNotBlank() } ?: "098628 26574"
    }

    // System roles verification
    var roleRefreshTrigger by remember { mutableIntStateOf(0) }
    val isDefaultDialer = remember(selectedSection, roleRefreshTrigger) {
        com.amitbharat.phonedialer.telecom.TelecomHelper.isDefaultDialer(context)
    }
    val isDefaultSms = remember(selectedSection, roleRefreshTrigger) {
        Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
    }

    val roleLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        roleRefreshTrigger++
    }

    fun requestDefaultDialer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                try {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                    roleLauncher.launch(intent)
                    return
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        try {
            val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
            }
            roleLauncher.launch(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                context.startActivity(intent)
            } catch (ex: Exception) {
                Toast.makeText(context, "Could not open default dialer settings", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun requestDefaultSms() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                try {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                    roleLauncher.launch(intent)
                    return
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        try {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
            }
            roleLauncher.launch(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                context.startActivity(intent)
            } catch (ex: Exception) {
                Toast.makeText(context, "Could not open default SMS prompt", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Tab Navigation
        TabRow(
            selectedTabIndex = if (selectedSection == SettingsSection.CALL) 0 else 1,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(
                selected = selectedSection == SettingsSection.CALL,
                onClick = { selectedSection = SettingsSection.CALL },
                text = { Text("Call Settings", fontWeight = FontWeight.SemiBold, fontSize = 15.sp) },
                icon = { Icon(Icons.Default.Call, contentDescription = "Call Settings") }
            )
            Tab(
                selected = selectedSection == SettingsSection.MESSAGE,
                onClick = { selectedSection = SettingsSection.MESSAGE },
                text = { Text("Message Settings", fontWeight = FontWeight.SemiBold, fontSize = 15.sp) },
                icon = { Icon(Icons.AutoMirrored.Filled.Message, contentDescription = "Message Settings") }
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (selectedSection == SettingsSection.CALL) {
                // ==========================================
                // CALL SETTINGS SECTION
                // ==========================================

                // Default Dialer Card
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDefaultDialer) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (isDefaultDialer) Icons.Default.CheckCircle else Icons.Default.Call,
                                    contentDescription = null,
                                    tint = if (isDefaultDialer) AccentGreen else MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (isDefaultDialer) "Default Phone App (Active)" else "Set as Default Phone App",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = if (isDefaultDialer) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = if (isDefaultDialer) "Phone Dialer is currently your default application for placing and receiving calls."
                                else "Set Phone Dialer as your default phone app to receive incoming calls with the full-screen caller ID and dial numbers directly.",
                                fontSize = 13.sp,
                                color = if (isDefaultDialer) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                            if (!isDefaultDialer) {
                                Spacer(Modifier.height(12.dp))
                                Button(onClick = { requestDefaultDialer() }) {
                                    Text("Set as Default Phone App")
                                }
                            }
                        }
                    }
                }

                // Call Assist Category
                item {
                    SettingsCategoryHeader("Call Assist")
                    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column {
                            SettingsItemRow(
                                title = "Caller ID and spam",
                                icon = Icons.Default.Security,
                                subtitle = if (isCallerIdSpamEnabled) "Filter spam is On" else "Off",
                                onClick = { showCallerIdSpamDialog = true }
                            )
                        }
                    }
                }

                // General Category
                item {
                    SettingsCategoryHeader("General")
                    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column {
                            SettingsItemRow(
                                title = "Accessibility",
                                icon = Icons.Default.Accessibility,
                                subtitle = "Hearing aids and TTY settings",
                                onClick = {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Accessibility settings unavailable", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            SettingsItemRow(
                                title = "Assisted dialling",
                                icon = Icons.Default.Public,
                                subtitle = if (isAssistedDialingEnabled) "Enabled" else "Disabled",
                                onClick = { showAssistedDialingDialog = true }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            SettingsItemRow(
                                title = "Blocked numbers",
                                icon = Icons.Default.Block,
                                subtitle = "${blockedNumbers.size} blocked numbers",
                                onClick = { showBlockedNumbersDialog = true }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            SettingsItemRow(
                                title = "Calling accounts",
                                icon = Icons.Default.SimCard,
                                subtitle = "Manage SIMs and SIP calling accounts",
                                onClick = {
                                    try {
                                        context.startActivity(Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS))
                                    } catch (e: Exception) {
                                        try {
                                            context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                                        } catch (ex: Exception) {
                                            Toast.makeText(context, "Calling accounts unavailable", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            SettingsItemRow(
                                title = "Call recording",
                                icon = Icons.Default.Mic,
                                subtitle = when (callRecordingMode) {
                                    "ALL" -> "Always record all calls"
                                    "UNKNOWN" -> "Record unknown numbers only"
                                    else -> "Manual recording only"
                                },
                                onClick = { showCallRecordingDialog = true }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            SettingsItemRow(
                                title = "Display options",
                                icon = Icons.Default.Palette,
                                subtitle = "Sort order, name format and theme",
                                onClick = { showDisplayOptionsDialog = true }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            SettingsItemRow(
                                title = "Incoming call gesture",
                                icon = Icons.Default.TouchApp,
                                subtitle = "Gestures to answer or silence calls",
                                onClick = { showIncomingGestureDialog = true }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            SettingsItemRow(
                                title = "Quick responses",
                                icon = Icons.Default.Quickreply,
                                subtitle = "Edit call decline text replies",
                                onClick = { showQuickRepliesDialog = true }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            SettingsItemRow(
                                title = "Sounds and vibration",
                                icon = Icons.AutoMirrored.Filled.VolumeUp,
                                subtitle = "Dialpad tones and vibration feedback",
                                onClick = { showSoundsVibrationDialog = true }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            SettingsItemRow(
                                title = "Voicemail",
                                icon = Icons.Default.Voicemail,
                                subtitle = "Service and notification settings",
                                onClick = { showVoicemailDialog = true }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            SettingsItemRow(
                                title = "Contact ringtones",
                                icon = Icons.Default.MusicNote,
                                subtitle = "Set custom ringtones for callers",
                                onClick = {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Sound settings unavailable", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            SettingsItemRow(
                                title = "Calling card",
                                icon = Icons.Default.CreditCard,
                                subtitle = "Configure international dialing rules",
                                onClick = { showCallingCardDialog = true }
                            )
                        }
                    }
                }

                // Advanced Category
                item {
                    SettingsCategoryHeader("Advanced")
                    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column {
                            SettingsItemRow(
                                title = "Caller ID announcement",
                                icon = Icons.Default.RecordVoiceOver,
                                subtitle = if (isCallerIdAnnounce) "Always announce" else "Never",
                                onClick = { showCallerIdAnnounceDialog = true }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            SettingsItemRow(
                                title = "Flip to silence",
                                icon = Icons.Default.ScreenRotation,
                                subtitle = if (isFlipToSilence) "Turned On" else "Off",
                                onClick = { showFlipToSilenceDialog = true }
                            )
                        }
                    }
                }

            } else {
                // ==========================================
                // MESSAGE SETTINGS SECTION (Matches Image 3)
                // ==========================================

                // Carrier / SIM Card Container
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.SimCard, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = simName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            // MMS turned off banner
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 14.dp)
                            ) {
                                Text(
                                    text = "MMS turned off",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Your operator doesn't allow outgoing MMS messages",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // Use simple characters
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Use simple characters", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                                Switch(
                                    checked = useSimpleChars,
                                    onCheckedChange = {
                                        useSimpleChars = it
                                        prefs.setUseSimpleCharactersEnabled(it)
                                    }
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // Get SMS delivery reports
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text("Get SMS delivery reports", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(Modifier.height(2.dp))
                                    Text("Find out when an SMS message is delivered", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = smsDeliveryReports,
                                    onCheckedChange = {
                                        smsDeliveryReports = it
                                        prefs.setSmsDeliveryReportsEnabled(it)
                                    }
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // Show iPhone reactions as emoji
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Show iPhone reactions as emoji", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                                Switch(
                                    checked = showIphoneReactions,
                                    onCheckedChange = {
                                        showIphoneReactions = it
                                        prefs.setShowIphoneReactionsEnabled(it)
                                    }
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // Wireless emergency alerts
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        try {
                                            context.startActivity(Intent("android.cellbroadcastreceiver.CB_SETTINGS"))
                                        } catch (e: Exception) {
                                            try {
                                                context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                                            } catch (ex: Exception) {
                                                Toast.makeText(context, "Emergency alerts settings unavailable", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                    .padding(horizontal = 18.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Wireless emergency alerts", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                            }

                            HorizontalDivider(modifier = Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // Phone number
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 14.dp)
                            ) {
                                Text("Phone number", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.height(2.dp))
                                Text(simNumber, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            HorizontalDivider(modifier = Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // SMSC
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 14.dp)
                            ) {
                                Text("SMSC", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                                Spacer(Modifier.height(2.dp))
                                Text("+917007075009", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // Additional Messaging Settings (User: "and add more")
                item {
                    SettingsCategoryHeader("Additional Messaging Options")
                    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Default SMS App
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Default SMS App", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(if (isDefaultSms) "Active" else "Tap to set default", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (!isDefaultSms) {
                                    Button(onClick = { requestDefaultSms() }) {
                                        Text("Set Default")
                                    }
                                } else {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentGreen)
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                            // Auto-retrieve MMS
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Auto-Retrieve MMS", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text("Automatically download media attachments", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = autoRetrieveMms,
                                    onCheckedChange = {
                                        autoRetrieveMms = it
                                        prefs.setAutoRetrieveMmsEnabled(it)
                                    }
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                            // Group Messaging
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Group Messaging", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text("Send MMS replies to all recipients", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = groupMessaging,
                                    onCheckedChange = {
                                        groupMessaging = it
                                        prefs.setGroupMessagingEnabled(it)
                                    }
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                            // Message Notifications
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("SMS Notifications", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text("Show push alerts for incoming texts", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = smsNotifications,
                                    onCheckedChange = {
                                        smsNotifications = it
                                        prefs.setSmsNotificationsEnabled(it)
                                    }
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                            // Sound & Vibrate
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Vibrate on Incoming SMS", fontSize = 14.sp)
                                Switch(
                                    checked = smsVibration,
                                    onCheckedChange = {
                                        smsVibration = it
                                        prefs.setSmsVibrationEnabled(it)
                                    }
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                            // Auto-Delete Old Messages
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Auto-Delete Old Messages", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text("Delete old texts when limit is reached", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = autoDeleteOldMessages,
                                    onCheckedChange = {
                                        autoDeleteOldMessages = it
                                        prefs.setAutoDeleteOldMessagesEnabled(it)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ========================================================
    // CALL SETTINGS INTERACTIVE DIALOGS
    // ========================================================

    // 1. Caller ID & Spam Dialog
    if (showCallerIdSpamDialog) {
        AlertDialog(
            onDismissRequest = { showCallerIdSpamDialog = false },
            title = { Text("Caller ID and spam", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("See caller and spam ID", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Identify business and spam numbers", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isCallerIdSpamEnabled,
                            onCheckedChange = {
                                isCallerIdSpamEnabled = it
                                prefs.setBlockSpamCallsEnabled(it)
                            }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Filter spam calls", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Prevent suspected spam calls from disturbing you", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isCallerIdSpamEnabled,
                            onCheckedChange = {
                                isCallerIdSpamEnabled = it
                                prefs.setBlockSpamCallsEnabled(it)
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCallerIdSpamDialog = false }) { Text("Done") }
            }
        )
    }

    // 2. Assisted Dialing Dialog
    if (showAssistedDialingDialog) {
        AlertDialog(
            onDismissRequest = { showAssistedDialingDialog = false },
            title = { Text("Assisted dialling", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Assisted dialling", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Automatically predict and add country codes when calling from abroad", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isAssistedDialingEnabled,
                            onCheckedChange = {
                                isAssistedDialingEnabled = it
                                prefs.setAssistedDialingEnabled(it)
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAssistedDialingDialog = false }) { Text("Done") }
            }
        )
    }

    // 3. Blocked Numbers Dialog
    if (showBlockedNumbersDialog) {
        AlertDialog(
            onDismissRequest = { showBlockedNumbersDialog = false },
            title = { Text("Blocked numbers", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Unknown", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Block calls from unidentified callers", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isBlockUnknown,
                            onCheckedChange = {
                                isBlockUnknown = it
                                prefs.setBlockUnknownCallsEnabled(it)
                            }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newBlockNumberInput,
                            onValueChange = { newBlockNumberInput = it },
                            placeholder = { Text("Add a phone number") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newBlockNumberInput.isNotBlank()) {
                                    prefs.addBlockedNumber(newBlockNumberInput.trim())
                                    blockedNumbers = prefs.getBlockedNumbers().toList()
                                    newBlockNumberInput = ""
                                }
                            }
                        ) {
                            Text("Block")
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    if (blockedNumbers.isEmpty()) {
                        Text("No blocked numbers yet.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        blockedNumbers.forEach { num ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(num, fontSize = 14.sp)
                                IconButton(onClick = {
                                    prefs.removeBlockedNumber(num)
                                    blockedNumbers = prefs.getBlockedNumbers().toList()
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Unblock", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBlockedNumbersDialog = false }) { Text("Close") }
            }
        )
    }

    // 4. Call Recording Dialog
    if (showCallRecordingDialog) {
        AlertDialog(
            onDismissRequest = { showCallRecordingDialog = false },
            title = { Text("Call recording", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Auto-Recording Options:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    listOf(
                        "ALL" to "Always record all calls",
                        "UNKNOWN" to "Record unknown numbers only",
                        "OFF" to "Manual recording only"
                    ).forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    callRecordingMode = mode
                                    prefs.setCallRecordingMode(mode)
                                    prefs.setAutoCallRecordingEnabled(mode != "OFF")
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = callRecordingMode == mode,
                                onClick = {
                                    callRecordingMode = mode
                                    prefs.setCallRecordingMode(mode)
                                    prefs.setAutoCallRecordingEnabled(mode != "OFF")
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label, fontSize = 14.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Recordings are stored securely in device storage. Both speaker & mic audio channels are captured during active calls.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showCallRecordingDialog = false }) { Text("Done") }
            }
        )
    }

    // 5. Display Options Dialog
    if (showDisplayOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showDisplayOptionsDialog = false },
            title = { Text("Display options", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Theme Mode", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    listOf(
                        ThemeMode.SYSTEM to "System default",
                        ThemeMode.LIGHT to "Light",
                        ThemeMode.DARK to "Dark"
                    ).forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    prefs.setThemeMode(mode)
                                    onThemeChange(mode)
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = prefs.getThemeMode() == mode,
                                onClick = {
                                    prefs.setThemeMode(mode)
                                    onThemeChange(mode)
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDisplayOptionsDialog = false }) { Text("Done") }
            }
        )
    }

    // 6. Incoming Gesture Dialog
    if (showIncomingGestureDialog) {
        AlertDialog(
            onDismissRequest = { showIncomingGestureDialog = false },
            title = { Text("Incoming call gesture", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Flip to silence", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Place phone face down on a flat surface to silence ringing", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isFlipToSilence,
                            onCheckedChange = {
                                isFlipToSilence = it
                                prefs.setFlipToSilenceEnabled(it)
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIncomingGestureDialog = false }) { Text("Done") }
            }
        )
    }

    // 7. Sounds and Vibration Dialog
    if (showSoundsVibrationDialog) {
        AlertDialog(
            onDismissRequest = { showSoundsVibrationDialog = false },
            title = { Text("Sounds and vibration", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Dialpad touch tones", fontSize = 14.sp)
                        Switch(
                            checked = isDialpadSound,
                            onCheckedChange = {
                                isDialpadSound = it
                                prefs.setDialpadSoundEnabled(it)
                            }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Dialpad vibration feedback", fontSize = 14.sp)
                        Switch(
                            checked = isDialpadVibrate,
                            onCheckedChange = {
                                isDialpadVibrate = it
                                prefs.setVibrationEnabled(it)
                            }
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Sound settings unavailable", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open System Sound Settings")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSoundsVibrationDialog = false }) { Text("Done") }
            }
        )
    }

    // 8. Voicemail Dialog
    if (showVoicemailDialog) {
        AlertDialog(
            onDismissRequest = { showVoicemailDialog = false },
            title = { Text("Voicemail", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Carrier: $simName", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Voicemail number: Provided by your service provider", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Text("Notifications: Sound & vibration enabled for new voicemail alerts", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = { showVoicemailDialog = false }) { Text("OK") }
            }
        )
    }

    // 9. Calling Card Dialog
    if (showCallingCardDialog) {
        AlertDialog(
            onDismissRequest = { showCallingCardDialog = false },
            title = { Text("Calling card", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Calling card rules allow automatic prefixing for long-distance and international calls.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Text("No calling cards configured for current SIM.", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            },
            confirmButton = {
                TextButton(onClick = { showCallingCardDialog = false }) { Text("Done") }
            }
        )
    }

    // 10. Caller ID Announcement Dialog
    if (showCallerIdAnnounceDialog) {
        AlertDialog(
            onDismissRequest = { showCallerIdAnnounceDialog = false },
            title = { Text("Caller ID announcement", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Announce caller ID", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("The caller's name or number will be read out aloud for incoming calls", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isCallerIdAnnounce,
                            onCheckedChange = {
                                isCallerIdAnnounce = it
                                prefs.setCallerIdAnnouncementEnabled(it)
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCallerIdAnnounceDialog = false }) { Text("Done") }
            }
        )
    }

    // 11. Flip to Silence Dialog
    if (showFlipToSilenceDialog) {
        AlertDialog(
            onDismissRequest = { showFlipToSilenceDialog = false },
            title = { Text("Flip to silence", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "To silence an incoming call, place your phone face down on a flat surface.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Flip to silence", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Switch(
                            checked = isFlipToSilence,
                            onCheckedChange = {
                                isFlipToSilence = it
                                prefs.setFlipToSilenceEnabled(it)
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFlipToSilenceDialog = false }) { Text("Done") }
            }
        )
    }

    // 12. Quick Replies Dialog
    if (showQuickRepliesDialog) {
        AlertDialog(
            onDismissRequest = { showQuickRepliesDialog = false },
            title = { Text("Quick responses", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Add or delete automated response templates:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    quickReplies.forEachIndexed { index, reply ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(reply, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            IconButton(
                                onClick = {
                                    val updated = quickReplies.toMutableList().also { it.removeAt(index) }
                                    quickReplies = updated
                                    prefs.setQuickResponses(updated)
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newReplyText,
                            onValueChange = { newReplyText = it },
                            placeholder = { Text("New quick reply…", fontSize = 13.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newReplyText.isNotBlank()) {
                                    val updated = quickReplies.toMutableList().also { it.add(newReplyText.trim()) }
                                    quickReplies = updated
                                    prefs.setQuickResponses(updated)
                                    newReplyText = ""
                                }
                            }
                        ) {
                            Text("Add")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQuickRepliesDialog = false }) { Text("Done") }
            }
        )
    }
}

@Composable
fun SettingsCategoryHeader(title: String) {
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsItemRow(
    title: String,
    icon: ImageVector? = null,
    subtitle: String? = null,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}
