package com.amitbharat.phonedialer.ui.main

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amitbharat.phonedialer.model.CallLogItem
import com.amitbharat.phonedialer.model.Contact
import com.amitbharat.phonedialer.model.SpeedDialItem
import com.amitbharat.phonedialer.ui.contacts.ContactDetailsScreen
import com.amitbharat.phonedialer.ui.contacts.ContactsScreen
import com.amitbharat.phonedialer.ui.dialer.DialerScreen
import com.amitbharat.phonedialer.ui.favorites.FavoritesScreen
import com.amitbharat.phonedialer.ui.messages.MessagesScreen
import com.amitbharat.phonedialer.ui.settings.SettingsScreen
import com.amitbharat.phonedialer.utils.ThemeMode

enum class MainTab {
    DIALER,
    CONTACTS,
    MESSAGES,
    FAVORITES
}

data class ContactDetailSelection(
    val name: String,
    val number: String,
    val photoUri: String?,
    val contact: Contact? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    contacts: List<Contact>,
    favorites: List<Contact>,
    callLogs: List<CallLogItem>,
    speedDials: List<SpeedDialItem>,
    onCallClick: (String, Int) -> Unit,
    onAddContact: (Contact) -> Unit,
    onToggleFavorite: (Contact) -> Unit,
    onDeleteContact: (Contact) -> Unit,
    onDeleteCallLog: (Long) -> Unit,
    onSyncDeviceContacts: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit
) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(MainTab.DIALER) }
    val tabHistory = remember { mutableStateListOf(MainTab.DIALER) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showAboutScreen by remember { mutableStateOf(false) }
    var showSettingsScreen by remember { mutableStateOf(false) }
    var selectedContactDetails by remember { mutableStateOf<ContactDetailSelection?>(null) }
    
    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    fun navigateToTab(tab: MainTab) {
        if (currentTab != tab) {
            currentTab = tab
            tabHistory.add(tab)
        }
    }

    // Root Level Back Handler for Smooth Navigation & Double-Back App Exit
    BackHandler {
        when {
            selectedContactDetails != null -> selectedContactDetails = null
            showAboutScreen -> showAboutScreen = false
            showSettingsScreen -> showSettingsScreen = false
            tabHistory.size > 1 -> {
                tabHistory.removeAt(tabHistory.lastIndex)
                currentTab = tabHistory.last()
            }
            currentTab != MainTab.DIALER -> {
                currentTab = MainTab.DIALER
                tabHistory.clear()
                tabHistory.add(MainTab.DIALER)
            }
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPressTime < 2000) {
                    (context as? Activity)?.finish()
                } else {
                    lastBackPressTime = now
                    Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (selectedContactDetails != null) {
        val details = selectedContactDetails!!
        ContactDetailsScreen(
            name = details.name,
            number = details.number,
            photoUri = details.photoUri,
            contact = details.contact ?: contacts.find { it.name == details.name },
            callLogs = callLogs,
            onBack = { selectedContactDetails = null },
            onCallClick = { num -> onCallClick(num, 0) },
            onMessageClick = { num ->
                selectedContactDetails = null
                currentTab = MainTab.MESSAGES
            },
            onToggleFavorite = onToggleFavorite,
            onEditContact = { c ->
                selectedContactDetails = null
                currentTab = MainTab.CONTACTS
            }
        )
    } else if (showAboutScreen) {
        com.amitbharat.phonedialer.ui.settings.AboutScreen(
            onBack = { showAboutScreen = false }
        )
    } else if (showSettingsScreen) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Settings", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { showSettingsScreen = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                SettingsScreen(
                    onThemeChange = onThemeChange,
                    onOpenAbout = { showAboutScreen = true }
                )
            }
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = when (currentTab) {
                                    MainTab.DIALER -> "Phone Dialer"
                                    MainTab.CONTACTS -> "Contacts"
                                    MainTab.MESSAGES -> "Messages"
                                    MainTab.FAVORITES -> "Favorites"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                            Text(
                                text = "by Amit Bharat",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    showSettingsScreen = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(androidx.compose.ui.res.stringResource(com.amitbharat.phonedialer.R.string.action_about)) },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    showAboutScreen = true
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    // 1. Dialer
                    NavigationBarItem(
                        selected = currentTab == MainTab.DIALER,
                        onClick = { navigateToTab(MainTab.DIALER) },
                        icon = { Icon(Icons.Default.Call, contentDescription = "Dialer") },
                        label = { Text("Dialer", fontSize = 12.sp) }
                    )
                    // 2. Contacts
                    NavigationBarItem(
                        selected = currentTab == MainTab.CONTACTS,
                        onClick = { navigateToTab(MainTab.CONTACTS) },
                        icon = { Icon(Icons.Default.People, contentDescription = "Contacts") },
                        label = { Text("Contacts", fontSize = 12.sp) }
                    )
                    // 3. Messages
                    NavigationBarItem(
                        selected = currentTab == MainTab.MESSAGES,
                        onClick = { navigateToTab(MainTab.MESSAGES) },
                        icon = { Icon(Icons.Default.Message, contentDescription = "Messages") },
                        label = { Text("Messages", fontSize = 12.sp) }
                    )
                    // 4. Favorites
                    NavigationBarItem(
                        selected = currentTab == MainTab.FAVORITES,
                        onClick = { navigateToTab(MainTab.FAVORITES) },
                        icon = { Icon(Icons.Default.Star, contentDescription = "Favorites") },
                        label = { Text("Favorites", fontSize = 12.sp) }
                    )
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                when (currentTab) {
                    MainTab.DIALER -> DialerScreen(
                        allContacts = contacts,
                        favorites = favorites,
                        callLogs = callLogs,
                        speedDials = speedDials,
                        onCallClick = onCallClick,
                        onDeleteCallLog = onDeleteCallLog,
                        onContactClick = { name, number, photoUri ->
                            selectedContactDetails = ContactDetailSelection(name, number, photoUri)
                        }
                    )
                    MainTab.CONTACTS -> ContactsScreen(
                        contacts = contacts,
                        onCallClick = { num -> onCallClick(num, 0) },
                        onAddContact = onAddContact,
                        onToggleFavorite = onToggleFavorite,
                        onDeleteContact = onDeleteContact,
                        onSyncDeviceContacts = onSyncDeviceContacts,
                        onContactClick = { name, number, photoUri, contact ->
                            selectedContactDetails = ContactDetailSelection(name, number, photoUri, contact)
                        }
                    )
                    MainTab.MESSAGES -> MessagesScreen(
                        contacts = contacts,
                        onCallClick = { num -> onCallClick(num, 0) }
                    )
                    MainTab.FAVORITES -> FavoritesScreen(
                        favorites = favorites,
                        onCallClick = { num -> onCallClick(num, 0) },
                        onContactClick = { name, number, photoUri, contact ->
                            selectedContactDetails = ContactDetailSelection(name, number, photoUri, contact)
                        }
                    )
                }
            }
        }
    }
}
