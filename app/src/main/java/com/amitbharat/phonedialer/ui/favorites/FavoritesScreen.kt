package com.amitbharat.phonedialer.ui.favorites

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amitbharat.phonedialer.model.Contact
import com.amitbharat.phonedialer.ui.theme.AccentGreen

@Composable
fun FavoritesScreen(
    favorites: List<Contact>,
    onCallClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    if (favorites.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Icon(Icons.Default.StarBorder, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text("No favorite contacts yet", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text("Star contacts in the Contacts tab to access them quickly here.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = modifier.fillMaxSize().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(favorites, key = { it.id }) { contact ->
                FavoriteContactCard(
                    contact = contact,
                    onCall = { onCallClick(contact.numbers.firstOrNull() ?: "") },
                    onMessage = {
                        val num = contact.numbers.firstOrNull() ?: ""
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("sms:")))
                    },
                    onWhatsApp = {
                        val num = contact.numbers.firstOrNull() ?: ""
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=")))
                    }
                )
            }
        }
    }
}

@Composable
fun FavoriteContactCard(
    contact: Contact,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onWhatsApp: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contact.name.take(1).uppercase(),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = contact.name,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = contact.numbers.firstOrNull() ?: "",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = onCall, modifier = Modifier.size(38.dp).background(AccentGreen.copy(alpha = 0.15f), CircleShape)) {
                    Icon(Icons.Default.Call, contentDescription = "Call", tint = AccentGreen, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onMessage, modifier = Modifier.size(38.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape)) {
                    Icon(Icons.Default.Message, contentDescription = "SMS", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onWhatsApp, modifier = Modifier.size(38.dp).background(Color(0xFF25D366).copy(alpha = 0.15f), CircleShape)) {
                    Icon(Icons.Default.Share, contentDescription = "WhatsApp", tint = Color(0xFF25D366), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
