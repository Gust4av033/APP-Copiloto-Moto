package com.gust4av033.appcopilotomoto

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

data class AppTarget(val label: String, val launchIntent: Intent)

/** Solo selecciona aplicaciones visibles en el lanzador y con nombre exacto. */
class AppLaunchAdapter(private val context: Context) {
    fun find(spokenName: String): Result<AppTarget> = runCatching {
        val manager = context.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val candidates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.queryIntentActivities(query, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            manager.queryIntentActivities(query, 0)
        }
        val matches = candidates.mapNotNull { info ->
            val label = info.loadLabel(manager).toString().trim()
            val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
            if (packageName == context.packageName ||
                MinimalIntentRouter.normalize(label) != MinimalIntentRouter.normalize(spokenName)) null
            else packageName to label
        }.distinctBy { it.first }
        check(matches.isNotEmpty()) { "No encontré ‘$spokenName’ entre las aplicaciones instaladas." }
        check(matches.size == 1) { "Hay varias aplicaciones llamadas ‘$spokenName’; no abrí ninguna." }
        val (packageName, label) = matches.single()
        val launch = manager.getLaunchIntentForPackage(packageName)
            ?: error("$label no permite abrirse desde APP.")
        AppTarget(label, launch)
    }
}
