package com.amitbharat.phonedialer.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amitbharat.phonedialer.model.Contact
import com.amitbharat.phonedialer.ui.theme.AccentGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditContactScreen(
    initialContact: Contact? = null,
    onBack: () -> Unit,
    onSave: (Contact) -> Unit
) {
    var firstName by remember { mutableStateOf(initialContact?.name?.split(" ")?.firstOrNull() ?: "") }
    var lastName by remember { mutableStateOf(initialContact?.name?.split(" ")?.drop(1)?.joinToString(" ") ?: "") }
    var phonePrimary by remember { mutableStateOf(initialContact?.numbers?.firstOrNull() ?: "") }
    var phoneSecondary by remember { mutableStateOf(initialContact?.numbers?.getOrNull(1) ?: "") }
    var email by remember { mutableStateOf(initialContact?.email ?: "") }
    var company by remember { mutableStateOf(initialContact?.company ?: "") }
    var notes by remember { mutableStateOf(initialContact?.notes ?: "") }
    var isFavorite by remember { mutableStateOf(initialContact?.isFavorite ?: false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (initialContact == null) "Create New Contact" else "Edit Contact",
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            val fullName = (firstName.trim() + " " + lastName.trim()).trim()
                            if (fullName.isNotBlank() && phonePrimary.isNotBlank()) {
                                val numbersList = mutableListOf(phonePrimary.trim())
                                if (phoneSecondary.isNotBlank()) numbersList.add(phoneSecondary.trim())

                                val contactToSave = (initialContact?.copy(
                                    name = fullName,
                                    numbers = numbersList.distinct(),
                                    email = email.ifBlank { null },
                                    company = company.ifBlank { null },
                                    notes = notes.ifBlank { null },
                                    isFavorite = isFavorite
                                ) ?: Contact(
                                    name = fullName,
                                    numbers = numbersList.distinct(),
                                    email = email.ifBlank { null },
                                    company = company.ifBlank { null },
                                    notes = notes.ifBlank { null },
                                    isFavorite = isFavorite
                                ))
                                onSave(contactToSave)
                            }
                        },
                        enabled = firstName.isNotBlank() || phonePrimary.isNotBlank()
                    ) {
                        Text(
                            "Save",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = if (firstName.isNotBlank() || phonePrimary.isNotBlank()) AccentGreen else Color.Gray
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Big Contact Icon Placeholder
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(54.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(Modifier.height(4.dp))

            // First Name
            OutlinedTextField(
                value = firstName,
                onValueChange = { firstName = it },
                label = { Text("First Name *") },
                leadingIcon = { Icon(Icons.Default.PersonOutline, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Last Name
            OutlinedTextField(
                value = lastName,
                onValueChange = { lastName = it },
                label = { Text("Last Name") },
                leadingIcon = { Icon(Icons.Default.PersonOutline, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Primary Phone
            OutlinedTextField(
                value = phonePrimary,
                onValueChange = { phonePrimary = it },
                label = { Text("Phone Number *") },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Secondary Phone
            OutlinedTextField(
                value = phoneSecondary,
                onValueChange = { phoneSecondary = it },
                label = { Text("Secondary Phone (Optional)") },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Email
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email Address") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Company
            OutlinedTextField(
                value = company,
                onValueChange = { company = it },
                label = { Text("Company / Organization") },
                leadingIcon = { Icon(Icons.Default.Business, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes / Remarks") },
                leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null) },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Favorite switch
            Card(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            tint = if (isFavorite) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Add to Favorites", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                    Switch(checked = isFavorite, onCheckedChange = { isFavorite = it })
                }
            }
        }
    }
}
