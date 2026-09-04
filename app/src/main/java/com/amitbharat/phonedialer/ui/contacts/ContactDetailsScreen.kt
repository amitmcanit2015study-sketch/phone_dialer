package com.amitbharat.phonedialer.ui.contacts

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amitbharat.phonedialer.model.CallLogItem
import com.amitbharat.phonedialer.model.CallType
import com.amitbharat.phonedialer.model.Contact
import com.amitbharat.phonedialer.ui.theme.AccentGreen
import com.amitbharat.phonedialer.ui.theme.AccentRed
import com.amitbharat.phonedialer.utils.ContactAvatar
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailsScreen(
    name: String,
    number: String,
    photoUri: String? = null,
    contact: Contact? = null,
    callLogs: List<CallLogItem>,
    onBack: () -> Unit,
    onCallClick: (String) -> Unit,
    onMessageClick: (String) -> Unit,
    onToggleFavorite: ((Contact) -> Unit)? = null,
    onEditContact: ((Contact) -> Unit)? = null
) {
    val context = LocalContext.current

    val filteredLogs = remember(number, callLogs) {
        val cleanNum = number.replace("[^0-9+]".toRegex(), "")
        callLogs.filter {
            val c = it.number.replace("[^0-9+]".toRegex(), "")
            c == cleanNum || (cleanNum.length >= 10 && c.endsWith(cleanNum.takeLast(10))) ||
            (it.name != null && it.name.equals(name, ignoreCase = true))
        }
        .distinctBy { item ->
            val c = item.number.replace("[^0-9+]".toRegex(), "")
            "${c}_${item.timestamp}_${item.callType}_${item.duration}"
        }
        .sortedByDescending { it.timestamp }
    }

    val totalDuration = remember(filteredLogs) { filteredLogs.sumOf { it.duration } }
    val totalMins = totalDuration / 60
    val totalSecs = totalDuration % 60
    val durationText = if (totalMins > 0) "${totalMins}m ${totalSecs}s" else "${totalSecs}s"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contact Details", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (contact != null && onToggleFavorite != null) {
                        IconButton(onClick = { onToggleFavorite(contact) }) {
                            Icon(
                                if (contact.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Favorite",
                                tint = if (contact.isFavorite) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    if (contact != null && onEditContact != null) {
                        IconButton(onClick = { onEditContact(contact) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Contact")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            // Header Hero Section
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ContactAvatar(name = name, photoUri = photoUri, size = 80.dp, fontSize = 30.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = number,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(18.dp))

                        // Quick Action Buttons Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            // Call Action
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = { onCallClick(number) },
                                    modifier = Modifier
                                        .size(50.dp)
                                        .background(AccentGreen, CircleShape)
                                ) {
                                    Icon(Icons.Default.Call, contentDescription = "Call", tint = Color.White, modifier = Modifier.size(24.dp))
                                }
                                Spacer(Modifier.height(4.dp))
                                Text("Call", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            // SMS Action
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = { onMessageClick(number) },
                                    modifier = Modifier
                                        .size(50.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                ) {
                                    Icon(Icons.Default.Message, contentDescription = "SMS", tint = Color.White, modifier = Modifier.size(24.dp))
                                }
                                Spacer(Modifier.height(4.dp))
                                Text("Message", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            // WhatsApp Action
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = {
                                        val clean = number.replace("[^0-9+]".toRegex(), "")
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$clean"))
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier
                                        .size(50.dp)
                                        .background(Color(0xFF25D366), CircleShape)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "WhatsApp", tint = Color.White, modifier = Modifier.size(24.dp))
                                }
                                Spacer(Modifier.height(4.dp))
                                Text("WhatsApp", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            // Copy Action
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = {
                                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cb.setPrimaryClip(ClipData.newPlainText("Phone Number", number))
                                        Toast.makeText(context, "Number copied", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .size(50.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                                }
                                Spacer(Modifier.height(4.dp))
                                Text("Copy", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // Summary Stats Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Total Calls", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${filteredLogs.size}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("Total Duration", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(durationText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("Missed Calls", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${filteredLogs.count { it.callType == CallType.MISSED || it.callType == CallType.REJECTED }}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AccentRed)
                        }
                    }
                }
            }

            // Timeline Header
            item {
                Text(
                    text = "CALL HISTORY TIMELINE (${filteredLogs.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp, start = 4.dp)
                )
            }

            if (filteredLogs.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No call history recorded for this contact", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                }
            } else {
                items(filteredLogs, key = { it.id }) { log ->
                    val (icon, tint, label) = when (log.callType) {
                        CallType.INCOMING -> Triple(Icons.AutoMirrored.Filled.CallReceived, AccentGreen, "Incoming Call")
                        CallType.OUTGOING -> Triple(Icons.AutoMirrored.Filled.CallMade, Color(0xFF3B82F6), "Outgoing Call")
                        CallType.MISSED -> Triple(Icons.AutoMirrored.Filled.CallMissed, AccentRed, "Missed Call")
                        CallType.REJECTED -> Triple(Icons.Default.CallEnd, AccentRed, "Rejected Call")
                        CallType.BLOCKED -> Triple(Icons.Default.Block, Color.Gray, "Blocked Call")
                    }

                    val formattedDateTime = remember(log.timestamp) {
                        SimpleDateFormat("EEEE, dd MMM yyyy • hh:mm:ss a", Locale.getDefault()).format(Date(log.timestamp))
                    }

                    val logDurationText = remember(log.duration) {
                        if (log.duration <= 0) "No answer"
                        else {
                            val m = log.duration / 60
                            val s = log.duration % 60
                            if (m > 0) "${m}m ${s}s" else "${s}s"
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(tint.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(label, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.height(2.dp))
                                Text(formattedDateTime, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                logDurationText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (log.duration > 0) MaterialTheme.colorScheme.onSurface else AccentRed
                            )
                        }
                    }
                }
            }
        }
    }
}
