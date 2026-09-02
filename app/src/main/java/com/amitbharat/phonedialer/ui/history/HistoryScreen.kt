package com.amitbharat.phonedialer.ui.history

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amitbharat.phonedialer.model.CallLogItem
import com.amitbharat.phonedialer.model.CallType
import com.amitbharat.phonedialer.ui.theme.AccentGreen
import com.amitbharat.phonedialer.ui.theme.AccentRed
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(
    callLogs: List<CallLogItem>,
    onCallClick: (String) -> Unit,
    onDeleteClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("All", "Missed", "Recorded")

    val filteredLogs = remember(selectedTab, callLogs) {
        when (selectedTab) {
            1 -> callLogs.filter { it.callType == CallType.MISSED }
            2 -> callLogs.filter { it.recordingPath != null }
            else -> callLogs
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Filter Tabs Row
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
                )
            }
        }

        if (filteredLogs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No call history yet", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
                items(filteredLogs, key = { it.id }) { item ->
                    CallLogItemRow(
                        item = item,
                        onCallClick = { onCallClick(item.number) },
                        onDeleteClick = { onDeleteClick(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun CallLogItemRow(
    item: CallLogItem,
    onCallClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val context = LocalContext.current
    var showDetailsDialog by remember { mutableStateOf(false) }

    val formattedDate = remember(item.timestamp) {
        SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(item.timestamp))
    }

    val (icon, tint) = when (item.callType) {
        CallType.INCOMING -> Icons.Default.CallReceived to Color(0xFF3B82F6)
        CallType.OUTGOING -> Icons.Default.CallMade to AccentGreen
        CallType.MISSED -> Icons.Default.CallMissed to AccentRed
        CallType.REJECTED -> Icons.Default.CallEnd to AccentRed
        CallType.BLOCKED -> Icons.Default.Block to Color.Gray
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { showDetailsDialog = true },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name ?: item.number,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = if (item.callType == CallType.MISSED) AccentRed else MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = formattedDate, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (item.duration > 0) {
                        Text(
                            text = " • s",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (item.recordingPath != null) {
                        Spacer(Modifier.width(4.dp))
                        Text(text = "???", fontSize = 12.sp)
                    }
                }
            }
            IconButton(onClick = onCallClick) {
                Icon(Icons.Default.Call, contentDescription = "Call", tint = AccentGreen)
            }
        }
    }

    if (showDetailsDialog) {
        AlertDialog(
            onDismissRequest = { showDetailsDialog = false },
            title = { Text(item.name ?: item.number, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Number: ", fontSize = 15.sp)
                    Text("Time: ", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (item.duration > 0) {
                        Text("Duration:  seconds", fontSize = 14.sp)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showDetailsDialog = false
                    onCallClick()
                }, colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) {
                    Text("Call")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showDetailsDialog = false
                    onDeleteClick()
                }) {
                    Text("Delete")
                }
            }
        )
    }
}
