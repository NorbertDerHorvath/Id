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
import com.example.id.data.entities.LoadingEvent
import com.example.id.data.entities.RefuelEvent
import com.example.id.data.entities.WorkdayEvent

@Database(
    entities = [WorkdayEvent::class, BreakEvent::class, RefuelEvent::class, LoadingEvent::class],
    version = 7, // Verziószám növelve 7-re
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7) // Hozzáadva a MIGRATION_6_7
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// Migráció 1-ről 2-re: Hozzáadja a paymentMethod oszlopot a refuel_events táblához
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE refuel_events ADD COLUMN paymentMethod TEXT NOT NULL DEFAULT 'Chip'")
    }
}

// Migráció 2-ről 3-ra: Hozzáadja az absence_events táblát
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `absence_events` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `userId` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `startDate` INTEGER NOT NULL,
                `endDate` INTEGER NOT NULL
            )
            """)
    }
}

// Migráció 3-ról 4-re: Hozzáadja a breakTime és type oszlopokat a workday_events táblához
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workday_events ADD COLUMN breakTime INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE workday_events ADD COLUMN type TEXT NOT NULL DEFAULT 'WORK'" )
    }
}

// Migráció 4-ről 5-re: Módosítja a workday_events táblát és törli az absence_events táblát
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workday_events ADD COLUMN startDate INTEGER")
        db.execSQL("ALTER TABLE workday_events ADD COLUMN endDate INTEGER")
        db.execSQL("DROP TABLE IF EXISTS absence_events")
    }
}

// Migráció 5-ről 6-ra: Hozzáadja a helyszínnel kapcsolatos oszlopokat a refuel_events táblához
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE refuel_events ADD COLUMN latitude REAL")
        db.execSQL("ALTER TABLE refuel_events ADD COLUMN longitude REAL")
        db.execSQL("ALTER TABLE refuel_events ADD COLUMN location TEXT")
    }
}

// Migráció 6-ról 7-re: Hozzáadja az isSynced oszlopot
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workday_events ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE refuel_events ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE loading_events ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 0")
    }
}
