package org.umn.lensspent.data.local

import androidx.room.TypeConverter
import org.umn.lensspent.data.model.CaptureSource
import org.umn.lensspent.data.model.CaptureStatus

/**
 * Room converters for the capture pipeline enums.
 *
 * Stored as names rather than ordinals so reordering an enum can never silently reinterpret old rows.
 */
class Converters {

    @TypeConverter
    fun fromCaptureSource(value: CaptureSource): String = value.name

    @TypeConverter
    fun toCaptureSource(value: String): CaptureSource = CaptureSource.valueOf(value)

    @TypeConverter
    fun fromCaptureStatus(value: CaptureStatus): String = value.name

    @TypeConverter
    fun toCaptureStatus(value: String): CaptureStatus = CaptureStatus.valueOf(value)
}
