package com.yann4wrld.y4battery

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppItem(
    val pkg: String,
    val label: String,
    val icon: ImageBitmap,
    val lastUsed: Long,
    val stopped: Boolean,
    val system: Boolean,
)

object AppRepo {

    /** Applis qu'on ne touche jamais : les arrêter casserait le téléphone. */
    fun protectedPackages(ctx: Context): Set<String> {
        val s = mutableSetOf(
            ctx.packageName,
            "moe.shizuku.privileged.api",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.android.vending",
            "com.android.systemui",
            "com.android.phone",
            "com.android.settings",
            "com.android.providers.telephony",
        )
        ctx.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo?.packageName?.let { s.add(it) } // launcher
        Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')?.let { s.add(it) } // clavier
        return s
    }

    private fun isSystem(ai: ApplicationInfo) = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0

    /** Applis cibles : utilisateur (+ système lançables si demandé), hors liste protégée. */
    fun targets(ctx: Context, includeSystem: Boolean): List<ApplicationInfo> {
        val pm = ctx.packageManager
        val protected = protectedPackages(ctx)
        return pm.getInstalledApplications(0).filter { ai ->
            ai.packageName !in protected && (
                !isSystem(ai) ||
                    (includeSystem && pm.getLaunchIntentForPackage(ai.packageName) != null)
                )
        }
    }

    fun hasUsageAccess(ctx: Context): Boolean {
        val aom = ctx.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            aom.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            @Suppress("DEPRECATION")
            aom.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun lastUsed(ctx: Context): Map<String, Long> {
        if (!hasUsageAccess(ctx)) return emptyMap()
        val usm = ctx.getSystemService(UsageStatsManager::class.java)
        val end = System.currentTimeMillis()
        val start = end - 7L * 24 * 3600 * 1000
        return usm.queryAndAggregateUsageStats(start, end).mapValues { it.value.lastTimeUsed }
    }

    fun load(ctx: Context, includeSystem: Boolean): List<AppItem> {
        val pm = ctx.packageManager
        val used = lastUsed(ctx)
        return targets(ctx, includeSystem).map { ai ->
            AppItem(
                pkg = ai.packageName,
                label = pm.getApplicationLabel(ai).toString(),
                icon = pm.getApplicationIcon(ai).toBitmap(96, 96).asImageBitmap(),
                lastUsed = used[ai.packageName] ?: 0L,
                stopped = (ai.flags and ApplicationInfo.FLAG_STOPPED) != 0,
                system = isSystem(ai),
            )
        }.sortedWith(compareByDescending<AppItem> { it.lastUsed }.thenBy { it.label.lowercase() })
    }
}
