package com.yann4wrld.y4battery

import android.app.ActivityManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("y4battery", Context.MODE_PRIVATE)

    var blocked: Set<String>
        get() = sp.getStringSet("blocked", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("blocked", v).apply()

    var auto: Boolean
        get() = sp.getBoolean("auto", false)
        set(v) = sp.edit().putBoolean("auto", v).apply()
}

data class CleanResult(val cleaned: Int, val frozen: Int, val freedMb: Long)

object Cleaner {

    fun clean(ctx: Context): CleanResult {
        val am = ctx.getSystemService(ActivityManager::class.java)
        val blocked = Prefs(ctx).blocked
        val shizuku = ShizukuShell.ready()
        val protected = AppRepo.protectedPackages(ctx)

        val before = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem

        // Toutes les applis utilisateur + celles que tu as mises dans la liste "geler"
        val targets = (AppRepo.targets(ctx, includeSystem = false).map { it.packageName } + blocked)
            .toSet() - protected

        var cleaned = 0
        var frozen = 0
        for (pkg in targets) {
            if (pkg in blocked && shizuku) {
                if (ShizukuShell.forceStop(pkg)) frozen++
            } else {
                // Ne tue que les processus qui ne sont PAS au premier plan
                am.killBackgroundProcesses(pkg)
                cleaned++
            }
        }

        Thread.sleep(600)
        val after = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem
        val freed = ((after - before).coerceAtLeast(0)) / (1024 * 1024)
        return CleanResult(cleaned, frozen, freed)
    }
}

class CleanWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Cleaner.clean(applicationContext)
        return Result.success()
    }
}

object AutoClean {
    private const val NAME = "y4_autoclean"

    fun set(ctx: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(ctx)
        if (enabled) {
            // 15 min = intervalle minimum autorisé par Android
            val req = PeriodicWorkRequestBuilder<CleanWorker>(15, TimeUnit.MINUTES).build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
        } else {
            wm.cancelUniqueWork(NAME)
        }
    }
}
