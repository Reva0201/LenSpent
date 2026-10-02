package org.umn.lensspent.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.umn.lensspent.data.local.entity.CaptureEntity
import org.umn.lensspent.data.local.entity.CategoryEntity
import org.umn.lensspent.data.local.entity.TransactionEntity

/**
 * The app's only source of truth. Everything runs offline against this file; nothing here depends on
 * the network, which is what lets the capture -> confirm -> save loop work in airplane mode.
 *
 * Schemas are exported to `app/schemas` so every future version bump is reviewable.
 */
@Database(
    entities = [
        CategoryEntity::class,
        TransactionEntity::class,
        CaptureEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class LensSpentDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao

    abstract fun categoryDao(): CategoryDao

    abstract fun captureDao(): CaptureDao

    companion object {
        const val Name = "lensspent.db"
    }
}
