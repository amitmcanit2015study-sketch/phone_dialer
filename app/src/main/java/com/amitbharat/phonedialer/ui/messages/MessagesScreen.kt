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
    val threadIds: List<Long>
)

data class SmsMessageItem(
    val id: Long,
    val address: String,
    val body: String,
    val timestamp: Long,
    val isOutgoing: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    contacts: List<Contact>,
    onCallClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var isSearchOpen by remember { mutableStateOf(false) }
    var selectedThread by remember { mutableStateOf<MessageThread?>(null) }
    var showNewComposer by remember { mutableStateOf(false) }

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

    // Fetch and aggregate ALL SMS Messages grouped strictly by Normalized Phone Number
    val threads = remember(contacts) {
        val list = mutableListOf<MessageThread>()
        try {
            val cursor: Cursor? = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf("_id", "thread_id", "address", "body", "date", "read", "type"),
                null,
                null,
                "date DESC"
            )
            cursor?.use {
                val threadIdIdx = it.getColumnIndex("thread_id")
                val addressIdx = it.getColumnIndex("address")
                val bodyIdx = it.getColumnIndex("body")
                val dateIdx = it.getColumnIndex("date")
                val readIdx = it.getColumnIndex("read")

                val groupedMap = LinkedHashMap<String, MutableList<Pair<Long, String>>>() // normKey -> list of (date, body)
                val addressDisplayMap = HashMap<String, String>()
                val threadIdMap = HashMap<String, MutableList<Long>>()
                val unreadCountMap = HashMap<String, Int>()

                while (it.moveToNext()) {
                    val threadId = if (threadIdIdx >= 0) it.getLong(threadIdIdx) else 0L
                    val rawAddress = if (addressIdx >= 0) it.getString(addressIdx) ?: "" else ""
                    val body = if (bodyIdx >= 0) it.getString(bodyIdx) ?: "" else ""
                    val date = if (dateIdx >= 0) it.getLong(dateIdx) else System.currentTimeMillis()
                    val read = if (readIdx >= 0) it.getInt(readIdx) == 1 else true

                    if (rawAddress.isNotBlank()) {
                        val normKey = normalizeNumber(rawAddress)
                        if (normKey.isNotBlank()) {
                            groupedMap.getOrPut(normKey) { mutableListOf() }.add(Pair(date, body))
                            if (!addressDisplayMap.containsKey(normKey)) {
                                addressDisplayMap[normKey] = rawAddress
                            }
                            if (threadId > 0) {
                                threadIdMap.getOrPut(normKey) { mutableListOf() }.add(threadId)
                            }
                            if (!read) {
                                unreadCountMap[normKey] = (unreadCountMap[normKey] ?: 0) + 1
                            }
                        }
                    }
                }

                groupedMap.forEach { (normKey, itemsList) ->
                    val displayAddress = addressDisplayMap[normKey] ?: normKey
                    val latestItem = itemsList.first()
                    val matchingContact = contacts.find { c ->
                        c.numbers.any { num -> normalizeNumber(num) == normKey }
                    }

                    list.add(
                        MessageThread(
                            normalizedNumber = normKey,
                            displayAddress = displayAddress,
                            contactName = matchingContact?.name,
                            latestBody = latestItem.second,
                            latestTimestamp = latestItem.first,
                            unreadCount = unreadCountMap[normKey] ?: 0,
                            threadIds = threadIdMap[normKey]?.distinct() ?: emptyList()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
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
                if (existing != null) {
                    selectedThread = existing
                } else {
                    val matchingContact = contacts.find { c -> c.numbers.any { n -> normalizeNumber(n) == norm } }
                    selectedThread = MessageThread(
                        normalizedNumber = norm,
                        displayAddress = targetNum,
                        contactName = matchingContact?.name,
                        latestBody = text,
                        latestTimestamp = System.currentTimeMillis(),
                        unreadCount = 0,
                        threadIds = emptyList()
                    )
                }
            }
        )
    } else if (selectedThread != null) {
        ChatThreadScreen(
            thread = selectedThread!!,
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
                // Header (No extra vertical space)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Messages (${threads.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
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
                        contentPadding = PaddingValues(top = 12.dp, bottom = if (isSearchOpen) 150.dp else 100.dp)
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
                                    .clickable { selectedThread = thread },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ContactAvatar(name = thread.contactName ?: thread.displayAddress, size = 48.dp, fontSize = 18.sp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = thread.contactName ?: thread.displayAddress,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = formattedTime,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(Modifier.height(2.dp))
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

            // Expandable Bottom Search Bar (Item 2 & 5: Aligned single bar at bottom)
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
                            placeholder = { Text("Search messages…", fontSize = 14.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                        IconButton(onClick = { isSearchOpen = false; searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Close Search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Floating Action Buttons: Search FAB on Bottom Left + New Message FAB on Bottom Right (Hidden when search is open)
            if (!isSearchOpen) {
                Box(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(20.dp)) {
                    // Bottom Left Search Icon
                    FloatingActionButton(
                        onClick = { isSearchOpen = true },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        modifier = Modifier.align(Alignment.BottomStart).size(52.dp).shadow(6.dp, CircleShape)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(24.dp))
                    }

                    // Bottom Right New Message FAB
                    FloatingActionButton(
                        onClick = { showNewComposer = true },
                        containerColor = AccentGreen,
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.align(Alignment.BottomEnd).size(64.dp).shadow(12.dp, CircleShape)
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
    onBack: () -> Unit,
    onCallClick: () -> Unit
) {
    val context = LocalContext.current
    var messageInput by remember { mutableStateOf("") }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    fun normalizeNumber(raw: String): String {
        val digits = raw.replace(Regex("[^0-9]"), "")
        return if (digits.length >= 10) digits.takeLast(10) else digits
    }

    // Fetch all SMS messages for this normalized number (Item 10 & 9 auto-refresh)
    val messages = remember(thread.normalizedNumber, refreshTrigger) {
        val list = mutableListOf<SmsMessageItem>()
        try {
            val cursor: Cursor? = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf("_id", "address", "body", "date", "type"),
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

                val targetNorm = thread.normalizedNumber

                while (it.moveToNext()) {
                    val addr = if (addrIdx >= 0) it.getString(addrIdx) ?: "" else ""
                    if (normalizeNumber(addr) == targetNorm) {
                        val id = if (idIdx >= 0) it.getLong(idIdx) else 0L
                        val body = if (bodyIdx >= 0) it.getString(bodyIdx) ?: "" else ""
                        val date = if (dateIdx >= 0) it.getLong(dateIdx) else System.currentTimeMillis()
                        val type = if (typeIdx >= 0) it.getInt(typeIdx) else Telephony.Sms.MESSAGE_TYPE_INBOX

                        list.add(
                            SmsMessageItem(
                                id = id,
                                address = addr,
                                body = body,
                                timestamp = date,
                                isOutgoing = type == Telephony.Sms.MESSAGE_TYPE_SENT || type == Telephony.Sms.MESSAGE_TYPE_OUTBOX
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ContactAvatar(name = thread.contactName ?: thread.displayAddress, size = 38.dp, fontSize = 15.sp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(thread.contactName ?: thread.displayAddress, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(thread.displayAddress, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                modifier = Modifier.fillMaxWidth().navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = messageInput,
                        onValueChange = { messageInput = it },
                        placeholder = { Text("Type a message…") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (messageInput.isNotBlank()) {
                                try {
                                    val smsManager = SmsManager.getDefault()
                                    smsManager.sendTextMessage(thread.displayAddress, null, messageInput, null, null)
                                    Toast.makeText(context, "Message sent", Toast.LENGTH_SHORT).show()
                                    messageInput = ""
                                    refreshTrigger++
                                } catch (e: Exception) {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:${thread.displayAddress}")).apply {
                                        putExtra("sms_body", messageInput)
                                    }
                                    context.startActivity(intent)
                                    refreshTrigger++
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
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id.toString() + "_" + it.timestamp }) { msg ->
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
                            Spacer(Modifier.height(2.dp))
                            Text(
                                SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(msg.timestamp)),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
                }
            }
        }
    }
}
