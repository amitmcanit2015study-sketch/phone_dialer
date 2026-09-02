package com.amitbharat.phonedialer.repository

import android.content.ContentResolver
import android.content.Context
import android.provider.ContactsContract
import com.amitbharat.phonedialer.database.AppDatabase
import com.amitbharat.phonedialer.database.entity.ContactEntity
import com.amitbharat.phonedialer.model.Contact
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ContactsRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val contactDao = db.contactDao()
    private val gson = Gson()
    private val type = object : TypeToken<List<String>>() {}.type

    fun getAllContacts(): Flow<List<Contact>> = flow {
        // 1. Emit live contacts directly from device ContentResolver immediately
        val deviceContacts = fetchDeviceContactsDirectly()
        emit(deviceContacts)

        // 2. Also observe database updates
        contactDao.getAllContacts().map { list ->
            if (list.isNotEmpty()) list.map { it.toModel(gson, type) } else deviceContacts
        }.collect {
            emit(it)
        }
    }.flowOn(Dispatchers.IO)

    fun getFavoriteContacts(): Flow<List<Contact>> = flow {
        val deviceContacts = fetchDeviceContactsDirectly()
        val favorites = deviceContacts.filter { it.isFavorite }
        emit(favorites)

        contactDao.getFavoriteContacts().map { list ->
            if (list.isNotEmpty()) list.map { it.toModel(gson, type) } else favorites
        }.collect {
            emit(it)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun addContact(contact: Contact): Long {
        val entity = ContactEntity(
            name = contact.name,
            numbersJson = gson.toJson(contact.numbers),
            photoUri = contact.photoUri,
            email = contact.email,
            isFavorite = contact.isFavorite,
            notes = contact.notes,
            company = contact.company
        )
        return contactDao.insertContact(entity)
    }

    suspend fun updateContact(contact: Contact) {
        val entity = ContactEntity(
            id = contact.id,
            name = contact.name,
            numbersJson = gson.toJson(contact.numbers),
            photoUri = contact.photoUri,
            email = contact.email,
            isFavorite = contact.isFavorite,
            notes = contact.notes,
            company = contact.company
        )
        contactDao.insertContact(entity)
    }

    suspend fun deleteContact(contact: Contact) {
        val entity = ContactEntity(
            id = contact.id,
            name = contact.name,
            numbersJson = gson.toJson(contact.numbers),
            photoUri = contact.photoUri,
            email = contact.email,
            isFavorite = contact.isFavorite
        )
        contactDao.deleteContact(entity)
    }

    fun fetchDeviceContactsDirectly(): List<Contact> {
        val result = mutableListOf<Contact>()
        try {
            val resolver = context.contentResolver
            val cursor = resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                    ContactsContract.CommonDataKinds.Phone.STARRED
                ),
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC"
            )

            cursor?.use {
                val contactMap = LinkedHashMap<String, MutableList<String>>()
                val photoMap = HashMap<String, String?>()
                val starMap = HashMap<String, Boolean>()
                val idMap = HashMap<String, Long>()

                val idIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                val starIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.STARRED)

                while (it.moveToNext()) {
                    val rawName = if (nameIdx >= 0) it.getString(nameIdx) else null
                    val name = if (!rawName.isNullOrBlank()) rawName else "Unknown"
                    val number = if (numIdx >= 0) it.getString(numIdx)?.trim() ?: "" else ""
                    val photo = if (photoIdx >= 0) it.getString(photoIdx) else null
                    val isStarred = if (starIdx >= 0) it.getInt(starIdx) == 1 else false
                    val contactId = if (idIdx >= 0) it.getLong(idIdx) else 0L

                    if (number.isNotBlank()) {
                        contactMap.getOrPut(name) { mutableListOf() }.add(number)
                        if (photo != null) photoMap[name] = photo
                        if (isStarred) starMap[name] = true
                        idMap[name] = contactId
                    }
                }

                contactMap.forEach { (name, numbers) ->
                    result.add(
                        Contact(
                            id = idMap[name] ?: 0L,
                            name = name,
                            numbers = numbers.distinct(),
                            photoUri = photoMap[name],
                            isFavorite = starMap[name] ?: false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    suspend fun syncDeviceContacts() = withContext(Dispatchers.IO) {
        val contacts = fetchDeviceContactsDirectly()
        contacts.forEach { c ->
            val entity = ContactEntity(
                name = c.name,
                numbersJson = gson.toJson(c.numbers),
                photoUri = c.photoUri,
                isFavorite = c.isFavorite
            )
            contactDao.insertContact(entity)
        }
    }

    private fun ContactEntity.toModel(gson: Gson, type: java.lang.reflect.Type): Contact {
        val numbers: List<String> = try {
            gson.fromJson(numbersJson, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        return Contact(
            id = id,
            name = name,
            numbers = numbers,
            photoUri = photoUri,
            email = email,
            isFavorite = isFavorite,
            notes = notes,
            company = company
        )
    }
}
