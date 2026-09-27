package com.amitbharat.phonedialer.ui.main

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.amitbharat.phonedialer.R
import com.amitbharat.phonedialer.model.CallLogItem
import com.amitbharat.phonedialer.model.Contact
import com.amitbharat.phonedialer.repository.CallLogRepository
import com.amitbharat.phonedialer.repository.ContactsRepository
import com.amitbharat.phonedialer.telecom.TelecomHelper
import com.amitbharat.phonedialer.ui.theme.PhoneDialerTheme
import com.amitbharat.phonedialer.utils.PreferencesManager
import com.amitbharat.phonedialer.utils.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_TAB = "extra_open_tab"
        const val EXTRA_CHAT_ADDRESS = "extra_chat_address"
    }

    private lateinit var contactsRepo: ContactsRepository
    private lateinit var callLogRepo: CallLogRepository
    private lateinit var prefs: PreferencesManager

    private var targetTabState = mutableStateOf(com.amitbharat.phonedialer.ui.main.MainTab.DIALER)
    private var targetChatAddressState = mutableStateOf<String?>(null)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.READ_CONTACTS] == true) {
            lifecycleScope.launch {
                contactsRepo.syncDeviceContacts()
                com.amitbharat.phonedialer.utils.WhatsAppHelper.refreshWhatsAppContacts(this@MainActivity)
            }
        }
        if (permissions[Manifest.permission.READ_CALL_LOG] == true) {
            lifecycleScope.launch { callLogRepo.syncDeviceCallLogs() }
        }
        checkDefaultDialerRole()
    }

    private val defaultDialerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Handled default dialer result
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        contactsRepo = ContactsRepository(this)
        callLogRepo = CallLogRepository(this)
        prefs = PreferencesManager.getInstance(this)

        handleIntent(intent)
        requestRequiredPermissions()

        if (prefs.isKeepAliveEnabled()) {
            com.amitbharat.phonedialer.service.DialerKeepAliveService.startService(this)
        }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })

        lifecycleScope.launch(Dispatchers.IO) {
            // Lazy load non-critical data after UI is completely rendered
            kotlinx.coroutines.delay(3000)
            com.amitbharat.phonedialer.utils.WhatsAppHelper.refreshWhatsAppContacts(this@MainActivity)
            com.amitbharat.phonedialer.repository.SmsRepository.getInstance(this@MainActivity)
                .loadThreads(contactsRepo.getCachedContacts())
        }

        setContent {
            var themeMode by remember { mutableStateOf(prefs.getThemeMode()) }
            val hasCachedData = remember { contactsRepo.getCachedContacts().isNotEmpty() || callLogRepo.getCachedCallLogs().isNotEmpty() }
            var isAppLoading by remember { mutableStateOf(!hasCachedData) }

            val contacts by contactsRepo.getAllContacts().collectAsState(initial = contactsRepo.getCachedContacts())
            val favorites = remember(contacts) { contacts.filter { it.isFavorite } }
            val callLogs by callLogRepo.getAllCallLogs().collectAsState(initial = callLogRepo.getCachedCallLogs())
            val speedDials by callLogRepo.getSpeedDials().collectAsState(initial = emptyList())
            val targetTab by targetTabState
            val targetChatAddress by targetChatAddressState

            // Ultra-fast loading: dismiss splash as soon as initial cache or first batch is available
            LaunchedEffect(contacts, callLogs) {
                if (contacts.isNotEmpty() || callLogs.isNotEmpty()) {
                    isAppLoading = false
                }
            }

            // Fallback safety timeout so app always opens within 350ms
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(350)
                isAppLoading = false
            }

            PhoneDialerTheme(themeMode = themeMode) {
                Crossfade(targetState = isAppLoading, label = "AppLoadingTransition") { loading ->
                    if (loading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp)
                            ) {
                                Spacer(Modifier.weight(1f))

                                Image(
                                    painter = painterResource(id = R.drawable.ic_retro_phone),
                                    contentDescription = "App Icon",
                                    modifier = Modifier
                                        .size(110.dp)
                                        .clip(RoundedCornerShape(26.dp))
                                )

                                Spacer(Modifier.height(20.dp))

                                Text(
                                    text = "Phone Dialer",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 26.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )

                                Spacer(Modifier.height(30.dp))

                                CircularProgressIndicator(
                                    modifier = Modifier.size(36.dp),
                                    strokeWidth = 3.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Spacer(Modifier.weight(1f))

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(bottom = 32.dp)
                                ) {
                                    Text(
                                        text = "Developed by",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = "Amit Bharat",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    } else {
                        MainScreen(
                            contacts = contacts,
                            favorites = favorites,
                            callLogs = callLogs,
                            speedDials = speedDials,
                            initialTab = targetTab,
                            initialChatAddress = targetChatAddress,
                            onCallClick = { number, sim ->
                                TelecomHelper.makeCall(this@MainActivity, number, sim)
                            },
                            onAddContact = { contact ->
                                lifecycleScope.launch { contactsRepo.addContact(contact) }
                            },
                            onToggleFavorite = { contact ->
                                lifecycleScope.launch {
                                    contactsRepo.updateContact(contact.copy(isFavorite = !contact.isFavorite))
                                }
                            },
                            onDeleteContact = { contact ->
                                lifecycleScope.launch { contactsRepo.deleteContact(contact) }
                            },
                            onDeleteCallLog = { id ->
                                lifecycleScope.launch { callLogRepo.deleteCallLog(id) }
                            },
                            onSyncDeviceContacts = {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    contactsRepo.syncDeviceContacts()
                                    callLogRepo.syncDeviceCallLogs()
                                    com.amitbharat.phonedialer.repository.SmsRepository.getInstance(this@MainActivity)
                                        .loadThreads(contactsRepo.getCachedContacts(), forceRefresh = true)
                                }
                            },
                            onThemeChange = { mode -> themeMode = mode },
                            onLoadMoreCallLogs = {
                                lifecycleScope.launch { callLogRepo.loadMoreCallLogs(60) }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val tabExtra = intent.getStringExtra(EXTRA_OPEN_TAB)
        val chatAddressExtra = intent.getStringExtra(EXTRA_CHAT_ADDRESS)

        if (tabExtra == "MESSAGES" || intent.action == Intent.ACTION_SENDTO || intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_SEND) {
            targetTabState.value = com.amitbharat.phonedialer.ui.main.MainTab.MESSAGES
            val scheme = intent.data?.scheme
            if (scheme == "sms" || scheme == "smsto" || scheme == "mms" || scheme == "mmsto") {
                val ssp = intent.data?.schemeSpecificPart?.substringBefore("?") ?: ""
                if (ssp.isNotBlank()) {
                    targetChatAddressState.value = ssp
                }
            } else if (!chatAddressExtra.isNullOrBlank()) {
                targetChatAddressState.value = chatAddressExtra
            }
        }
    }

    private val contentObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
            autoSyncAllData()
        }
    }

    private var lastAutoSyncTime = 0L
    private fun autoSyncAllData(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && (now - lastAutoSyncTime < 4000)) return // debounce rapid content observer updates
        lastAutoSyncTime = now
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                    val freshContacts = contactsRepo.fetchDeviceContactsDirectly()
                    if (freshContacts.isNotEmpty()) {
                        ContactsRepository.updateCachedContacts(freshContacts)
                    }
                }
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
                    val freshLogs = callLogRepo.fetchDeviceCallLogsDirectly()
                    if (freshLogs.isNotEmpty()) {
                        CallLogRepository.updateCachedCallLogs(freshLogs)
                    }
                }
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
                    com.amitbharat.phonedialer.repository.SmsRepository.getInstance(this@MainActivity)
                        .loadThreads(contactsRepo.getCachedContacts(), forceRefresh = true)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkDefaultDialerRole()
        autoSyncAllData()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            contentResolver.unregisterContentObserver(contentObserver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private var hasCheckedDefaultDialer = false

    private fun checkDefaultDialerRole() {
        if (hasCheckedDefaultDialer) return
        hasCheckedDefaultDialer = true
        if (!TelecomHelper.isDefaultDialer(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = getSystemService(Context.ROLE_SERVICE) as? RoleManager
                roleManager?.createRequestRoleIntent(RoleManager.ROLE_DIALER)?.let {
                    defaultDialerLauncher.launch(it)
                }
            } else {
                val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                    putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
                }
                defaultDialerLauncher.launch(intent)
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            permissions.add(Manifest.permission.READ_CALL_LOG)
            permissions.add(Manifest.permission.WRITE_CALL_LOG)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        } else {
            autoSyncAllData(force = true)
            checkDefaultDialerRole()
        }

        try {
            contentResolver.registerContentObserver(android.provider.ContactsContract.Contacts.CONTENT_URI, true, contentObserver)
            contentResolver.registerContentObserver(android.provider.CallLog.Calls.CONTENT_URI, true, contentObserver)
            contentResolver.registerContentObserver(android.provider.Telephony.Sms.CONTENT_URI, true, contentObserver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
