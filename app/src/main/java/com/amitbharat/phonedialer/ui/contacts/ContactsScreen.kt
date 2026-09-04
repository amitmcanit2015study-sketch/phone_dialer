package com.amitbharat.phonedialer.ui.contacts

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amitbharat.phonedialer.model.Contact
import com.amitbharat.phonedialer.ui.theme.AccentGreen
import com.amitbharat.phonedialer.utils.ContactAvatar

@Composable
fun ContactsScreen(
    contacts: List<Contact>,
    onCallClick: (String) -> Unit,
    onAddContact: (Contact) -> Unit,
    onToggleFavorite: (Contact) -> Unit,
    onDeleteContact: (Contact) -> Unit,
    onSyncDeviceContacts: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var showAddScreen by remember { mutableStateOf(false) }
    var editingContact by remember { mutableStateOf<Contact?>(null) }

    val filteredContacts = remember(searchQuery, contacts) {
        if (searchQuery.isBlank()) contacts
        else contacts.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.numbers.any { num -> num.contains(searchQuery) }
        }
    }

    if (showAddScreen || editingContact != null) {
        AddEditContactScreen(
            initialContact = editingContact,
            onBack = {
                showAddScreen = false
                editingContact = null
            },
            onSave = { contact ->
                onAddContact(contact)
                showAddScreen = false
                editingContact = null
            }
        )
    } else {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddScreen = true },
                    containerColor = AccentGreen,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "Add Contact")
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Header with Contact Count
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Contacts (${contacts.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onSyncDeviceContacts) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync Contacts", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search contacts…") },
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

                if (filteredContacts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No contacts found", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = onSyncDeviceContacts) {
                                Text("Sync Device Contacts")
                            }
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filteredContacts, key = { it.id.toString() + "_" + it.name }) { contact ->
                            ContactItemRow(
                                contact = contact,
                                onCallClick = { onCallClick(contact.numbers.firstOrNull() ?: "") },
                                onToggleFavorite = { onToggleFavorite(contact) },
                                onDeleteContact = { onDeleteContact(contact) },
                                onEditContact = { editingContact = contact }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContactItemRow(
    contact: Contact,
    onCallClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDeleteContact: () -> Unit,
    onEditContact: () -> Unit
) {
    val context = LocalContext.current
    var showSheet by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable { showSheet = true },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ContactAvatar(
                name = contact.name,
                photoUri = contact.photoUri,
                size = 46.dp,
                fontSize = 18.sp
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = contact.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(text = contact.numbers.firstOrNull() ?: "No number", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (contact.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Favorite",
                    tint = if (contact.isFavorite) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onCallClick) {
                Icon(Icons.Default.Call, contentDescription = "Call", tint = AccentGreen)
            }
        }
    }

    if (showSheet) {
        AlertDialog(
            onDismissRequest = { showSheet = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ContactAvatar(name = contact.name, photoUri = contact.photoUri, size = 52.dp, fontSize = 20.sp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(contact.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(contact.numbers.firstOrNull() ?: "", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            text = {
                Column {
                    contact.numbers.forEach { num ->
                        Text("Phone: $num", fontSize = 15.sp, modifier = Modifier.padding(vertical = 2.dp))
                    }
                    if (!contact.email.isNullOrBlank()) {
                        Text("Email: ${contact.email}", fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp))
                    }
                    if (!contact.company.isNullOrBlank()) {
                        Text("Company: ${contact.company}", fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp))
                    }
                    if (!contact.notes.isNullOrBlank()) {
                        Text("Notes: ${contact.notes}", fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showSheet = false
                    onCallClick()
                }, colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) {
                    Text("Call")
                }
            },
            dismissButton = {
                Row {
                    OutlinedButton(onClick = {
                        showSheet = false
                        onEditContact()
                    }) {
                        Text("Edit")
                    }
                    Spacer(Modifier.width(6.dp))
                    OutlinedButton(onClick = {
                        showSheet = false
                        onDeleteContact()
                    }) {
                        Text("Delete")
                    }
                }
            }
        )
    }
}
