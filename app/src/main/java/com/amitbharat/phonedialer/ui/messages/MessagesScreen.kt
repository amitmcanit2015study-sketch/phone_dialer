package com.amitbharat.phonedialer.ui.messages

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amitbharat.phonedialer.model.Contact
import com.amitbharat.phonedialer.ui.theme.AccentGreen
import com.amitbharat.phonedialer.utils.ContactAvatar
import java.text.SimpleDateFormat
import java.util.*

data class MessageThread(
    val normalizedNumber: String,
    val displayAddress: String,
    val contactName: String?,
    val latestBody: String,
    val latestTimestamp: Long,
    val unreadCount: Int,
    val threadIds: List<Long>,
    val isOutgoing: Boolean = false,
    val isDelivered: Boolean = false
)

data class SmsMessageItem(
    val id: Long,
    val address: String,
    val body: String,
    val timestamp: Long,
    val isOutgoing: Boolean,
    val status: Int = -1,
    val isDelivered: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    contacts: List<Contact>,
    onCallClick: (String) -> Unit,
    onOpenThread: ((MessageThread) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var isSearchOpen by remember { mutableStateOf(false) }
    var selectedThread by remember { mutableStateOf<MessageThread?>(null) }
    var showNewComposer by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen) {
            focusRequester.requestFocus()
        }
    }

    BackHandler(enabled = showNewComposer || selectedThread != null || isSearchOpen) {
        when {
            showNewComposer -> showNewComposer = false
            selectedThread != null -> selectedThread = null
            isSearchOpen -> {
                isSearchOpen = false
                searchQuery = ""
            }
        }
    }

    fun normalizeNumber(raw: String): String {
        val digits = raw.replace(Regex("[^0-9]"), "")
        return if (digits.length >= 10) digits.takeLast(10) else digits
    }

    var syncTrigger by remember { mutableStateOf(0) }

    val smsRepo = remember { com.amitbharat.phonedialer.repository.SmsRepository.getInstance(context) }
    val threads by smsRepo.threads.collectAsState()

    LaunchedEffect(contacts, syncTrigger) {
        smsRepo.loadThreads(contacts, forceRefresh = syncTrigger > 0)
    }

    val filteredThreads = remember(searchQuery, threads) {
        if (searchQuery.isBlank()) threads
        else threads.filter {
            (it.contactName?.contains(searchQuery, ignoreCase = true) == true) ||
                    it.displayAddress.contains(searchQuery) ||
                    it.latestBody.contains(searchQuery, ignoreCase = true)
        }
    }

    if (showNewComposer) {
        NewMessageComposerScreen(
            contacts = contacts,
            onBack = { showNewComposer = false },
            onSend = { targetNum, text ->
                showNewComposer = false
                val norm = normalizeNumber(targetNum)
                val existing = threads.find { it.normalizedNumber == norm }
                val targetThread = existing ?: run {
                    val matchingContact = contacts.find { c -> c.numbers.any { n -> normalizeNumber(n) == norm } }
                    MessageThread(
                        normalizedNumber = norm,
                        displayAddress = targetNum,
                        contactName = matchingContact?.name,
                        latestBody = text,
                        latestTimestamp = System.currentTimeMillis(),
                        unreadCount = 0,
                        threadIds = emptyList()
                    )
                }
                if (onOpenThread != null) {
                    onOpenThread(targetThread)
                } else {
                    selectedThread = targetThread
                }
            }
        )
    } else if (selectedThread != null) {
        ChatThreadScreen(
            thread = selectedThread!!,
            contacts = contacts,
            onBack = { selectedThread = null },
            onCallClick = { onCallClick(selectedThread!!.displayAddress) }
        )
    } else {
        Box(modifier = modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 2.dp)
            ) {
                // Top-Anchored Search Bar when isSearchOpen is true
                AnimatedVisibility(
                    visible = isSearchOpen,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search messages…", fontSize = 14.sp) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester),
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
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Standard Header (When search is inactive)
                if (!isSearchOpen) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Messages (${threads.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { isSearchOpen = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            FilledTonalButton(
                                onClick = {
                                    syncTrigger++
                                    Toast.makeText(context, "Syncing SMS messages…", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Sync", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Sync SMS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (filteredThreads.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Message, contentDescription = null, modifier = Modifier.size(54.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            Spacer(Modifier.height(8.dp))
                            Text("No messages found", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp)
                    ) {
                        items(filteredThreads, key = { it.normalizedNumber }) { thread ->
                            val formattedTime = remember(thread.latestTimestamp) {
                                val diff = System.currentTimeMillis() - thread.latestTimestamp
                                if (diff < 24 * 60 * 60 * 1000) {
                                    SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(thread.latestTimestamp))
                                } else {
                                    SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(thread.latestTimestamp))
                                }
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable {
                                        if (onOpenThread != null) {
                                            onOpenThread(thread)
                                        } else {
                                            selectedThread = thread
                                        }
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                val resolvedName = thread.contactName ?: remember(thread.displayAddress, contacts) {
                                    val norm = smsRepo.normalizeNumber(thread.displayAddress)
                                    val match = contacts.find { c -> c.numbers.any { smsRepo.normalizeNumber(it) == norm } }
                                    match?.name ?: smsRepo.resolveContactName(thread.displayAddress, contacts)
                                }
                                val hasSavedName = !resolvedName.isNullOrBlank() && resolvedName != thread.displayAddress

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ContactAvatar(name = if (hasSavedName) resolvedName!! else thread.displayAddress, size = 48.dp, fontSize = 18.sp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = if (hasSavedName) resolvedName!! else thread.displayAddress,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                if (hasSavedName) {
                                                    Text(
                                                        text = thread.displayAddress,
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                            Text(
                                                text = formattedTime,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(Modifier.height(3.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (thread.isOutgoing) {
                                                Icon(
                                                    imageVector = if (thread.isDelivered) Icons.Default.DoneAll else Icons.Default.Check,
                                                    contentDescription = if (thread.isDelivered) "Delivered" else "Sent",
                                                    tint = if (thread.isDelivered) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.DoneAll,
                                                    contentDescription = "Received",
                                                    tint = AccentGreen,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                            }
                                            Text(
                                                text = thread.latestBody,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Dual Floating Action Buttons: Search FAB + New Message FAB on Bottom Right (Hidden when search is open)
            if (!isSearchOpen) {
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
                        onClick = { showNewComposer = true },
                        containerColor = AccentGreen,
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.size(64.dp).shadow(12.dp, CircleShape)
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = "New Message", modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

// Dedicated New Message Composer Screen (Item 9)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewMessageComposerScreen(
    contacts: List<Contact>,
    onBack: () -> Unit,
    onSend: (recipientNumber: String, messageText: String) -> Unit
) {
    val context = LocalContext.current
    var recipientNumber by remember { mutableStateOf("") }
    var messageText by remember { mutableStateOf("") }
    var showContactPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Message", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Recipient Field with Contact Add Plus Icon Button (+)
            OutlinedTextField(
                value = recipientNumber,
                onValueChange = { recipientNumber = it },
                label = { Text("To: Phone number or contact") },
                trailingIcon = {
                    IconButton(onClick = { showContactPicker = true }) {
                        Icon(Icons.Default.AddCircle, contentDescription = "Pick Contact", tint = AccentGreen, modifier = Modifier.size(28.dp))
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Message Body Text Area
            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                label = { Text("Type your message…") },
                minLines = 4,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Send Action Button
            Button(
                onClick = {
                    if (recipientNumber.isNotBlank() && messageText.isNotBlank()) {
                        try {
                            val smsManager = SmsManager.getDefault()
                            smsManager.sendTextMessage(recipientNumber, null, messageText, null, null)
                            Toast.makeText(context, "Message sent to $recipientNumber", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:$recipientNumber")).apply {
                                putExtra("sms_body", messageText)
                            }
                            context.startActivity(intent)
                        }
                        onSend(recipientNumber, messageText)
                    } else {
                        Toast.makeText(context, "Please enter recipient number and message text", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("SEND MESSAGE", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }

    // Contact Picker Dialog when (+) is clicked
    if (showContactPicker) {
        AlertDialog(
            onDismissRequest = { showContactPicker = false },
            title = { Text("Select Contact", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.height(350.dp)) {
                    items(contacts, key = { it.id.toString() + "_" + it.name }) { contact ->
                        val num = contact.numbers.firstOrNull() ?: ""
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (num.isNotBlank()) {
                                        recipientNumber = num
                                        showContactPicker = false
                                    }
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ContactAvatar(name = contact.name, photoUri = contact.photoUri, size = 40.dp, fontSize = 16.sp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(contact.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(num, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showContactPicker = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatThreadScreen(
    thread: MessageThread,
    contacts: List<Contact> = emptyList(),
    onBack: () -> Unit,
    onCallClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val smsRepo = remember { com.amitbharat.phonedialer.repository.SmsRepository.getInstance(context) }

    val resolvedName = thread.contactName ?: remember(thread.displayAddress, contacts) {
        val norm = smsRepo.normalizeNumber(thread.displayAddress)
        val match = contacts.find { c -> c.numbers.any { smsRepo.normalizeNumber(it) == norm } }
        match?.name ?: smsRepo.resolveContactName(thread.displayAddress, contacts)
    }
    val hasSavedName = !resolvedName.isNullOrBlank() && resolvedName != thread.displayAddress

    var messageInput by remember { mutableStateOf("") }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var localSentMessages by remember(thread.normalizedNumber) { mutableStateOf<List<SmsMessageItem>>(emptyList()) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    val density = androidx.compose.ui.platform.LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val isImeVisible = imeBottom > 0
    var isInputFocused by remember { mutableStateOf(false) }

    fun normalizeNumber(raw: String): String {
        val digits = raw.replace(Regex("[^0-9]"), "")
        return if (digits.length >= 7) digits.takeLast(10) else digits
    }

    // Fetch all SMS messages for this normalized number
    val messages = remember(thread.normalizedNumber, refreshTrigger) {
        val list = mutableListOf<SmsMessageItem>()
        try {
            val cursor: Cursor? = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf("_id", "address", "body", "date", "type", "status"),
                null,
                null,
                "date ASC"
            )
            cursor?.use {
                val idIdx = it.getColumnIndex("_id")
                val addrIdx = it.getColumnIndex("address")
                val bodyIdx = it.getColumnIndex("body")
                val dateIdx = it.getColumnIndex("date")
                val typeIdx = it.getColumnIndex("type")
                val statusIdx = it.getColumnIndex("status")

                val targetNorm = thread.normalizedNumber

                while (it.moveToNext()) {
                    val addr = if (addrIdx >= 0) it.getString(addrIdx) ?: "" else ""
                    val key = smsRepo.getThreadKey(addr)
                    if (key == targetNorm || normalizeNumber(addr) == targetNorm) {
                        val id = if (idIdx >= 0) it.getLong(idIdx) else 0L
                        val body = if (bodyIdx >= 0) it.getString(bodyIdx) ?: "" else ""
                        val date = if (dateIdx >= 0) it.getLong(dateIdx) else System.currentTimeMillis()
                        val type = if (typeIdx >= 0) it.getInt(typeIdx) else Telephony.Sms.MESSAGE_TYPE_INBOX
                        val status = if (statusIdx >= 0) it.getInt(statusIdx) else -1
                        val isOut = type == Telephony.Sms.MESSAGE_TYPE_SENT || type == Telephony.Sms.MESSAGE_TYPE_OUTBOX
                        val isDelivered = status == 0

                        list.add(
                            SmsMessageItem(
                                id = id,
                                address = addr,
                                body = body,
                                timestamp = date,
                                isOutgoing = isOut,
                                status = status,
                                isDelivered = isDelivered
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    val allMessages = remember(messages, localSentMessages) {
        (messages + localSentMessages)
            .distinctBy { "${it.address}_${it.body}_${it.timestamp / 3000}" }
            .sortedBy { it.timestamp }
    }

    // Keep recent messages visible above keyboard whenever keyboard opens or input is focused
    LaunchedEffect(isImeVisible, imeBottom) {
        if (allMessages.isNotEmpty()) {
            kotlinx.coroutines.delay(60)
            listState.scrollToItem(allMessages.size - 1)
        }
    }

    LaunchedEffect(isInputFocused) {
        if (isInputFocused && allMessages.isNotEmpty()) {
            kotlinx.coroutines.delay(120)
            listState.scrollToItem(allMessages.size - 1)
        }
    }

    LaunchedEffect(allMessages.size) {
        if (allMessages.isNotEmpty()) {
            listState.scrollToItem(allMessages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ContactAvatar(name = if (hasSavedName) resolvedName!! else thread.displayAddress, size = 38.dp, fontSize = 15.sp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(if (hasSavedName) resolvedName!! else thread.displayAddress, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            if (hasSavedName) {
                                Text(thread.displayAddress, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onCallClick) {
                        Icon(Icons.Default.Call, contentDescription = "Call", tint = AccentGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Surface(
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = messageInput,
                        onValueChange = { messageInput = it },
                        placeholder = { Text("Type a message…") },
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { focusState ->
                                isInputFocused = focusState.isFocused
                            },
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            val textToSend = messageInput.trim()
                            if (textToSend.isNotBlank()) {
                                val immediateItem = SmsMessageItem(
                                    id = System.currentTimeMillis(),
                                    address = thread.displayAddress,
                                    body = textToSend,
                                    timestamp = System.currentTimeMillis(),
                                    isOutgoing = true
                                )
                                localSentMessages = localSentMessages + immediateItem
                                messageInput = ""

                                try {
                                    val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                        context.getSystemService(android.telephony.SmsManager::class.java)
                                    } else {
                                        @Suppress("DEPRECATION")
                                        android.telephony.SmsManager.getDefault()
                                    }
                                    smsManager.sendTextMessage(thread.displayAddress, null, textToSend, null, null)
                                    Toast.makeText(context, "Message sent", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:${thread.displayAddress}")).apply {
                                            putExtra("sms_body", textToSend)
                                        }
                                        context.startActivity(intent)
                                    } catch (ex: Exception) {}
                                }

                                try {
                                    val values = android.content.ContentValues().apply {
                                        put(Telephony.Sms.ADDRESS, thread.displayAddress)
                                        put(Telephony.Sms.BODY, textToSend)
                                        put(Telephony.Sms.DATE, System.currentTimeMillis())
                                        put(Telephony.Sms.READ, 1)
                                        put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                                    }
                                    context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
                                } catch (e: Exception) {}

                                com.amitbharat.phonedialer.repository.SmsRepository.getInstance(context)
                                    .updateThreadOptimistic(thread.displayAddress, textToSend, thread.contactName)

                                refreshTrigger++
                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(50)
                                    listState.scrollToItem(allMessages.size - 1)
                                }
                            }
                        },
                        modifier = Modifier.size(46.dp).background(AccentGreen, CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(allMessages, key = { it.id.toString() + "_" + it.timestamp + "_" + it.isOutgoing }) { msg ->
                val align = if (msg.isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
                val bg = if (msg.isOutgoing) AccentGreen.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant

                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = align) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = bg,
                        modifier = Modifier.widthIn(max = 280.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(msg.body, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.align(Alignment.End),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(msg.timestamp)),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(4.dp))
                                if (msg.isOutgoing) {
                                    if (msg.isDelivered) {
                                        Icon(
                                            imageVector = Icons.Default.DoneAll,
                                            contentDescription = "Delivered",
                                            tint = AccentGreen,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Sent",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.DoneAll,
                                        contentDescription = "Received",
                                        tint = AccentGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
