package com.amitbharat.phonedialer.ui.settings

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telecom.TelecomManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amitbharat.phonedialer.R
import com.amitbharat.phonedialer.ui.theme.AccentGreen
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

    // Call Settings State
    var isAutoRecordAll by remember { mutableStateOf(prefs.isAutoCallRecordingEnabled()) }
    var isVibration by remember { mutableStateOf(prefs.isVibrationEnabled()) }
    var isSound by remember { mutableStateOf(prefs.isDialpadSoundEnabled()) }
    var blockUnknown by remember { mutableStateOf(prefs.isBlockUnknownCallsEnabled()) }
    var blockSpam by remember { mutableStateOf(prefs.isBlockSpamCallsEnabled()) }
    var selectedSimOption by remember { mutableIntStateOf(prefs.getDefaultSim()) }
    var autoFormatNumbers by remember { mutableStateOf(prefs.isAutoFormatNumbersEnabled()) }

    // Message Settings State
    var smsDeliveryReports by remember { mutableStateOf(prefs.isSmsDeliveryReportsEnabled()) }
    var smsNotifications by remember { mutableStateOf(prefs.isSmsNotificationsEnabled()) }
    var smsSound by remember { mutableStateOf(prefs.isSmsSoundEnabled()) }
    var smsVibration by remember { mutableStateOf(prefs.isSmsVibrationEnabled()) }
    var autoRetrieveMms by remember { mutableStateOf(prefs.isAutoRetrieveMmsEnabled()) }
    var groupMessaging by remember { mutableStateOf(prefs.isGroupMessagingEnabled()) }
    var autoDeleteOldMessages by remember { mutableStateOf(prefs.isAutoDeleteOldMessagesEnabled()) }

    var showQuickRepliesDialog by remember { mutableStateOf(false) }
    var quickReplies by remember { mutableStateOf(prefs.getQuickResponses()) }
    var newReplyText by remember { mutableStateOf("") }

    // System roles verification
    val telecomManager = remember { context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager }
    val isDefaultDialer = remember(selectedSection) {
        telecomManager?.defaultDialerPackage == context.packageName
    }
    val isDefaultSms = remember(selectedSection) {
        Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
    }

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

    fun requestDefaultSms() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Could not open default SMS prompt", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Could not open default SMS prompt", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Tab Navigation to cleanly separate Call and Message settings
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (selectedSection == SettingsSection.CALL) {
                // ==========================================
                // CALL SETTINGS SECTION
                // ==========================================

                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 0.dp)) {
                        SettingsCategoryHeader("Call Assist")
                        SettingsItemRow("Caller ID and spam", Icons.Default.Security)
                        
                        SettingsCategoryHeader("General")
                        SettingsItemRow("Accessibility", Icons.Default.Accessibility)
                        SettingsItemRow("Assisted dialling", Icons.Default.Public)
                        SettingsItemRow("Blocked numbers", Icons.Default.Block)
                        SettingsItemRow("Calling accounts", Icons.Default.SimCard)
                        SettingsItemRow("Call recording", Icons.Default.Mic)
                        SettingsItemRow("Display options", Icons.Default.Palette)
                        SettingsItemRow("Incoming call gesture", Icons.Default.TouchApp)
                        SettingsItemRow("Quick responses", Icons.Default.Quickreply)
                        SettingsItemRow("Sounds and vibration", Icons.AutoMirrored.Filled.VolumeUp)
                        SettingsItemRow("Voicemail", Icons.Default.Voicemail)
                        SettingsItemRow("Contact ringtones", Icons.Default.MusicNote)
                        SettingsItemRow("Calling card", Icons.Default.CreditCard)
                        
                        SettingsCategoryHeader("Advanced")
                        SettingsItemRow("Caller ID announcement", Icons.Default.RecordVoiceOver)
                        SettingsItemRow("Flip to silence", Icons.Default.ScreenRotation)
                    }
                }

            } else {
                // ==========================================
                // MESSAGE SETTINGS SECTION
                // ==========================================

                // 1. Default SMS App Card
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDefaultSms) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (isDefaultSms) Icons.Default.CheckCircle else Icons.AutoMirrored.Filled.Message,
                                    contentDescription = null,
                                    tint = if (isDefaultSms) AccentGreen else MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (isDefaultSms) "Default SMS App (Active)" else "Set as Default SMS App",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = if (isDefaultSms) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = if (isDefaultSms) "Phone Dialer is currently your default application for sending and receiving SMS messages."
                                else "Make Phone Dialer your default messaging app to enable seamless SMS send/receive, delivery notifications, and full chat integration.",
                                fontSize = 13.sp,
                                color = if (isDefaultSms) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                            if (!isDefaultSms) {
                                Spacer(Modifier.height(12.dp))
                                Button(onClick = { requestDefaultSms() }) {
                                    Text("Set as Default SMS App")
                                }
                            }
                        }
                    }
                }

                // 2. SMS Delivery Reports
                item {
                    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DoneAll, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(10.dp))
                                Text("Delivery & Status Reports", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Get SMS delivery reports", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text("Find out when an SMS message is delivered", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = smsDeliveryReports,
                                    onCheckedChange = {
                                        smsDeliveryReports = it
                                        prefs.setSmsDeliveryReportsEnabled(it)
                                    }
                                )
                            }
                        }
                    }
                }

                // 3. Message Notifications & Haptics
                item {
                    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(10.dp))
                                Text("Message Notifications", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Notification Alerts", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text("Show push notification banners for new incoming SMS", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = smsNotifications,
                                    onCheckedChange = {
                                        smsNotifications = it
                                        prefs.setSmsNotificationsEnabled(it)
                                    }
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Message Alert Sound", fontSize = 14.sp)
                                Switch(
                                    checked = smsSound,
                                    onCheckedChange = {
                                        smsSound = it
                                        prefs.setSmsSoundEnabled(it)
                                    }
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Vibrate on Incoming Message", fontSize = 14.sp)
                                Switch(
                                    checked = smsVibration,
                                    onCheckedChange = {
                                        smsVibration = it
                                        prefs.setSmsVibrationEnabled(it)
                                    }
                                )
                            }
                        }
                    }
                }

                // 4. Multimedia (MMS) Settings
                item {
                    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(10.dp))
                                Text("Multimedia Messages (MMS)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Auto-Retrieve MMS", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text("Automatically download media attachments when connected", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = autoRetrieveMms,
                                    onCheckedChange = {
                                        autoRetrieveMms = it
                                        prefs.setAutoRetrieveMmsEnabled(it)
                                    }
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Group Messaging", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text("Send single MMS group message instead of individual SMS", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = groupMessaging,
                                    onCheckedChange = {
                                        groupMessaging = it
                                        prefs.setGroupMessagingEnabled(it)
                                    }
                                )
                            }
                        }
                    }
                }

                // 5. Quick SMS Responses
                item {
                    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Quickreply, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(10.dp))
                                    Text("Quick SMS Responses", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                                TextButton(onClick = { showQuickRepliesDialog = true }) {
                                    Text("Manage")
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Template messages used to decline incoming calls or reply quickly from the chat screen.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            quickReplies.take(3).forEach { reply ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "\"$reply\"",
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // 6. Message Storage & Cleanup
                item {
                    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(10.dp))
                                Text("Storage & Message Retention", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Auto-Delete Old Messages", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text("Delete old texts when conversation reaches 1,000 messages", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

    // Dialog to add or manage Quick Responses
    if (showQuickRepliesDialog) {
        AlertDialog(
            onDismissRequest = { showQuickRepliesDialog = false },
            title = { Text("Quick Responses", fontWeight = FontWeight.Bold) },
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
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newReplyText,
                            onValueChange = { newReplyText = it },
                            placeholder = { Text("Add new response…", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                if (newReplyText.isNotBlank()) {
                                    val updated = quickReplies + newReplyText.trim()
                                    quickReplies = updated
                                    prefs.setQuickResponses(updated)
                                    newReplyText = ""
                                }
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQuickRepliesDialog = false }) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
fun SettingsCategoryHeader(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsItemRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    subtitle: String? = null,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
