package com.amitbharat.phonedialer.ui.messages

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsManager
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val threadId: Long,
    val address: String,
    val contactName: String?,
    val body: String,
    val timestamp: Long,
    val read: Boolean
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
    var selectedThread by remember { mutableStateOf<MessageThread?>(null) }
    var showNewMessageDialog by remember { mutableStateOf(false) }

    // Fetch SMS Threads from ContentResolver
    val threads = remember {
        val list = mutableListOf<MessageThread>()
        try {
            val cursor: Cursor? = context.contentResolver.query(
                Telephony.Sms.Conversations.CONTENT_URI,
                arrayOf("thread_id", "address", "body", "date", "read"),
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

                val seenThreads = mutableSetOf<Long>()

                while (it.moveToNext()) {
                    val threadId = if (threadIdIdx >= 0) it.getLong(threadIdIdx) else 0L
                    if (threadId > 0 && seenThreads.add(threadId)) {
                        val address = if (addressIdx >= 0) it.getString(addressIdx) ?: "" else ""
                        val body = if (bodyIdx >= 0) it.getString(bodyIdx) ?: "" else ""
                        val date = if (dateIdx >= 0) it.getLong(dateIdx) else System.currentTimeMillis()
                        val read = if (readIdx >= 0) it.getInt(readIdx) == 1 else true

                        val cleanAddr = address.replace("[^0-9+]".toRegex(), "")
                        val matchingContact = contacts.find { c ->
                            c.numbers.any { num -> num.replace("[^0-9+]".toRegex(), "") == cleanAddr }
                        }

                        list.add(
                            MessageThread(
                                threadId = threadId,
                                address = address.ifBlank { "Unknown" },
                                contactName = matchingContact?.name,
                                body = body,
                                timestamp = date,
                                read = read
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

    val filteredThreads = remember(searchQuery, threads) {
        if (searchQuery.isBlank()) threads
        else threads.filter {
            (it.contactName?.contains(searchQuery, ignoreCase = true) == true) ||
            it.address.contains(searchQuery) ||
            it.body.contains(searchQuery, ignoreCase = true)
        }
    }

    if (selectedThread != null) {
        ChatThreadScreen(
            thread = selectedThread!!,
            onBack = { selectedThread = null },
            onCallClick = { onCallClick(selectedThread!!.address) }
        )
    } else {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showNewMessageDialog = true },
                    containerColor = AccentGreen,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Chat, contentDescription = "New Message")
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Header
                Text(
                    text = "Messages (${threads.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search messages…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )

                Spacer(Modifier.height(8.dp))

                if (filteredThreads.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Message, contentDescription = null, modifier = Modifier.size(54.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            Spacer(Modifier.height(8.dp))
                            Text("No messages found", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filteredThreads, key = { it.threadId.toString() + "_" + it.address }) { thread ->
                            val formattedTime = remember(thread.timestamp) {
                                val diff = System.currentTimeMillis() - thread.timestamp
                                if (diff < 24 * 60 * 60 * 1000) {
                                    SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(thread.timestamp))
                                } else {
                                    SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(thread.timestamp))
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
                                    ContactAvatar(name = thread.contactName ?: thread.address, size = 48.dp, fontSize = 18.sp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = thread.contactName ?: thread.address,
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
                                            text = thread.body,
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
    }

    if (showNewMessageDialog) {
        NewMessageDialog(
            contacts = contacts,
            onDismiss = { showNewMessageDialog = false },
            onSend = { targetNumber, messageText ->
                showNewMessageDialog = false
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:$targetNumber")).apply {
                        putExtra("sms_body", messageText)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Could not open SMS app", Toast.LENGTH_SHORT).show()
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

    val messages = remember(thread.threadId) {
        val list = mutableListOf<SmsMessageItem>()
        try {
            val cursor: Cursor? = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf("_id", "address", "body", "date", "type"),
                "thread_id = ?",
                arrayOf(thread.threadId.toString()),
                "date ASC"
            )
            cursor?.use {
                val idIdx = it.getColumnIndex("_id")
                val addrIdx = it.getColumnIndex("address")
                val bodyIdx = it.getColumnIndex("body")
                val dateIdx = it.getColumnIndex("date")
                val typeIdx = it.getColumnIndex("type")

                while (it.moveToNext()) {
                    val id = if (idIdx >= 0) it.getLong(idIdx) else 0L
                    val addr = if (addrIdx >= 0) it.getString(addrIdx) ?: "" else ""
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
                        ContactAvatar(name = thread.contactName ?: thread.address, size = 38.dp, fontSize = 15.sp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(thread.contactName ?: thread.address, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(thread.address, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                    smsManager.sendTextMessage(thread.address, null, messageInput, null, null)
                                    Toast.makeText(context, "Message sent", Toast.LENGTH_SHORT).show()
                                    messageInput = ""
                                } catch (e: Exception) {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:${thread.address}")).apply {
                                        putExtra("sms_body", messageInput)
                                    }
                                    context.startActivity(intent)
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
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
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

@Composable
fun NewMessageDialog(
    contacts: List<Contact>,
    onDismiss: () -> Unit,
    onSend: (String, String) -> Unit
) {
    var recipient by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Message", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = recipient,
                    onValueChange = { recipient = it },
                    label = { Text("Recipient Phone Number") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Message Text") },
                    minLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (recipient.isNotBlank() && message.isNotBlank()) onSend(recipient, message) },
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
            ) {
                Text("Send")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
