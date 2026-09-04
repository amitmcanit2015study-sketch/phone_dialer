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
import androidx.lifecycle.lifecycleScope
import com.amitbharat.phonedialer.model.CallLogItem
import com.amitbharat.phonedialer.model.Contact
import com.amitbharat.phonedialer.repository.CallLogRepository
import com.amitbharat.phonedialer.repository.ContactsRepository
import com.amitbharat.phonedialer.telecom.TelecomHelper
import com.amitbharat.phonedialer.ui.theme.PhoneDialerTheme
import com.amitbharat.phonedialer.utils.PreferencesManager
import com.amitbharat.phonedialer.utils.ThemeMode
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var contactsRepo: ContactsRepository
    private lateinit var callLogRepo: CallLogRepository
    private lateinit var prefs: PreferencesManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.READ_CONTACTS] == true) {
            lifecycleScope.launch { contactsRepo.syncDeviceContacts() }
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

        requestRequiredPermissions()

        setContent {
            var themeMode by remember { mutableStateOf(prefs.getThemeMode()) }

            val contacts by contactsRepo.getAllContacts().collectAsState(initial = emptyList())
            val favorites by contactsRepo.getFavoriteContacts().collectAsState(initial = emptyList())
            val callLogs by callLogRepo.getAllCallLogs().collectAsState(initial = emptyList())
            val speedDials by callLogRepo.getSpeedDials().collectAsState(initial = emptyList())

            PhoneDialerTheme(themeMode = themeMode) {
                MainScreen(
                    contacts = contacts,
                    favorites = favorites,
                    callLogs = callLogs,
                    speedDials = speedDials,
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
                        lifecycleScope.launch {
                            contactsRepo.syncDeviceContacts()
                            callLogRepo.syncDeviceCallLogs()
                        }
                    },
                    onThemeChange = { mode -> themeMode = mode }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            contactsRepo.syncDeviceContacts()
            callLogRepo.syncDeviceCallLogs()
        }
    }

    private fun checkDefaultDialerRole() {
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
            lifecycleScope.launch {
                contactsRepo.syncDeviceContacts()
                callLogRepo.syncDeviceCallLogs()
            }
            checkDefaultDialerRole()
        }
    }
}
