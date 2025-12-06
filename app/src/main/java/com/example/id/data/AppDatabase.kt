package com.example.id.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.id.data.dao.BreakEventDao
import com.example.id.data.dao.LoadingEventDao
import com.example.id.data.dao.RefuelEventDao
import com.example.id.data.dao.WorkdayEventDao
import com.example.id.data.entities.BreakEvent
import com.example.id.data.entities.LoadingEvent
import com.example.id.data.entities.RefuelEvent
import com.example.id.data.entities.WorkdayEvent

@Database(
    entities = [WorkdayEvent::class, BreakEvent::class, RefuelEvent::class, LoadingEvent::class],
    version = 8,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workdayEventDao(): WorkdayEventDao
    abstract fun breakEventDao(): BreakEventDao
    abstract fun refuelEventDao(): RefuelEventDao
    abstract fun loadingEventDao(): LoadingEventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tfm-database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
