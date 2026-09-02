package com.amitbharat.phonedialer.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amitbharat.phonedialer.model.CallLogItem
import com.amitbharat.phonedialer.model.Contact
import com.amitbharat.phonedialer.model.SpeedDialItem
import com.amitbharat.phonedialer.ui.contacts.ContactsScreen
import com.amitbharat.phonedialer.ui.dialer.DialerScreen
import com.amitbharat.phonedialer.ui.favorites.FavoritesScreen
import com.amitbharat.phonedialer.ui.settings.SettingsScreen
import com.amitbharat.phonedialer.utils.ThemeMode

enum class MainTab {
    DIALER,
    CONTACTS,
    FAVORITES,
    SETTINGS
}

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
    var currentTab by remember { mutableStateOf(MainTab.DIALER) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = when (currentTab) {
                                MainTab.DIALER -> "Phone Dialer"
                                MainTab.CONTACTS -> "Contacts"
                                MainTab.FAVORITES -> "Favorites"
                                MainTab.SETTINGS -> "Settings"
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = currentTab == MainTab.DIALER,
                    onClick = { currentTab = MainTab.DIALER },
                    icon = { Icon(Icons.Default.Call, contentDescription = "Dialer") },
                    label = { Text("Dialer", fontSize = 12.sp) }
                )
                NavigationBarItem(
                    selected = currentTab == MainTab.CONTACTS,
                    onClick = { currentTab = MainTab.CONTACTS },
                    icon = { Icon(Icons.Default.People, contentDescription = "Contacts") },
                    label = { Text("Contacts", fontSize = 12.sp) }
                )
                NavigationBarItem(
                    selected = currentTab == MainTab.FAVORITES,
                    onClick = { currentTab = MainTab.FAVORITES },
                    icon = { Icon(Icons.Default.Star, contentDescription = "Favorites") },
                    label = { Text("Favorites", fontSize = 12.sp) }
                )
                NavigationBarItem(
                    selected = currentTab == MainTab.SETTINGS,
                    onClick = { currentTab = MainTab.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings", fontSize = 12.sp) }
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
                    onDeleteCallLog = onDeleteCallLog
                )
                MainTab.CONTACTS -> ContactsScreen(
                    contacts = contacts,
                    onCallClick = { num -> onCallClick(num, 0) },
                    onAddContact = onAddContact,
                    onToggleFavorite = onToggleFavorite,
                    onDeleteContact = onDeleteContact,
                    onSyncDeviceContacts = onSyncDeviceContacts
                )
                MainTab.FAVORITES -> FavoritesScreen(
                    favorites = favorites,
                    onCallClick = { num -> onCallClick(num, 0) }
                )
                MainTab.SETTINGS -> SettingsScreen(
                    onThemeChange = onThemeChange
                )
            }
        }
    }
}
