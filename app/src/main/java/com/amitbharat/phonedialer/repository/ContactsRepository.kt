package com.amitbharat.phonedialer.repository

import android.content.ContentResolver
import android.content.Context
import android.provider.ContactsContract
import com.amitbharat.phonedialer.database.AppDatabase
import com.amitbharat.phonedialer.database.entity.CallLogEntity
import com.amitbharat.phonedialer.database.entity.ContactEntity
import com.amitbharat.phonedialer.database.entity.SpeedDialEntity
import com.amitbharat.phonedialer.model.CallLogItem
import com.amitbharat.phonedialer.model.CallType
import com.amitbharat.phonedialer.model.Contact
import com.amitbharat.phonedialer.model.SpeedDialItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ContactsRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val contactDao = db.contactDao()
    private val gson = Gson()
    private val listType = object : TypeToken<List<String>>() {}.type

    fun getAllContacts(): Flow<List<Contact>> {
        return contactDao.getAllContacts().map { entities ->
            entities.map { it.toModel(gson, listType) }
        }
    }

    fun getFavoriteContacts(): Flow<List<Contact>> {
        return contactDao.getFavoriteContacts().map { entities ->
            entities.map { it.toModel(gson, listType) }
        }
    }

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
        contactDao.updateContact(entity)
    }

    suspend fun deleteContact(contact: Contact) {
        val entity = ContactEntity(
            id = contact.id,
            name = contact.name,
            numbersJson = gson.toJson(contact.numbers)
        )
        contactDao.deleteContact(entity)
    }

    suspend fun syncDeviceContacts() = withContext(Dispatchers.IO) {
        try {
            val contentResolver: ContentResolver = context.contentResolver
            val cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_URI
                ),
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )

            cursor?.use {
                val contactMap = mutableMapOf<String, MutableList<String>>()
                val photoMap = mutableMapOf<String, String?>()

                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

                while (it.moveToNext()) {
                    val name = if (nameIndex >= 0) it.getString(nameIndex) ?: "Unknown" else "Unknown"
                    val number = if (numberIndex >= 0) it.getString(numberIndex) ?: "" else ""
                    val photo = if (photoIndex >= 0) it.getString(photoIndex) else null

                    if (number.isNotBlank()) {
                        contactMap.getOrPut(name) { mutableListOf() }.add(number)
                        if (photo != null) photoMap[name] = photo
                    }
                }

                contactMap.forEach { (name, numbers) ->
                    val entity = ContactEntity(
                        name = name,
                        numbersJson = gson.toJson(numbers.distinct()),
                        photoUri = photoMap[name]
                    )
                    contactDao.insertContact(entity)
                }
            }
        } catch (ignored: Exception) {}
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
