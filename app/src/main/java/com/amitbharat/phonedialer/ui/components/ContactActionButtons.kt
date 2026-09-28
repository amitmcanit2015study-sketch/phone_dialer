package com.amitbharat.phonedialer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Message
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.amitbharat.phonedialer.R
import com.amitbharat.phonedialer.utils.WhatsAppHelper

@Composable
fun ContactActionButtons(
    number: String,
    contactId: Long? = null,
    contactNumbers: List<String> = emptyList(),
    onMessageClick: () -> Unit,
    onCallClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    // Pure icon tints without background
    val waTint = if (isDark) Color(0xFF25D366) else Color(0xFF16A34A)
    val msgTint = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
    val callTint = if (isDark) Color(0xFF34D399) else Color(0xFF16A34A)

    val isWa = if (contactId != null && contactNumbers.isNotEmpty()) {
        WhatsAppHelper.isWhatsAppLinked(contactId, contactNumbers)
    } else {
        WhatsAppHelper.isWhatsAppLinked(number = number)
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isWa && number.isNotBlank()) {
            IconButton(
                onClick = {
                    WhatsAppHelper.openWhatsAppChat(context, number)
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_whatsapp),
                    contentDescription = "WhatsApp",
                    tint = waTint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        IconButton(
            onClick = onMessageClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.Default.Message,
                contentDescription = "Message",
                tint = msgTint,
                modifier = Modifier.size(20.dp)
            )
        }

        IconButton(
            onClick = onCallClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.Default.Call,
                contentDescription = "Call",
                tint = callTint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
