package com.gust4av033.appcopilotomoto

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.gust4av033.appcopilotomoto.presentation.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-night")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class MotorcycleUiTest {
    private val compose = createComposeRule()
    @get:Rule val host = org.junit.rules.TestRule { base, description ->
        object : org.junit.runners.model.Statement() {
            override fun evaluate() { assumeTrue(BuildConfig.DEBUG); compose.apply(base, description).evaluate() }
        }
    }
    private val saved = MotorcycleUiState(loaded = true,
        data = MotorcycleData(MotorcycleProfile("Moto de prueba", "Modelo de prueba", 12_000_000, null)))

    private fun reveal(text: String): SemanticsNodeInteraction {
        val node = compose.onNodeWithText(text)
        if (!node.isDisplayed()) compose.onNodeWithTag("settings_content").performScrollToIndex(0)
        repeat(30) {
            if (!node.isDisplayed()) compose.onNodeWithTag("settings_content").performTouchInput {
                swipeUp(startY = height * .8f, endY = height * .35f, durationMillis = 250)
            }
        }
        node.assertIsDisplayed()
        return node
    }

    @Test fun editingProfileEmitsValidatedDraftWithoutStartingAnyAudio() {
        var draft: MotorcycleDraft? = null
        var audio = 0
        compose.setContent {
            CopilotApp(CopilotUiState(motorcycle = saved), CopilotScreen.MOTORCYCLE, {},
                CopilotActions(onSaveMotorcycle = { draft = it }, onToggleTrip = { audio++ },
                    onToggleWake = { audio++ }, onToggleListening = { audio++ }, onManualTripCommand = { audio++ }))
        }
        reveal("Editar Mi Moto").performClick()
        reveal("Nombre de tu moto").performTextReplacement("Tormenta")
        reveal("Kilometraje del tablero en km (opcional)").performTextReplacement("12450,5")
        reveal("Guardar Mi Moto").performClick()
        assertEquals(MotorcycleDraft("Tormenta", "Modelo de prueba", "12450,5"), draft)
        assertEquals(0, audio)
    }

    @Test fun activeTripLocksEditingButAllowsReadingAndGoingBack() {
        var back: CopilotScreen? = null
        compose.setContent {
            CopilotApp(CopilotUiState(motorcycle = saved, tripActive = true), CopilotScreen.MOTORCYCLE,
                { back = it }, CopilotActions())
        }
        reveal("Elegir moto del catálogo").assertIsNotEnabled()
        reveal("Nombre de tu moto").assertIsNotEnabled()
        reveal("Guardar Mi Moto").assertIsNotEnabled()
        compose.onNodeWithTag("settings_content").performScrollToIndex(0)
        compose.onNodeWithContentDescription("Volver a Ajustes").performClick()
        assertEquals(CopilotScreen.SETTINGS, back)
    }

    @Test fun aNewBikeIsChosenFromTheCatalogueAndSavedWithoutPhotoAccess() {
        var draft: MotorcycleDraft? = null
        compose.setContent {
            CopilotApp(CopilotUiState(motorcycle = MotorcycleUiState(loaded = true)), CopilotScreen.MOTORCYCLE, {},
                CopilotActions(onSaveMotorcycle = { draft = it }))
        }
        reveal("Elegir moto del catálogo").performClick()
        compose.onNodeWithText("Hero Ignitor 125").performClick()
        reveal("Nombre de tu moto").performTextReplacement("Ignitor azul")
        reveal("Guardar Mi Moto").performClick()
        assertEquals("hero_ignitor_125", draft!!.catalogId)
        assertEquals("Hero Ignitor 125", draft!!.model)
        compose.onNodeWithText("Elegir foto").assertDoesNotExist()
    }

    @Test fun catalogueSpecificationsAreReadOnlyUntilExplicitEditAndEmitOnlyOverrides() {
        var draft: MotorcycleDraft? = null
        val selected = saved.copy(data = MotorcycleData(saved.data.profile!!.copy(catalogId = "hero_ignitor_125")))
        compose.setContent {
            CopilotApp(CopilotUiState(motorcycle = selected), CopilotScreen.MOTORCYCLE, {},
                CopilotActions(onSaveMotorcycle = { draft = it }))
        }
        reveal("Capacidad del tanque (L)").assertIsNotEnabled()
        compose.onNodeWithText("10").assertExists()
        reveal("Editar Mi Moto").performClick()
        reveal("Capacidad del tanque (L)").performTextReplacement("9.8")
        reveal("Guardar Mi Moto").performClick()
        assertEquals(mapOf("FUEL" to "9.8"), draft!!.specificationOverrides)
        assertEquals("10", MotorcycleCatalog.find("hero_ignitor_125")!!.specifications["FUEL"])
    }

    @Test fun loadFailureShowsRetryAndNeverAnEditableEmptyProfile() {
        var retries = 0
        compose.setContent {
            CopilotApp(CopilotUiState(motorcycle = MotorcycleUiState(error = true,
                messageRes = R.string.motorcycle_load_error)), CopilotScreen.MOTORCYCLE, {},
                CopilotActions(onRefreshMotorcycle = { retries++ }))
        }
        compose.onNodeWithText("Guardar Mi Moto").assertDoesNotExist()
        reveal("Volver a leer los datos").performClick()
        assertEquals(1, retries)
    }

    @Test fun maintenanceControlsSaveEditAndDeleteARealLocalRecord() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("motorcycles.db")
        MotorcycleRepository(context).use { it.saveProfile(MotorcycleDraft("Moto", "", "12000")) }
        val ui = mutableStateOf(saved)
        fun reload() {
            val data = MotorcycleRepository(context).use { it.load() }
            ui.value = ui.value.copy(data = data, revision = ui.value.revision + 1)
        }
        reload()
        compose.setContent {
            CopilotApp(CopilotUiState(motorcycle = ui.value), CopilotScreen.MOTORCYCLE, {}, CopilotActions(
                onSaveMaintenance = { draft ->
                    MotorcycleRepository(context).use { it.saveMaintenance(draft) }; reload()
                },
                onDeleteMaintenance = { id ->
                    MotorcycleRepository(context).use { it.deleteMaintenance(id) }; reload()
                }
            ))
        }
        reveal("Registrar mantenimiento").performClick()
        reveal("Trabajo realizado (por ejemplo, aceite)").performTextReplacement("Aceite de prueba")
        reveal("Guardar mantenimiento").performClick()
        reveal("Aceite de prueba").assertIsDisplayed()
        reveal("Editar registro").performClick()
        reveal("Trabajo realizado (por ejemplo, aceite)").performTextReplacement("Aceite y filtro")
        reveal("Guardar mantenimiento").performClick()
        MotorcycleRepository(context).use { assertEquals("Aceite y filtro", it.load().maintenance.single().title) }
        reveal("Eliminar registro").performClick()
        compose.onNode(hasText("Eliminar registro") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        MotorcycleRepository(context).use { assertTrue(it.load().maintenance.isEmpty()) }
    }

    private fun capture(theme: String, scale: Float) {
        RuntimeEnvironment.setQualifiers("w360dp-h800dp-${if (theme == "night") "night" else "notnight"}")
        var starts = 0
        val screen = mutableStateOf(CopilotScreen.MAP)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                CopilotApp(CopilotUiState(motorcycle = saved.copy(data = MotorcycleData(saved.data.profile!!.copy(catalogId = "hero_ignitor_125")))), screen.value, { screen.value = it },
                    CopilotActions(onToggleTrip = { starts++ }, onManualTripCommand = { starts++ }))
            }
        }
        compose.onNodeWithTag("map_motorcycle").performClick()
        assertEquals(CopilotScreen.MOTORCYCLE, screen.value)
        assertEquals(0, starts)
        fun screenshot(suffix: String) {
            compose.runOnIdle {
                val view = (compose as AndroidComposeTestRule<*, *>).activity.window.decorView
                val image = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(image))
                val path = File("build/reports/mi-moto/mi-moto-$theme-${if (scale == 2f) "200" else "100"}-$suffix.png")
                path.parentFile?.mkdirs()
                path.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                assertTrue(path.length() > 1000)
            }
        }
        screenshot("top")
        reveal("Capacidad del tanque (L)").assertIsNotEnabled()
        screenshot("specs")
        reveal("Guardar Mi Moto")
        screenshot("profile")
        reveal("Registrar mantenimiento")
        screenshot("maintenance")
    }
    @Test fun nightNormal() = capture("night", 1f)
    @Test fun dayNormal() = capture("day", 1f)
    @Test fun nightLarge() = capture("night", 2f)
    @Test fun dayLarge() = capture("day", 2f)
}
