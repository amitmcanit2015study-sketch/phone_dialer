package com.amitbharat.phonedialer.ui.favorites

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amitbharat.phonedialer.model.Contact
import com.amitbharat.phonedialer.ui.theme.AccentGreen
import com.amitbharat.phonedialer.utils.ContactAvatar

@Composable
fun FavoritesScreen(
    favorites: List<Contact>,
    onCallClick: (String) -> Unit,
    onMessageClick: (String) -> Unit = {},
    onContactClick: (name: String, number: String, photoUri: String?, contact: Contact) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Starred Favorites, 1: Archived

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp)) {
        // Tab Row
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.background,
            divider = {}
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Starred Favorites (${favorites.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Archived (0)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            )
        }

        Spacer(Modifier.height(8.dp))

        if (selectedTab == 1) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No archived favorites", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
            }
        } else if (favorites.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(Icons.Default.StarBorder, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Text("No favorite contacts yet", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text("Star contacts in the Contacts tab to access them quickly here.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp)
            ) {
                items(favorites, key = { it.id.toString() + "_" + it.name }) { contact ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ContactAvatar(name = contact.name, photoUri = contact.photoUri, size = 52.dp, fontSize = 20.sp)
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = contact.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = contact.numbers.firstOrNull() ?: "",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // Sequence from the last: Call, Message, WhatsApp (Left-to-right: WhatsApp -> Message -> Call)
                            val isWa = com.amitbharat.phonedialer.utils.WhatsAppHelper.isWhatsAppLinked(contact.id, contact.numbers)
                            val num = contact.numbers.firstOrNull() ?: ""

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isWa && num.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            com.amitbharat.phonedialer.utils.WhatsAppHelper.openWhatsAppChat(context, num)
                                        },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Color(0xFFE8F8EE), CircleShape)
                                    ) {
                                        Icon(
                                            painter = androidx.compose.ui.res.painterResource(id = com.amitbharat.phonedialer.R.drawable.ic_whatsapp),
                                            contentDescription = "WhatsApp",
                                            tint = Color(0xFF25D366),
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { onMessageClick(num) },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFFE0F2FE), CircleShape)
                                ) {
                                    Icon(
                                        Icons.Default.Message,
                                        contentDescription = "Message",
                                        tint = Color(0xFF0284C7),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { onCallClick(num) },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFFD1FAE5), CircleShape)
                                ) {
                                    Icon(Icons.Default.Call, contentDescription = "Call", tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
