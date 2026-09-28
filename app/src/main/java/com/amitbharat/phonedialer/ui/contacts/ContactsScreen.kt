package com.amitbharat.phonedialer.ui.contacts

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch
import com.amitbharat.phonedialer.ui.components.AlphabetScroller
import com.amitbharat.phonedialer.ui.components.ContactActionButtons
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amitbharat.phonedialer.model.Contact
import com.amitbharat.phonedialer.ui.theme.AccentGreen
import com.amitbharat.phonedialer.utils.ContactAvatar

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContactsScreen(
    contacts: List<Contact>,
    onCallClick: (String) -> Unit,
    onMessageClick: (String) -> Unit,
    onAddContact: (Contact) -> Unit,
    onToggleFavorite: (Contact) -> Unit,
    onDeleteContact: (Contact) -> Unit,
    onSyncDeviceContacts: () -> Unit,
    onContactClick: (name: String, number: String, photoUri: String?, contact: Contact) -> Unit,
    onSearchActive: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchOpen by remember { mutableStateOf(false) }
    var showAddScreen by remember { mutableStateOf(false) }
    var editingContact by remember { mutableStateOf<Contact?>(null) }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val density = androidx.compose.ui.platform.LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    var keyboardEverOpened by remember { mutableStateOf(false) }

    LaunchedEffect(isSearchOpen) {
        onSearchActive(isSearchOpen)
        if (isSearchOpen) {
            keyboardEverOpened = false
            focusRequester.requestFocus()
        } else {
            keyboardEverOpened = false
        }
    }

    LaunchedEffect(imeBottom, isSearchOpen) {
        if (isSearchOpen) {
            if (imeBottom > 200) {
                keyboardEverOpened = true
            } else if (keyboardEverOpened && imeBottom == 0) {
                // When keyboard is hidden, automatically close search box (Req)
                isSearchOpen = false
                searchQuery = ""
                onSearchActive(false)
            }
        }
    }

    BackHandler(enabled = isSearchOpen || showAddScreen || editingContact != null) {
        when {
            showAddScreen -> showAddScreen = false
            editingContact != null -> editingContact = null
            isSearchOpen -> {
                isSearchOpen = false
                searchQuery = ""
                onSearchActive(false)
            }
        }
    }

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

                // Header Row (When search is inactive)
                if (!isSearchOpen) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Contacts (${contacts.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
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
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(end = 26.dp),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp)
                        ) {
                            items(filteredContacts, key = { it.id.toString() + "_" + it.name }) { contact ->
                                ContactItemRow(
                                    contact = contact,
                                    onCallClick = { onCallClick(contact.numbers.firstOrNull() ?: "") },
                                    onMessageClick = { onMessageClick(contact.numbers.firstOrNull() ?: "") },
                                    onToggleFavorite = { onToggleFavorite(contact) },
                                    onContactClick = { onContactClick(contact.name, contact.numbers.firstOrNull() ?: "", contact.photoUri, contact) }
                                )
                            }
                        }
                        
                        val alphabet = remember(filteredContacts) {
                            filteredContacts.mapNotNull { it.name.firstOrNull()?.uppercaseChar() }
                                .filter { it.isLetter() }
                                .distinct()
                                .sorted()
                        }
                        
                        AlphabetScroller(
                            letters = alphabet,
                            onLetterSelect = { letter ->
                                val index = filteredContacts.indexOfFirst { it.name.firstOrNull()?.uppercaseChar() == letter }
                                if (index >= 0) {
                                    coroutineScope.launch {
                                        listState.scrollToItem(index)
                                    }
                                }
                            },
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp, top = 16.dp, bottom = 80.dp)
                        )
                    }
                }
            }

            // Dual Floating Action Buttons: Search FAB + Contact Add FAB on Bottom Right (Hidden when search is open)
            if (!isSearchOpen) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Floating Search FAB
                    FloatingActionButton(
                        onClick = {
                            isSearchOpen = true
                            onSearchActive(true)
                        },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        modifier = Modifier.size(52.dp).shadow(6.dp, CircleShape)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(24.dp))
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

            // Bottom-Anchored Search Bar directly above the keyboard when isSearchOpen is true (Zero gap with keyboard)
            if (isSearchOpen) {
                Surface(
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 16.dp,
                    tonalElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                androidx.compose.foundation.text.BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
                                        imeAction = androidx.compose.ui.text.input.ImeAction.Search
                                    ),
                                    decorationBox = { innerTextField ->
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = "Search contacts…",
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                            )
                                        }
                                        innerTextField()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(focusRequester)
                                        .padding(vertical = 4.dp)
                                )
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { searchQuery = "" },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                isSearchOpen = false
                                searchQuery = ""
                                onSearchActive(false)
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Search",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
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
    onMessageClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onContactClick: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable { onContactClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 1.5.dp),
        border = if (isDark) BorderStroke(1.dp, Color(0xFF282D37).copy(alpha = 0.7f)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
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
                Text(
                    text = contact.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = contact.numbers.firstOrNull() ?: "No number",
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(6.dp))
            val primaryNum = contact.numbers.firstOrNull() ?: ""

            ContactActionButtons(
                number = primaryNum,
                contactId = contact.id,
                contactNumbers = contact.numbers,
                onMessageClick = onMessageClick,
                onCallClick = onCallClick
            )
        }
    }
}
