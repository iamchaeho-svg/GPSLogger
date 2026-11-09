package kr.go.pyeongtaek.gpslogger

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LocationDb(context: Context) : SQLiteOpenHelper(context, "gps_records.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE records (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                recorded_at INTEGER NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                accuracy REAL,
                altitude REAL,
                speed REAL,
                provider TEXT
            )""".trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun insert(record: LocationRecord) {
        val values = ContentValues().apply {
            put("recorded_at", record.time)
            put("latitude", record.latitude)
            put("longitude", record.longitude)
            put("accuracy", record.accuracy)
            put("altitude", record.altitude)
            put("speed", record.speed)
            put("provider", record.provider)
        }
        writableDatabase.insert("records", null, values)
    }

    fun countRecords(): Long {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM records", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }
    }

    fun latestRecord(): LocationRecord? {
        readableDatabase.rawQuery(
            "SELECT recorded_at, latitude, longitude, accuracy, altitude, speed, provider FROM records ORDER BY id DESC LIMIT 1",
            null
        ).use { c ->
            if (!c.moveToFirst()) return null
            return LocationRecord(
                time = c.getLong(0), latitude = c.getDouble(1), longitude = c.getDouble(2),
                accuracy = if (c.isNull(3)) null else c.getFloat(3),
                altitude = if (c.isNull(4)) null else c.getDouble(4),
                speed = if (c.isNull(5)) null else c.getFloat(5),
                provider = c.getString(6) ?: "GPS"
            )
        }
    }

    fun clearRecords() {
        writableDatabase.delete("records", null, null)
    }

    fun exportCsv(output: OutputStream) {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA)
        output.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("기록시각,위도,경도,정확도(m),고도(m),속도(m/s),위치제공자")
            writer.newLine()
            readableDatabase.rawQuery(
                "SELECT recorded_at, latitude, longitude, accuracy, altitude, speed, provider FROM records ORDER BY id ASC",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    val values = listOf(
                        formatter.format(Date(c.getLong(0))),
                        c.getDouble(1).toString(), c.getDouble(2).toString(),
                        if (c.isNull(3)) "" else c.getFloat(3).toString(),
                        if (c.isNull(4)) "" else c.getDouble(4).toString(),
                        if (c.isNull(5)) "" else c.getFloat(5).toString(),
                        c.getString(6) ?: ""
                    )
                    writer.write(values.joinToString(",") { csvEscape(it) })
                    writer.newLine()
                }
            }
        }
    }

    private fun csvEscape(value: String): String = "\"${value.replace("\"", "\"\"")}\""
}

data class LocationRecord(
    val time: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float?,
    val altitude: Double?,
    val speed: Float?,
    val provider: String?
)
