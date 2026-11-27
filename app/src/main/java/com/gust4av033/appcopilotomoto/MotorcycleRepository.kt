package com.gust4av033.appcopilotomoto

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import org.json.JSONObject

data class MotorcycleProfile(val name: String, val model: String, val odometerMeters: Long?, val photoName: String?, val catalogId: String? = null, val specificationOverrides: Map<String, String> = emptyMap())
data class MotorcycleDraft(val name: String, val model: String, val odometerKm: String, val catalogId: String? = null, val specificationOverrides: Map<String, String> = emptyMap())
data class MaintenanceRecord(
    val id: String, val title: String, val date: LocalDate, val odometerMeters: Long?,
    val notes: String, val nextDate: LocalDate?, val nextOdometerMeters: Long?
) {
    fun isDue(today: LocalDate, manualOdometerMeters: Long?): Boolean =
        nextDate?.let { !today.isBefore(it) } == true ||
            (nextOdometerMeters != null && manualOdometerMeters != null && manualOdometerMeters >= nextOdometerMeters)
}
data class MaintenanceDraft(
    val id: String? = null, val title: String, val date: String, val odometerKm: String,
    val notes: String, val nextDate: String, val nextOdometerKm: String
)
data class MotorcycleData(val profile: MotorcycleProfile? = null, val maintenance: List<MaintenanceRecord> = emptyList())

/** Kilometres entered by the rider, never derived from a GPS trip. No grouping separators. */
fun parseManualKilometers(value: String): Long? {
    val text = value.trim()
    if (!Regex("[0-9]{1,7}([.,][0-9]{1,3})?").matches(text)) return null
    return runCatching { BigDecimal(text.replace(',', '.')).movePointRight(3).longValueExact() }.getOrNull()
}

fun manualKilometers(meters: Long?): String = meters?.let {
    BigDecimal.valueOf(it, 3).stripTrailingZeros().toPlainString()
}.orEmpty()

fun MotorcycleDraft.isValid() = validMotorcycleOverrides(specificationOverrides) && name.trim().isNotEmpty() && name.trim().length <= 60 &&
    (catalogId == null || MotorcycleCatalog.find(catalogId) != null) &&
    model.trim().length <= 100 && (odometerKm.isBlank() || parseManualKilometers(odometerKm) != null)

fun MaintenanceDraft.isValid(today: LocalDate): Boolean {
    val performed = runCatching { LocalDate.parse(date.trim()) }.getOrNull() ?: return false
    val next = if (nextDate.isBlank()) null else runCatching { LocalDate.parse(nextDate.trim()) }.getOrNull() ?: return false
    val km = if (odometerKm.isBlank()) null else parseManualKilometers(odometerKm) ?: return false
    val dueKm = if (nextOdometerKm.isBlank()) null else parseManualKilometers(nextOdometerKm) ?: return false
    return title.trim().isNotEmpty() && title.trim().length <= 80 && notes.trim().length <= 600 &&
        !performed.isAfter(today) && (next == null || !next.isBefore(performed)) &&
        (km == null || dueKm == null || dueKm > km)
}

/** Local SQLite store. UI calls it on a dedicated worker; it has no audio or location side effects. */
class MotorcycleRepository(context: Context) : AutoCloseable {
    // Preserve the rider's file on corruption so a read failure cannot silently reset the profile.
    private val helper = object : SQLiteOpenHelper(context.applicationContext, "motorcycles.db", null, 3,
        DatabaseErrorHandler { }) {
        override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""CREATE TABLE motorcycles (
                id INTEGER PRIMARY KEY CHECK(id = 1), user_id TEXT NOT NULL,
                name TEXT NOT NULL, model TEXT NOT NULL, odometer_meters INTEGER, photo_name TEXT, catalog_id TEXT, specification_overrides TEXT)""")
            db.execSQL("""CREATE TABLE maintenance (
                id TEXT PRIMARY KEY, motorcycle_id INTEGER NOT NULL REFERENCES motorcycles(id) ON DELETE CASCADE,
                title TEXT NOT NULL, performed_date TEXT NOT NULL, odometer_meters INTEGER,
                notes TEXT NOT NULL, next_date TEXT, next_odometer_meters INTEGER)""")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) db.execSQL("ALTER TABLE motorcycles ADD COLUMN catalog_id TEXT")
            if (oldVersion < 3) db.execSQL("ALTER TABLE motorcycles ADD COLUMN specification_overrides TEXT")
        }
    }

    fun load(): MotorcycleData {
        val db = helper.readableDatabase
        val profile = db.rawQuery("SELECT name, model, odometer_meters, photo_name, catalog_id, specification_overrides FROM motorcycles WHERE id=1", null).use {
            if (it.moveToFirst()) MotorcycleProfile(it.getString(0), it.getString(1),
                if (it.isNull(2)) null else it.getLong(2), it.getString(3), it.getString(4), decodeSpecifications(it.getString(5))) else null
        }
        val records = db.rawQuery("SELECT id, title, performed_date, odometer_meters, notes, next_date, next_odometer_meters FROM maintenance WHERE motorcycle_id=1 ORDER BY performed_date DESC, rowid DESC", null).use {
            buildList {
                while (it.moveToNext()) add(MaintenanceRecord(it.getString(0), it.getString(1), LocalDate.parse(it.getString(2)),
                    if (it.isNull(3)) null else it.getLong(3), it.getString(4),
                    it.getString(5)?.let(LocalDate::parse), if (it.isNull(6)) null else it.getLong(6)))
            }
        }
        return MotorcycleData(profile, records)
    }

    private fun decodeSpecifications(json: String?): Map<String, String> {
        if (json == null) return emptyMap()
        val objectValue = JSONObject(json)
        return objectValue.keys().asSequence().associateWith { objectValue.getString(it) }
            .also { check(validMotorcycleOverrides(it)) }
    }

    fun saveProfile(draft: MotorcycleDraft) {
        require(draft.isValid())
        val db = helper.writableDatabase
        val values = ContentValues().apply {
            put("name", draft.name.trim()); put("model", MotorcycleCatalog.find(draft.catalogId)?.model ?: draft.model.trim())
            put("catalog_id", draft.catalogId)
            put("specification_overrides", JSONObject(draft.specificationOverrides).toString())
            put("odometer_meters", draft.odometerKm.takeIf(String::isNotBlank)?.let(::parseManualKilometers))
        }
        db.beginTransaction()
        try {
            if (db.update("motorcycles", values, "id=1", null) == 0) {
                values.put("id", 1); values.put("user_id", "local")
                db.insertOrThrow("motorcycles", null, values)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun setPhoto(name: String?) {
        require(name == null || Regex("moto_[a-f0-9-]+\\.jpg").matches(name))
        val values = ContentValues().apply { put("photo_name", name) }
        check(helper.writableDatabase.update("motorcycles", values, "id=1", null) == 1)
    }

    fun saveMaintenance(draft: MaintenanceDraft, today: LocalDate = LocalDate.now()) {
        require(draft.isValid(today))
        val db = helper.writableDatabase
        val values = ContentValues().apply {
            put("motorcycle_id", 1); put("title", draft.title.trim()); put("performed_date", draft.date.trim())
            put("odometer_meters", draft.odometerKm.takeIf(String::isNotBlank)?.let(::parseManualKilometers))
            put("notes", draft.notes.trim()); put("next_date", draft.nextDate.trim().takeIf(String::isNotEmpty))
            put("next_odometer_meters", draft.nextOdometerKm.takeIf(String::isNotBlank)?.let(::parseManualKilometers))
        }
        if (draft.id == null) {
            values.put("id", UUID.randomUUID().toString())
            db.insertOrThrow("maintenance", null, values)
        } else check(db.update("maintenance", values, "id=? AND motorcycle_id=1", arrayOf(draft.id)) == 1)
    }

    fun deleteMaintenance(id: String) {
        check(helper.writableDatabase.delete("maintenance", "id=? AND motorcycle_id=1", arrayOf(id)) == 1)
    }

    override fun close() = helper.close()
}
