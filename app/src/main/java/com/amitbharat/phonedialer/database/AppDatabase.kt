package com.amitbharat.phonedialer.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.amitbharat.phonedialer.database.dao.*
import com.amitbharat.phonedialer.database.entity.*

@Database(
    entities = [
        ContactEntity::class,
        CallLogEntity::class,
        BlockedNumberEntity::class,
        SpeedDialEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun contactDao(): ContactDao
    abstract fun callLogDao(): CallLogDao
    abstract fun blockedNumberDao(): BlockedNumberDao
    abstract fun speedDialDao(): SpeedDialDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "phone_dialer.db"
                ).build().also { instance = it }
            }
        }
    }
}
