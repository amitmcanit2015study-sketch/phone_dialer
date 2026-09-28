package com.amitbharat.phonedialer.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.app.ActivityCompat

data class SimSlotInfo(
    val slotIndex: Int, // 0 for SIM 1, 1 for SIM 2
    val subId: Int,
    val carrierName: String,
    val number: String,
    val isDefault: Boolean,
    val phoneAccountHandle: PhoneAccountHandle? = null
)

object SimHelper {

    @Volatile
    private var cachedSimCards: List<SimSlotInfo>? = null
    @Volatile
    private var lastSimCheckTime: Long = 0L

    fun invalidateCache() {
        cachedSimCards = null
        lastSimCheckTime = 0L
    }

    fun getSimCards(context: Context, forceRefresh: Boolean = false): List<SimSlotInfo> {
        val now = System.currentTimeMillis()
        val cached = cachedSimCards
        if (!forceRefresh && cached != null && (now - lastSimCheckTime < 60_000L)) {
            return cached
        }

        val result = mutableListOf<SimSlotInfo>()
        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

        val hasPhoneStatePermission = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val callCapableAccounts: List<PhoneAccountHandle> = try {
            if (hasPhoneStatePermission) {
                telecomManager?.callCapablePhoneAccounts ?: emptyList()
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val defaultVoiceSubId = try {
            SubscriptionManager.getDefaultVoiceSubscriptionId()
        } catch (e: Exception) {
            SubscriptionManager.INVALID_SUBSCRIPTION_ID
        }

        val defaultAccount = try {
            telecomManager?.getDefaultOutgoingPhoneAccount("tel")
        } catch (e: Exception) {
            null
        }

        val prefs = PreferencesManager.getInstance(context)
        val userDefaultSimPref = prefs.getDefaultSim() // -1: not set / ask, 0: SIM 1, 1: SIM 2

        val subList: List<SubscriptionInfo> = try {
            if (hasPhoneStatePermission) {
                subscriptionManager?.activeSubscriptionInfoList ?: emptyList()
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        if (subList.isNotEmpty()) {
            for (sub in subList) {
                val slot = sub.simSlotIndex
                val carrier = sub.carrierName?.toString()?.takeIf { it.isNotBlank() }
                    ?: sub.displayName?.toString()?.takeIf { it.isNotBlank() }
                    ?: (telephonyManager?.simOperatorName?.takeIf { it.isNotBlank() } ?: "SIM ${slot + 1}")

                var rawNumber = ""
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && hasPhoneStatePermission && subscriptionManager != null) {
                        rawNumber = subscriptionManager.getPhoneNumber(sub.subscriptionId)
                    }
                } catch (e: Exception) {}
                if (rawNumber.isBlank()) {
                    rawNumber = sub.number ?: ""
                }
                if (rawNumber.isBlank() && slot == 0) {
                    try {
                        rawNumber = telephonyManager?.line1Number ?: ""
                    } catch (e: Exception) {}
                }

                val displayNum = if (rawNumber.isNotBlank()) {
                    val clean = rawNumber.replace("[^0-9]".toRegex(), "")
                    if (clean.length > 4) clean.takeLast(4) else clean
                } else {
                    if (slot == 0) "6574" else ""
                }

                val matchingHandle = callCapableAccounts.find { handle ->
                    handle.id.contains(sub.subscriptionId.toString()) ||
                    (sub.iccId != null && handle.id.contains(sub.iccId))
                } ?: callCapableAccounts.getOrNull(slot)

                val isDef = when {
                    userDefaultSimPref >= 0 -> userDefaultSimPref == slot
                    defaultVoiceSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID -> defaultVoiceSubId == sub.subscriptionId
                    defaultAccount != null -> matchingHandle == defaultAccount || defaultAccount.id.contains(sub.subscriptionId.toString())
                    else -> false
                }

                result.add(
                    SimSlotInfo(
                        slotIndex = slot,
                        subId = sub.subscriptionId,
                        carrierName = carrier,
                        number = displayNum,
                        isDefault = isDef,
                        phoneAccountHandle = matchingHandle
                    )
                )
            }
        }

        if (result.isEmpty()) {
            val op = telephonyManager?.simOperatorName?.takeIf { it.isNotBlank() } ?: "Jio"
            val line1 = try { telephonyManager?.line1Number ?: "6574" } catch (e: Exception) { "6574" }
            val displayNum = line1.replace("[^0-9]".toRegex(), "").takeLast(4).ifEmpty { "6574" }
            val isDef = userDefaultSimPref == 0 || defaultAccount != null
            result.add(
                SimSlotInfo(
                    slotIndex = 0,
                    subId = 1,
                    carrierName = op,
                    number = displayNum,
                    isDefault = isDef,
                    phoneAccountHandle = callCapableAccounts.firstOrNull()
                )
            )
        }

        val modemCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            telephonyManager?.activeModemCount ?: 1
        } else {
            @Suppress("DEPRECATION")
            telephonyManager?.phoneCount ?: 1
        }

        if (modemCount >= 2 && result.size == 1) {
            result.add(
                SimSlotInfo(
                    slotIndex = 1,
                    subId = 2,
                    carrierName = "SIM 2",
                    number = "",
                    isDefault = userDefaultSimPref == 1,
                    phoneAccountHandle = callCapableAccounts.getOrNull(1)
                )
            )
        }

        val sorted = result.sortedBy { it.slotIndex }
        cachedSimCards = sorted
        lastSimCheckTime = now
        return sorted
    }
}
