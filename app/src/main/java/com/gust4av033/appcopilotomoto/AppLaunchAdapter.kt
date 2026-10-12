package com.gust4av033.appcopilotomoto

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

data class LaunchableApp(val label: String, val packageName: String, val activityName: String)
data class AppTarget(val label: String, val launchIntent: Intent, val spokenLabel: String = label,
    val draftText: String? = null, val navigationDestination: NavigationDestination? = null,
    val navigationSearchQuery: String? = null)

sealed interface AppMatch {
    data class Found(val app: LaunchableApp) : AppMatch
    data class Ambiguous(val commands: List<String>) : AppMatch
    data object Missing : AppMatch
}

/** Empareja nombres reales del lanzador; nunca adivina una aplicación por coincidencia parcial. */
object AppNameMatcher {
    private val numbers = listOf("uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve", "diez")

    fun commandFor(app: LaunchableApp, catalog: List<LaunchableApp>): String {
        val sameName = catalog.filter { normalized(it.label) == normalized(app.label) }
            .sortedWith(compareBy(LaunchableApp::packageName, LaunchableApp::activityName))
        val suffix = if (sameName.size > 1) {
            val number = sameName.indexOf(app) + 1
            " número ${numbers.getOrNull(number - 1) ?: number.toString()}"
        } else ""
        return "abre ${app.label}$suffix"
    }

    fun find(spokenName: String, catalog: List<LaunchableApp>): AppMatch {
        val spoken = normalized(spokenName)
        val exact = catalog.filter { normalized(it.label) == spoken }
        if (exact.size == 1) return AppMatch.Found(exact.single())
        if (exact.size > 1) return AppMatch.Ambiguous(exact.map { commandFor(it, catalog) }.distinct())

        val qualified = Regex("^(.+) numero (\\d+|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez)$")
            .matchEntire(spoken) ?: return AppMatch.Missing
        val sameName = catalog.filter { normalized(it.label) == qualified.groupValues[1] }
            .sortedWith(compareBy(LaunchableApp::packageName, LaunchableApp::activityName))
        if (sameName.size < 2) return AppMatch.Missing
        val said = qualified.groupValues[2]
        val index = said.toIntOrNull() ?: numbers.indexOf(said).takeIf { it >= 0 }?.plus(1)
        return sameName.getOrNull((index ?: return AppMatch.Missing) - 1)?.let(AppMatch::Found)
            ?: AppMatch.Missing
    }

    private fun normalized(value: String) = MinimalIntentRouter.normalize(value)
}

/** Consulta el lanzador del perfil actual cada vez; no guarda un inventario permanente. */
class AppLaunchAdapter(private val context: Context) {
    fun installedApps(): Result<List<LaunchableApp>> = runCatching {
        val manager = context.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val candidates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.queryIntentActivities(query, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            manager.queryIntentActivities(query, 0)
        }
        candidates.mapNotNull { info ->
            val activity = info.activityInfo ?: return@mapNotNull null
            if (activity.packageName == context.packageName) return@mapNotNull null
            val label = info.loadLabel(manager).toString().trim()
            if (label.isBlank()) return@mapNotNull null
            LaunchableApp(label, activity.packageName, activity.name)
        }.distinctBy { it.packageName to it.activityName }
            .sortedWith(compareBy({ MinimalIntentRouter.normalize(it.label) },
                LaunchableApp::packageName, LaunchableApp::activityName))
    }

    fun find(spokenName: String): Result<AppTarget> = runCatching {
        val catalog = installedApps().getOrThrow()
        val found = when (val match = AppNameMatcher.find(spokenName, catalog)) {
            is AppMatch.Found -> match.app
            is AppMatch.Ambiguous -> error("Hay varias aplicaciones con ese nombre. Decí ${match.commands.joinToString(" o ")}.")
            AppMatch.Missing -> error("No encontré ‘$spokenName’ entre las aplicaciones que Android permite abrir.")
        }
        AppTarget(found.label, Intent.makeMainActivity(ComponentName(found.packageName, found.activityName)))
    }
}
