package com.amitbharat.phonedialer.ui.contacts

import androidx.compose.animation.*
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
    onContactClick: (name: String, number: String, photoUri: String?, contact: Contact) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchOpen by remember { mutableStateOf(false) }
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
        Box(modifier = modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 2.dp)
            ) {
                // Header Row (Clean, no extra vertical padding)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Contacts (${contacts.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onSyncDeviceContacts) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync Contacts", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                // Contacts List
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
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(filteredContacts, key = { it.id.toString() + "_" + it.name }) { contact ->
                            ContactItemRow(
                                contact = contact,
                                onCallClick = { onCallClick(contact.numbers.firstOrNull() ?: "") },
                                onToggleFavorite = { onToggleFavorite(contact) },
                                onContactClick = { onContactClick(contact.name, contact.numbers.firstOrNull() ?: "", contact.photoUri, contact) }
                            )
                        }
                    }
                }
            }

            // Expandable Bottom Search Bar (Item 6: Open search box on the bottom)
            AnimatedVisibility(
                visible = isSearchOpen,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 76.dp)
            ) {
                Surface(
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search contacts…") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
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
                }
            }

            // Dual Floating Action Buttons: Search FAB + Contact Add FAB on Bottom Right
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Floating Search FAB
                FloatingActionButton(
                    onClick = { isSearchOpen = !isSearchOpen },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    modifier = Modifier.size(52.dp).shadow(6.dp, CircleShape)
                ) {
                    Icon(if (isSearchOpen) Icons.Default.Close else Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(24.dp))
                }

                // Add Contact FAB
                FloatingActionButton(
                    onClick = { showAddScreen = true },
                    containerColor = AccentGreen,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp).shadow(12.dp, CircleShape)
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "Add Contact", modifier = Modifier.size(30.dp))
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
    onContactClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable { onContactClick() },
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
}
