package com.amitbharat.phonedialer.ui.dialer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amitbharat.phonedialer.model.CallLogItem
import com.amitbharat.phonedialer.model.CallType
import com.amitbharat.phonedialer.model.Contact
import com.amitbharat.phonedialer.model.SpeedDialItem
import com.amitbharat.phonedialer.search.T9SearchEngine
import com.amitbharat.phonedialer.ui.theme.AccentGreen
import com.amitbharat.phonedialer.ui.theme.AccentRed
import com.amitbharat.phonedialer.utils.ContactAvatar
import com.amitbharat.phonedialer.utils.PreferencesManager
import java.text.SimpleDateFormat
import java.util.*

data class ConsolidatedCallLog(
    val primaryId: Long,
    val name: String?,
    val number: String,
    val latestTimestamp: Long,
    val totalCount: Int,
    val incomingCount: Int,
    val outgoingCount: Int,
    val missedCount: Int,
    val latestCallType: CallType,
    val totalDuration: Long,
    val hasRecording: Boolean,
    val calls: List<CallLogItem>
)

@Composable
fun DialerScreen(
    allContacts: List<Contact>,
    favorites: List<Contact>,
    callLogs: List<CallLogItem>,
    speedDials: List<SpeedDialItem>,
    onCallClick: (String, Int) -> Unit,
    onDeleteCallLog: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager.getInstance(context) }
    var enteredNumber by remember { mutableStateOf("") }
    var isDialpadOpen by remember { mutableStateOf(false) }
    var selectedFilterIndex by remember { mutableIntStateOf(0) }
    var selectedGroupForDetails by remember { mutableStateOf<ConsolidatedCallLog?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // 1. Group consecutive / same number calls into ConsolidatedCallLog
    val consolidatedLogs = remember(callLogs) {
        val groupedList = mutableListOf<ConsolidatedCallLog>()
        val map = LinkedHashMap<String, MutableList<CallLogItem>>()

        // Normalize key by phone number or name
        for (item in callLogs) {
            val key = item.name?.trim()?.ifBlank { null } ?: item.number.replace("[^0-9+]".toRegex(), "")
            map.getOrPut(key) { mutableListOf() }.add(item)
        }

        map.values.forEach { items ->
            val first = items.first()
            val total = items.size
            val incoming = items.count { it.callType == CallType.INCOMING }
            val outgoing = items.count { it.callType == CallType.OUTGOING }
            val missed = items.count { it.callType == CallType.MISSED || it.callType == CallType.REJECTED }
            val dur = items.sumOf { it.duration }
            val hasRec = items.any { it.recordingPath != null }

            groupedList.add(
                ConsolidatedCallLog(
                    primaryId = first.id,
                    name = first.name,
                    number = first.number,
                    latestTimestamp = first.timestamp,
                    totalCount = total,
                    incomingCount = incoming,
                    outgoingCount = outgoing,
                    missedCount = missed,
                    latestCallType = first.callType,
                    totalDuration = dur,
                    hasRecording = hasRec,
                    calls = items
                )
            )
        }
        groupedList
    }

    // Counts for sub-filters (Live & Accurate)
    val totalCallsCount = consolidatedLogs.size
    val missedCallsCount = remember(consolidatedLogs) { consolidatedLogs.count { it.missedCount > 0 } }
    val receivedCallsCount = remember(consolidatedLogs) { consolidatedLogs.count { it.incomingCount > 0 } }
    val dialedCallsCount = remember(consolidatedLogs) { consolidatedLogs.count { it.outgoingCount > 0 } }
    val recordedCallsCount = remember(consolidatedLogs) { consolidatedLogs.count { it.hasRecording } }

    val filteredList = remember(selectedFilterIndex, consolidatedLogs, searchQuery) {
        val base = when (selectedFilterIndex) {
            1 -> consolidatedLogs.filter { it.missedCount > 0 }
            2 -> consolidatedLogs.filter { it.incomingCount > 0 }
            3 -> consolidatedLogs.filter { it.outgoingCount > 0 }
            4 -> consolidatedLogs.filter { it.hasRecording }
            else -> consolidatedLogs
        }
        if (searchQuery.isBlank()) base
        else base.filter {
            (it.name?.contains(searchQuery, ignoreCase = true) == true) || it.number.contains(searchQuery)
        }
    }

    val matchedContacts = remember(enteredNumber, allContacts) {
        if (enteredNumber.isEmpty()) emptyList()
        else T9SearchEngine.search(enteredNumber, allContacts).take(6)
    }

    fun handleKeyPress(char: String) {
        if (prefs.isVibrationEnabled()) {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
        }
        enteredNumber += char
    }

    fun handleBackspace() {
        if (enteredNumber.isNotEmpty()) {
            enteredNumber = enteredNumber.dropLast(1)
        }
    }

    fun handleLongPressDigit(digit: Int) {
        val item = speedDials.find { it.digit == digit }
        if (item != null && item.number.isNotBlank()) {
            onCallClick(item.number, 0)
        } else if (digit == 0) {
            handleKeyPress("+")
        }
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // 1. Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search name, number or calls") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                shape = RoundedCornerShape(20.dp),
                singleLine = true
            )

            // 2. Horizontal Favorites Bar (Deduplicated)
            if (favorites.isNotEmpty() && searchQuery.isEmpty()) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp)) {
                    Text(
                        text = "? FAVORITES ()",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(favorites, key = { it.id }) { fav ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        val num = fav.numbers.firstOrNull() ?: ""
                                        if (num.isNotBlank()) onCallClick(num, 0)
                                    }
                                    .padding(4.dp)
                            ) {
                                ContactAvatar(name = fav.name, photoUri = fav.photoUri, size = 54.dp, fontSize = 20.sp)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = fav.name.split(" ").firstOrNull() ?: fav.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.widthIn(max = 68.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }

            // 3. Sub-Filter Badges Row with Real Counts
            ScrollableTabRow(
                selectedTabIndex = selectedFilterIndex,
                edgePadding = 12.dp,
                containerColor = MaterialTheme.colorScheme.background,
                divider = {}
            ) {
                Tab(
                    selected = selectedFilterIndex == 0,
                    onClick = { selectedFilterIndex = 0 },
                    text = { Text("All ()", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
                Tab(
                    selected = selectedFilterIndex == 1,
                    onClick = { selectedFilterIndex = 1 },
                    text = { Text("Missed ()", color = AccentRed, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
                Tab(
                    selected = selectedFilterIndex == 2,
                    onClick = { selectedFilterIndex = 2 },
                    text = { Text("Received ()", color = AccentGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
                Tab(
                    selected = selectedFilterIndex == 3,
                    onClick = { selectedFilterIndex = 3 },
                    text = { Text("Dialed ()", color = Color(0xFF3B82F6), fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
                Tab(
                    selected = selectedFilterIndex == 4,
                    onClick = { selectedFilterIndex = 4 },
                    text = { Text("Recorded ()", color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
            }

            // 4. Consolidated Call History List
            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Call, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(54.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("No call history in this category", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    items(filteredList, key = { it.primaryId.toString() + it.number }) { group ->
                        val (icon, tint) = when (group.latestCallType) {
                            CallType.INCOMING -> Pair(Icons.AutoMirrored.Filled.CallReceived, AccentGreen)
                            CallType.OUTGOING -> Pair(Icons.AutoMirrored.Filled.CallMade, Color(0xFF3B82F6))
                            CallType.MISSED -> Pair(Icons.AutoMirrored.Filled.CallMissed, AccentRed)
                            CallType.REJECTED -> Pair(Icons.Default.CallEnd, AccentRed)
                            CallType.BLOCKED -> Pair(Icons.Default.Block, Color.Gray)
                        }

                        val timeFormatted = remember(group.latestTimestamp) {
                            val diff = System.currentTimeMillis() - group.latestTimestamp
                            val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                            if (diff < 24 * 60 * 60 * 1000) {
                                "Today, " + SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(group.latestTimestamp))
                            } else if (diff < 48 * 60 * 60 * 1000) {
                                "Yesterday, " + SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(group.latestTimestamp))
                            } else {
                                sdf.format(Date(group.latestTimestamp))
                            }
                        }

                        // Summary breakdown: e.g. "4 Dialed  3 Received  1 Missed"
                        val breakdownText = buildString {
                            val parts = mutableListOf<String>()
                            if (group.outgoingCount > 0) parts.add(" Dialed")
                            if (group.incomingCount > 0) parts.add(" Received")
                            if (group.missedCount > 0) parts.add(" Missed")
                            append(parts.joinToString("  "))
                            append("  ")
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable { selectedGroupForDetails = group },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ContactAvatar(
                                    name = group.name ?: group.number,
                                    photoUri = allContacts.find { it.name == group.name }?.photoUri,
                                    size = 48.dp,
                                    fontSize = 18.sp
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = group.name ?: group.number,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = if (group.missedCount > 0 && group.latestCallType == CallType.MISSED) AccentRed else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (group.totalCount > 1) {
                                            Spacer(Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant
                                            ) {
                                                Text(
                                                    text = "()",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = breakdownText,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (group.hasRecording) {
                                            Spacer(Modifier.width(4.dp))
                                            Text("???", fontSize = 12.sp)
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = { onCallClick(group.number, 0) },
                                    modifier = Modifier
                                        .size(42.dp)
                                        .background(AccentGreen.copy(alpha = 0.15f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Call, contentDescription = "Call", tint = AccentGreen, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Floating Open Keypad Button (FAB)
        if (!isDialpadOpen) {
            FloatingActionButton(
                onClick = { isDialpadOpen = true },
                containerColor = AccentGreen,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .size(64.dp)
                    .shadow(12.dp, CircleShape)
            ) {
                Icon(Icons.Default.Dialpad, contentDescription = "Open Dialpad", modifier = Modifier.size(30.dp))
            }
        }

        // 6. Slide-Up Ultra-Modern Glass Keypad Overlay
        AnimatedVisibility(
            visible = isDialpadOpen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Card(
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Bar with Drag Handle & Close
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DIALPAD",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { isDialpadOpen = false }) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close Keypad", modifier = Modifier.size(30.dp))
                        }
                    }

                    // T9 Search Match Chips
                    if (matchedContacts.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(matchedContacts) { contact ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                    modifier = Modifier.clickable {
                                        onCallClick(contact.numbers.firstOrNull() ?: enteredNumber, 0)
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ContactAvatar(name = contact.name, photoUri = contact.photoUri, size = 30.dp, fontSize = 13.sp)
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(contact.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                            Text(contact.numbers.firstOrNull() ?: "", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Entered Number Display with Backspace
                    Row(
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = enteredNumber,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                        if (enteredNumber.isNotEmpty()) {
                            IconButton(onClick = { handleBackspace() }) {
                                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // 3x4 Keys Grid
                    val keys = listOf(
                        Triple("1", "", 1),
                        Triple("2", "ABC", 2),
                        Triple("3", "DEF", 3),
                        Triple("4", "GHI", 4),
                        Triple("5", "JKL", 5),
                        Triple("6", "MNO", 6),
                        Triple("7", "PQRS", 7),
                        Triple("8", "TUV", 8),
                        Triple("9", "WXYZ", 9),
                        Triple("*", "", 0),
                        Triple("0", "+", 0),
                        Triple("#", "", 0)
                    )

                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        keys.chunked(3).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                row.forEach { (digit, sub, speedIndex) ->
                                    DialKeyModern(
                                        digit = digit,
                                        sub = sub,
                                        onClick = { handleKeyPress(digit) },
                                        onLongClick = { if (speedIndex > 0 || digit == "0") handleLongPressDigit(speedIndex) }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Green Pill Call Button
                    Button(
                        onClick = {
                            if (enteredNumber.isNotBlank()) {
                                onCallClick(enteredNumber, 0)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(56.dp)
                            .shadow(8.dp, RoundedCornerShape(28.dp))
                    ) {
                        Icon(Icons.Default.Call, contentDescription = "Call", tint = Color.White, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("CALL", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }

    // Consolidated Call Details Dialog (Shows all historical calls for this contact)
    selectedGroupForDetails?.let { group ->
        val fullDate = SimpleDateFormat("EEEE, dd MMMM yyyy, hh:mm:ss a", Locale.getDefault()).format(Date(group.latestTimestamp))
        val totalMins = group.totalDuration / 60
        val totalSecs = group.totalDuration % 60
        val totalDurText = if (totalMins > 0) "m s" else "s"

        AlertDialog(
            onDismissRequest = { selectedGroupForDetails = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ContactAvatar(name = group.name ?: group.number, size = 50.dp, fontSize = 20.sp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(group.name ?: group.number, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(group.number, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            text = {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text("Total Calls: ", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Breakdown:  Dialed   Received   Missed", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("Latest Call: ", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("Total Duration: ", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (group.hasRecording) {
                        Spacer(Modifier.height(4.dp))
                        Text("Recording: Available ???", fontSize = 13.sp, color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedGroupForDetails = null
                        onCallClick(group.number, 0)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Call")
                }
            },
            dismissButton = {
                Row {
                    OutlinedButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Phone Number", group.number))
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Copy")
                    }
                    Spacer(Modifier.width(6.dp))
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("sms:")))
                        selectedGroupForDetails = null
                    }) {
                        Text("SMS")
                    }
                }
            }
        )
    }
}
@Composable
fun DialKeyModern(
    digit: String,
    sub: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 100.dp, height = 62.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = digit,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (sub.isNotEmpty()) {
                Text(
                    text = sub,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
