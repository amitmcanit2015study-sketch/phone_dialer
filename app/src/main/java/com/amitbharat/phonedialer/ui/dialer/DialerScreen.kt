package com.amitbharat.phonedialer.ui.dialer

import android.content.Context
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amitbharat.phonedialer.model.Contact
import com.amitbharat.phonedialer.model.SpeedDialItem
import com.amitbharat.phonedialer.search.T9SearchEngine
import com.amitbharat.phonedialer.telecom.TelecomHelper
import com.amitbharat.phonedialer.ui.theme.AccentGreen
import com.amitbharat.phonedialer.utils.PreferencesManager

@Composable
fun DialerScreen(
    allContacts: List<Contact>,
    speedDials: List<SpeedDialItem>,
    onCallClick: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager.getInstance(context) }
    var enteredNumber by remember { mutableStateOf("") }

    val matchedContacts = remember(enteredNumber, allContacts) {
        if (enteredNumber.isEmpty()) emptyList()
        else T9SearchEngine.search(enteredNumber, allContacts)
    }

    fun handleKeyPress(char: String) {
        if (prefs.isVibrationEnabled()) {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
        }
        enteredNumber += char
    }

    fun handleBackspace() {
        if (enteredNumber.isNotEmpty()) {
            enteredNumber = enteredNumber.dropLast(1)
        }
    }

    fun handleLongPressDigit(digit: Int) {
        val item = speedDials.find { it.digit == digit }
        if (item != null && item.number.isNotBlank()) {
            onCallClick(item.number, 0)
        } else if (digit == 0) {
            handleKeyPress("+")
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // T9 Matching Contacts Preview Header
        if (matchedContacts.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(8.dp)
            ) {
                items(matchedContacts) { contact ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val num = contact.numbers.firstOrNull() ?: enteredNumber
                                onCallClick(num, 0)
                            }
                            .padding(vertical = 8.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = contact.name.take(1).uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = contact.name,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = contact.numbers.firstOrNull() ?: "",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            val num = contact.numbers.firstOrNull() ?: enteredNumber
                            onCallClick(num, 0)
                        }) {
                            Icon(Icons.Default.Call, contentDescription = "Call", tint = AccentGreen)
                        }
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        // Display Number Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = enteredNumber.ifEmpty { "" },
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            if (enteredNumber.isNotEmpty()) {
                IconButton(
                    onClick = { handleBackspace() },
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Backspace,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // 3x4 Dialpad Keypad
        val keys = listOf(
            Triple("1", "", 1),
            Triple("2", "ABC", 2),
            Triple("3", "DEF", 3),
            Triple("4", "GHI", 4),
            Triple("5", "JKL", 5),
            Triple("6", "MNO", 6),
            Triple("7", "PQRS", 7),
            Triple("8", "TUV", 8),
            Triple("9", "WXYZ", 9),
            Triple("*", "", 0),
            Triple("0", "+", 0),
            Triple("#", "", 0)
        )

        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            keys.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { (digit, sub, speedIndex) ->
                        DialKey(
                            digit = digit,
                            sub = sub,
                            onClick = { handleKeyPress(digit) },
                            onLongClick = { if (speedIndex > 0 || digit == "0") handleLongPressDigit(speedIndex) }
                        )
                    }
                }
            }
        }

        // Dual-SIM Call Buttons Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    if (enteredNumber.isNotBlank()) onCallClick(enteredNumber, 0)
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .height(56.dp)
                    .fillMaxWidth(0.85f)
            ) {
                Icon(Icons.Default.Call, contentDescription = "Call", tint = Color.White, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "CALL",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun DialKey(
    digit: String,
    sub: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 92.dp, height = 62.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = digit,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (sub.isNotEmpty()) {
                Text(
                    text = sub,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
