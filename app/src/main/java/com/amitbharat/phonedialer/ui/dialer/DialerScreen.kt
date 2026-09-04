package com.amitbharat.phonedialer.ui.dialer

import android.content.Context
import android.media.MediaPlayer
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class DayConsolidatedLog(
    val dayKey: String,
    val primaryId: Long,
    val name: String?,
    val number: String,
    val latestTimestamp: Long,
    val dayTotalCount: Int,
    val dayIncomingCount: Int,
    val dayOutgoingCount: Int,
    val dayMissedCount: Int,
    val latestCallType: CallType,
    val dayTotalDuration: Long,
    val hasRecording: Boolean,
    val recordingPath: String?,
    val isSavedContact: Boolean,
    val calls: List<CallLogItem>
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialerScreen(
    allContacts: List<Contact>,
    favorites: List<Contact>,
    callLogs: List<CallLogItem>,
    speedDials: List<SpeedDialItem>,
    onCallClick: (String, Int) -> Unit,
    onDeleteCallLog: (Long) -> Unit,
    onContactClick: (name: String, number: String, photoUri: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()

    var enteredNumber by remember { mutableStateOf("") }
    var isDialpadOpen by remember { mutableStateOf(false) }
    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var playingAudioPath by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = isDialpadOpen || isSearchOpen || enteredNumber.isNotEmpty()) {
        when {
            isDialpadOpen -> isDialpadOpen = false
            isSearchOpen -> {
                isSearchOpen = false
                searchQuery = ""
            }
            enteredNumber.isNotEmpty() -> enteredNumber = ""
        }
    }

    // Fast saved number lookup map
    val savedNumberMap = remember(allContacts) {
        val map = HashMap<String, Contact>()
        allContacts.forEach { c ->
            c.numbers.forEach { num ->
                val clean = num.replace(Regex("[^0-9+]"), "")
                if (clean.isNotBlank()) {
                    map[clean] = c
                    if (clean.length >= 10) map[clean.takeLast(10)] = c
                }
            }
        }
        map
    }

    // 1. Group call logs strictly BY DAY first, then BY CONTACT for date-specific counts
    val dayGroupedLogs = remember(callLogs, savedNumberMap) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val todayMs = cal.timeInMillis
        val yesterdayMs = todayMs - 24 * 60 * 60 * 1000L

        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val dayBuckets = LinkedHashMap<String, MutableList<CallLogItem>>()

        for (item in callLogs) {
            val ts = item.timestamp
            val dayKey = when {
                ts >= todayMs -> "Today"
                ts >= yesterdayMs -> "Yesterday"
                else -> dateFormat.format(Date(ts))
            }
            dayBuckets.getOrPut(dayKey) { mutableListOf() }.add(item)
        }

        val result = LinkedHashMap<String, List<DayConsolidatedLog>>()

        dayBuckets.forEach { (dayKey, itemsInDay) ->
            val contactMap = LinkedHashMap<String, MutableList<CallLogItem>>()
            for (item in itemsInDay) {
                val cleanNum = item.number.replace(Regex("[^0-9+]"), "")
                val key = item.name?.trim()?.ifBlank { null } ?: if (cleanNum.length >= 10) cleanNum.takeLast(10) else cleanNum
                contactMap.getOrPut(key) { mutableListOf() }.add(item)
            }

            val consolidatedList = mutableListOf<DayConsolidatedLog>()
            contactMap.values.forEach { items ->
                val first = items.first()
                val dayTotal = items.size
                val incoming = items.count { it.callType == CallType.INCOMING }
                val outgoing = items.count { it.callType == CallType.OUTGOING }
                val missed = items.count { it.callType == CallType.MISSED || it.callType == CallType.REJECTED }
                val dur = items.sumOf { it.duration }
                val recPath = items.firstOrNull { !it.recordingPath.isNullOrBlank() }?.recordingPath

                val cleanNum = first.number.replace(Regex("[^0-9+]"), "")
                val matchedContact = savedNumberMap[cleanNum] ?: if (cleanNum.length >= 10) savedNumberMap[cleanNum.takeLast(10)] else null
                val isSaved = matchedContact != null || first.isSavedContact

                consolidatedList.add(
                    DayConsolidatedLog(
                        dayKey = dayKey,
                        primaryId = first.id,
                        name = matchedContact?.name ?: first.name,
                        number = first.number,
                        latestTimestamp = first.timestamp,
                        dayTotalCount = dayTotal,
                        dayIncomingCount = incoming,
                        dayOutgoingCount = outgoing,
                        dayMissedCount = missed,
                        latestCallType = first.callType,
                        dayTotalDuration = dur,
                        hasRecording = recPath != null,
                        recordingPath = recPath,
                        isSavedContact = isSaved,
                        calls = items
                    )
                )
            }
            result[dayKey] = consolidatedList
        }
        result
    }

    // Tab counts
    val allLogsList = remember(dayGroupedLogs) { dayGroupedLogs.values.flatten() }
    val totalCallsCount = allLogsList.size
    val missedCallsCount = remember(allLogsList) { allLogsList.sumOf { it.dayMissedCount } }
    val receivedCallsCount = remember(allLogsList) { allLogsList.sumOf { it.dayIncomingCount } }
    val dialedCallsCount = remember(allLogsList) { allLogsList.sumOf { it.dayOutgoingCount } }
    val recordedCallsCount = remember(allLogsList) { allLogsList.count { it.hasRecording } }

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 5 })

    // T9 search matches
    val matchedContacts = remember(enteredNumber, allContacts) {
        if (enteredNumber.isEmpty()) emptyList()
        else T9SearchEngine.search(enteredNumber, allContacts)
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

    fun playAudio(path: String) {
        try {
            val file = File(path)
            if (file.exists()) {
                playingAudioPath = path
                val mediaPlayer = MediaPlayer()
                mediaPlayer.setDataSource(path)
                mediaPlayer.prepare()
                mediaPlayer.start()
                mediaPlayer.setOnCompletionListener {
                    playingAudioPath = null
                    mediaPlayer.release()
                }
                Toast.makeText(context, "Playing recorded call audio…", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Recording file not found on disk", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Could not play recording: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Horizontal Favorites Bar
            if (favorites.isNotEmpty() && searchQuery.isEmpty() && enteredNumber.isEmpty()) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp)) {
                    Text(
                        text = "FAVORITES (${favorites.size})",
                        fontSize = 12.sp,
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
                                ContactAvatar(name = fav.name, photoUri = fav.photoUri, size = 50.dp, fontSize = 19.sp)
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

            // Sub-Filter Tabs Row (All, Missed, Received, Dialed, Recorded)
            if (enteredNumber.isEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    edgePadding = 12.dp,
                    containerColor = MaterialTheme.colorScheme.background,
                    divider = {}
                ) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                        text = { Text("All", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                        text = { Text("Missed", color = AccentRed, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                    )
                    Tab(
                        selected = pagerState.currentPage == 2,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                        text = { Text("Received", color = AccentGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                    )
                    Tab(
                        selected = pagerState.currentPage == 3,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(3) } },
                        text = { Text("Dialed", color = Color(0xFF3B82F6), fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                    )
                    Tab(
                        selected = pagerState.currentPage == 4,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(4) } },
                        text = { Text("Recorded", color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                    )
                }
            }

            // Main Body: Swipeable Pager for Sub-Filters or T9 Search Results
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (enteredNumber.isNotEmpty()) {
                    if (matchedContacts.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No contacts match '$enteredNumber'", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            item {
                                Text(
                                    text = "SEARCH RESULTS (${matchedContacts.size})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(matchedContacts, key = { it.id.toString() + "_" + it.name }) { contact ->
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onContactClick(contact.name, contact.numbers.firstOrNull() ?: enteredNumber, contact.photoUri)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ContactAvatar(name = contact.name, photoUri = contact.photoUri, size = 44.dp, fontSize = 16.sp)
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(contact.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                                            Text(contact.numbers.firstOrNull() ?: "", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        IconButton(
                                            onClick = { onCallClick(contact.numbers.firstOrNull() ?: enteredNumber, 0) },
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(AccentGreen.copy(alpha = 0.15f), CircleShape)
                                        ) {
                                            Icon(Icons.Default.Call, contentDescription = "Call", tint = AccentGreen, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { pageIndex ->
                        val pageFilteredLogs = remember(dayGroupedLogs, pageIndex, searchQuery) {
                            val map = LinkedHashMap<String, List<DayConsolidatedLog>>()
                            dayGroupedLogs.forEach { (dayKey, itemsInDay) ->
                                val filtered = itemsInDay.filter { item ->
                                    val matchesSearch = searchQuery.isBlank() ||
                                            (item.name?.contains(searchQuery, ignoreCase = true) == true) ||
                                            item.number.contains(searchQuery)
                                    val matchesCategory = when (pageIndex) {
                                        1 -> item.dayMissedCount > 0
                                        2 -> item.dayIncomingCount > 0
                                        3 -> item.dayOutgoingCount > 0
                                        4 -> item.hasRecording
                                        else -> true
                                    }
                                    matchesSearch && matchesCategory
                                }
                                if (filtered.isNotEmpty()) {
                                    map[dayKey] = filtered
                                }
                            }
                            map
                        }

                        if (pageFilteredLogs.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Call, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(54.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text("No call history in this category", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 2.dp),
                                contentPadding = PaddingValues(bottom = if (isSearchOpen) 150.dp else 100.dp)
                            ) {
                                pageFilteredLogs.forEach { (dayHeader, logsInDay) ->
                                    // Section Header Count specific to the selected tab!
                                    val headerCount = when (pageIndex) {
                                        1 -> logsInDay.sumOf { it.dayMissedCount }
                                        2 -> logsInDay.sumOf { it.dayIncomingCount }
                                        3 -> logsInDay.sumOf { it.dayOutgoingCount }
                                        4 -> logsInDay.count { it.hasRecording }
                                        else -> logsInDay.sumOf { it.dayTotalCount }
                                    }

                                    item(key = "header_${pageIndex}_$dayHeader") {
                                        Surface(
                                            color = MaterialTheme.colorScheme.background,
                                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp, start = 4.dp)
                                        ) {
                                            Text(
                                                text = "$dayHeader ($headerCount)",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    items(logsInDay, key = { it.primaryId.toString() + "_" + it.number + "_" + it.dayKey }) { group ->
                                        val (icon, tint) = when (group.latestCallType) {
                                            CallType.INCOMING -> Pair(Icons.AutoMirrored.Filled.CallReceived, AccentGreen)
                                            CallType.OUTGOING -> Pair(Icons.AutoMirrored.Filled.CallMade, Color(0xFF3B82F6))
                                            CallType.MISSED -> Pair(Icons.AutoMirrored.Filled.CallMissed, AccentRed)
                                            CallType.REJECTED -> Pair(Icons.Default.CallEnd, AccentRed)
                                            CallType.BLOCKED -> Pair(Icons.Default.Block, Color.Gray)
                                        }

                                        // Breakdown text specific to tab mode!
                                        val breakdownText = when (pageIndex) {
                                            1 -> "${group.dayMissedCount} Missed"
                                            2 -> "${group.dayIncomingCount} Received"
                                            3 -> "${group.dayOutgoingCount} Dialed"
                                            4 -> "Call Recorded"
                                            else -> buildString {
                                                val parts = mutableListOf<String>()
                                                if (group.dayOutgoingCount > 0) parts.add("${group.dayOutgoingCount} Dialed")
                                                if (group.dayIncomingCount > 0) parts.add("${group.dayIncomingCount} Received")
                                                if (group.dayMissedCount > 0) parts.add("${group.dayMissedCount} Missed")
                                                append(parts.joinToString(" • "))
                                            }
                                        }

                                        val cardDisplayCount = when (pageIndex) {
                                            1 -> group.dayMissedCount
                                            2 -> group.dayIncomingCount
                                            3 -> group.dayOutgoingCount
                                            4 -> 1
                                            else -> group.dayTotalCount
                                        }

                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 3.dp)
                                                .clickable {
                                                    val matchedPhoto = allContacts.find { it.name == group.name }?.photoUri
                                                    onContactClick(group.name ?: group.number, group.number, matchedPhoto)
                                                },
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
                                                            color = if (group.dayMissedCount > 0 && group.latestCallType == CallType.MISSED) AccentRed else MaterialTheme.colorScheme.onSurface
                                                        )
                                                        if (cardDisplayCount > 1) {
                                                            Spacer(Modifier.width(6.dp))
                                                            Surface(
                                                                shape = RoundedCornerShape(10.dp),
                                                                color = MaterialTheme.colorScheme.surfaceVariant
                                                            ) {
                                                                Text(
                                                                    text = "($cardDisplayCount)",
                                                                    fontSize = 12.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                                                    color = MaterialTheme.colorScheme.primary
                                                                )
                                                            }
                                                        }

                                                        // Unsaved Contact Color Badge Flag (Item 13)
                                                        if (!group.isSavedContact) {
                                                            Spacer(Modifier.width(6.dp))
                                                            Surface(
                                                                shape = RoundedCornerShape(8.dp),
                                                                color = Color(0xFFF59E0B).copy(alpha = 0.2f)
                                                            ) {
                                                                Text(
                                                                    text = "Unsaved",
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                                    color = Color(0xFFF59E0B)
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
                                                    }
                                                }

                                                // If call is recorded, show play button option
                                                if (group.hasRecording && group.recordingPath != null) {
                                                    IconButton(
                                                        onClick = { playAudio(group.recordingPath) },
                                                        modifier = Modifier
                                                            .padding(end = 4.dp)
                                                            .size(38.dp)
                                                            .background(Color(0xFFF59E0B).copy(alpha = 0.15f), CircleShape)
                                                    ) {
                                                        Icon(
                                                            if (playingAudioPath == group.recordingPath) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                            contentDescription = "Play Recording",
                                                            tint = Color(0xFFF59E0B),
                                                            modifier = Modifier.size(20.dp)
                                                        )
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
                    }
                }
            }

        }

        // 5. Expandable Bottom Search Bar (Item 2 & 4: Aligned single bar at bottom)
        AnimatedVisibility(
            visible = isSearchOpen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        ) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search call history or contact…", fontSize = 14.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        )
                    )
                    IconButton(onClick = {
                        if (searchQuery.isNotEmpty()) {
                            searchQuery = ""
                        } else {
                            isSearchOpen = false
                        }
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Close Search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        val handleKeyPress: (String) -> Unit = { digit ->
            enteredNumber += digit
        }

        val handleBackspace: () -> Unit = {
            if (enteredNumber.isNotEmpty()) {
                enteredNumber = enteredNumber.dropLast(1)
            }
        }

        val handleLongPressDigit: (Int) -> Unit = { speedIndex ->
            if (speedIndex == 0) {
                enteredNumber += "+"
            } else {
                val sp = speedDials.find { it.digit == speedIndex }
                if (sp != null) {
                    onCallClick(sp.number, 0)
                }
            }
        }

        // Dual Floating Action Buttons: Search FAB + Keypad FAB (Hidden when search is open)
        if (!isDialpadOpen && !isSearchOpen) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FloatingActionButton(
                    onClick = { isSearchOpen = true },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    modifier = Modifier.size(52.dp).shadow(6.dp, CircleShape)
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(24.dp))
                }

                FloatingActionButton(
                    onClick = { isDialpadOpen = true },
                    containerColor = AccentGreen,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp).shadow(12.dp, CircleShape)
                ) {
                    Icon(Icons.Default.Dialpad, contentDescription = "Open Dialpad", modifier = Modifier.size(30.dp))
                }
            }
        }

        // Keypad Sheet
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialKeyModern(
    digit: String,
    sub: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = modifier
            .size(68.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = digit,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (sub.isNotEmpty()) {
                Text(
                    text = sub,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

