package com.gust4av033.appcopilotomoto

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK, 35])
class MotorcycleRepositoryTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val today = LocalDate.of(2026, 11, 24)
    @Before fun clean() { context.deleteDatabase("motorcycles.db") }
    private fun draft() = MaintenanceDraft(title = "Aceite", date = "2026-10-24", odometerKm = "12000",
        notes = "Filtro cambiado", nextDate = "2027-01-24", nextOdometerKm = "15000")

    @Test fun migratesVersionOneWithoutLosingTheBikePhotoOrMaintenance() {
        val file = context.getDatabasePath("motorcycles.db").apply { parentFile?.mkdirs() }
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE motorcycles (id INTEGER PRIMARY KEY, user_id TEXT NOT NULL, name TEXT NOT NULL, model TEXT NOT NULL, odometer_meters INTEGER, photo_name TEXT)")
            db.execSQL("CREATE TABLE maintenance (id TEXT PRIMARY KEY, motorcycle_id INTEGER, title TEXT, performed_date TEXT, odometer_meters INTEGER, notes TEXT, next_date TEXT, next_odometer_meters INTEGER)")
            db.execSQL("INSERT INTO motorcycles VALUES (1, 'local', 'Mi moto anterior', 'Modelo escrito', 12345000, 'moto_abc.jpg')")
            db.execSQL("INSERT INTO maintenance VALUES ('service', 1, 'Aceite', '2026-10-24', 12000000, 'Registro conservado', NULL, 15000000)")
            db.version = 1
        }
        MotorcycleRepository(context).use {
            val before = it.load()
            assertNull(before.profile!!.catalogId)
            it.saveProfile(MotorcycleDraft(before.profile.name, "ignored text", "12345", "hero_ignitor_125"))
            val after = it.load()
            assertEquals(before.maintenance, after.maintenance)
            assertEquals(before.profile.photoName, after.profile!!.photoName)
            assertEquals(before.profile.odometerMeters, after.profile.odometerMeters)
        }
        MotorcycleRepository(context).use {
            assertEquals("hero_ignitor_125", it.load().profile!!.catalogId)
            assertEquals("Hero Ignitor 125", it.load().profile!!.model)
            val before = it.load()
            assertTrue(runCatching { it.saveProfile(MotorcycleDraft("Moto", "Unknown", "0", "missing-model")) }.isFailure)
            assertEquals(before, it.load())
        }
    }

    @Test fun profileAndMaintenanceSurviveClosingAndReopeningDatabase() {
        MotorcycleRepository(context).use {
            assertNull(it.load().profile)
            it.saveProfile(MotorcycleDraft("Mi moto", "Modelo", "12500,25"))
            it.setPhoto("moto_abc-123.jpg")
            it.saveMaintenance(draft(), today)
        }
        MotorcycleRepository(context).use {
            val initial = it.load()
            assertEquals(12_500_250L, initial.profile?.odometerMeters)
            assertEquals("moto_abc-123.jpg", initial.profile?.photoName)
            assertEquals("Filtro cambiado", initial.maintenance.single().notes)
            it.saveProfile(MotorcycleDraft("Nuevo nombre", "Modelo 2", "13000"))
            assertEquals(initial.maintenance, it.load().maintenance)
            assertEquals("moto_abc-123.jpg", it.load().profile?.photoName)
        }
    }

    @Test fun maintenanceCanBeCorrectedAndDeletedPersistently() {
        val id = MotorcycleRepository(context).use {
            it.saveProfile(MotorcycleDraft("Moto", "", "0"))
            it.saveMaintenance(draft(), today)
            val id = it.load().maintenance.single().id
            it.saveMaintenance(draft().copy(id = id, title = "Aceite y filtro", nextOdometerKm = "16000"), today)
            assertEquals(1, it.load().maintenance.size)
            id
        }
        MotorcycleRepository(context).use {
            assertEquals("Aceite y filtro", it.load().maintenance.single().title)
            it.deleteMaintenance(id)
        }
        MotorcycleRepository(context).use { assertTrue(it.load().maintenance.isEmpty()) }
    }

    @Test fun unreadableDatabaseFailsWithoutDeletingOrReplacingRiderData() {
        MotorcycleRepository(context).use { it.saveProfile(MotorcycleDraft("Moto", "", "12000")) }
        val file = context.getDatabasePath("motorcycles.db")
        val damaged = "damaged database retained for recovery".toByteArray()
        file.writeBytes(damaged)
        assertTrue(runCatching { MotorcycleRepository(context).use { it.load() } }.isFailure)
        assertArrayEquals(damaged, file.readBytes())
    }

    @Test fun invalidInputsLeaveSavedDataIntact() {
        MotorcycleRepository(context).use {
            it.saveProfile(MotorcycleDraft("Moto", "", "12000"))
            val before = it.load()
            for (invalid in listOf("-1", "NaN", "Infinity", "12.000,50", "1e5", "1.2345", "10000000")) {
                assertNull(invalid, parseManualKilometers(invalid))
                assertTrue(runCatching { it.saveProfile(MotorcycleDraft("Moto", "", invalid)) }.isFailure)
            }
            assertTrue(runCatching { it.saveMaintenance(draft().copy(date = "2027-01-01"), today) }.isFailure)
            assertTrue(runCatching { it.saveMaintenance(draft().copy(date = "2026-02-30"), today) }.isFailure)
            assertTrue(runCatching { it.saveMaintenance(draft().copy(nextOdometerKm = "11999"), today) }.isFailure)
            assertEquals(before, it.load())
        }
        assertEquals(0L, parseManualKilometers("0"))
        assertEquals(12345L, parseManualKilometers("12,345"))
        assertEquals(12345L, parseManualKilometers("12.345"))
    }

    @Test fun dueStatusUsesEnteredOdometerAndDateWithExplicitUnknownMileage() {
        val item = MaintenanceRecord("id", "Aceite", today.minusDays(1), 10_000_000, "", null, 12_000_000)
        assertFalse(item.isDue(today, null))
        assertFalse(item.isDue(today, 11_999_999))
        assertTrue(item.isDue(today, 12_000_000))
        assertTrue(item.copy(nextDate = today).isDue(today, null))
        assertFalse(item.copy(nextDate = today.plusDays(1)).isDue(today, null))
    }

    @Test fun gpsDistanceIsIndependentAndMissingOrCorruptHistoryIsNotAFabricatedDistance() {
        val root = Files.createTempDirectory("motorcycle-gps").toFile()
        try {
            val trips = File(root, "trips").apply { mkdirs() }
            val index = File(trips, "trips_index.json")
            val repository = TripRepository(root)
            assertEquals(0.0 to 0, repository.recordedDistance())
            index.writeText(JSONArray().put(JSONObject().put("distanceMeters", 1234.5))
                .put(JSONObject().put("distanceMeters", 500.0)).toString())
            assertEquals(1734.5 to 2, repository.recordedDistance())
            MotorcycleRepository(context).use {
                it.saveProfile(MotorcycleDraft("Moto", "", "9000"))
                repository.recordedDistance()
                assertEquals(9_000_000L, it.load().profile?.odometerMeters)
            }
            index.writeText("invalid")
            assertTrue(runCatching { repository.recordedDistance() }.isFailure)
            repository.forget()
            assertEquals(0.0 to 0, repository.recordedDistance())
        } finally { root.deleteRecursively() }
    }
}
