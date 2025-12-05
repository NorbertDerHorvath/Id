package com.example.id.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.id.data.dao.BreakEventDao
import com.example.id.data.dao.LoadingEventDao
import com.example.id.data.dao.RefuelEventDao
import com.example.id.data.dao.WorkdayEventDao
import com.example.id.data.entities.BreakEvent
import com.example.id.data.entities.EventType
import com.example.id.data.entities.LoadingEvent
import com.example.id.data.entities.RefuelEvent
import com.example.id.data.entities.WorkdayEvent

@Database(
    entities = [WorkdayEvent::class, BreakEvent::class, RefuelEvent::class, LoadingEvent::class],
    version = 7,
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
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Insert sample data using raw SQL to avoid issues with coroutines and DAO availability during creation
                        db.execSQL("""
                            INSERT INTO workday_events (userId, role, startTime, endTime, startDate, endDate, breakTime, startLocation, startLatitude, startLongitude, endLocation, endLatitude, endLongitude, startOdometer, endOdometer, carPlate, type, isSynced) 
                            VALUES ('norbi', 'driver', ${System.currentTimeMillis() - 3600000}, ${System.currentTimeMillis()}, NULL, NULL, 0, 'Sample Start Location', 47.4979, 19.0402, 'Sample End Location', 47.4979, 19.0402, 12345, 12355, 'ABC-123', 'WORK', 0)
                        """)
                    }
                })
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE refuel_events ADD COLUMN paymentMethod TEXT NOT NULL DEFAULT 'Chip'")
    }
}

val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `absence_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `userId` TEXT NOT NULL, `type` TEXT NOT NULL, `startDate` INTEGER NOT NULL, `endDate` INTEGER NOT NULL)")
    }
}

val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workday_events ADD COLUMN breakTime INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE workday_events ADD COLUMN type TEXT NOT NULL DEFAULT 'WORK'")
    }
}

val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workday_events ADD COLUMN startDate INTEGER")
        db.execSQL("ALTER TABLE workday_events ADD COLUMN endDate INTEGER")
        db.execSQL("DROP TABLE IF EXISTS absence_events")
    }
}

val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE refuel_events ADD COLUMN latitude REAL")
        db.execSQL("ALTER TABLE refuel_events ADD COLUMN longitude REAL")
        db.execSQL("ALTER TABLE refuel_events ADD COLUMN location TEXT")
    }
}

val MIGRATION_6_7: Migration = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workday_events ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE refuel_events ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE loading_events ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 0")
    }
}
