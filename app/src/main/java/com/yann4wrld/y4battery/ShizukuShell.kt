package com.yann4wrld.y4battery

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

enum class ShizukuState { NOT_RUNNING, NO_PERMISSION, READY }

/**
 * Exécute des commandes shell avec les droits "adb" via Shizuku.
 * Permet ce qu'une appli normale ne peut pas faire : force-stop et
 * interdiction réelle de tourner en arrière-plan.
 */
object ShizukuShell {

    const val PKG = "moe.shizuku.privileged.api"

    fun state(): ShizukuState = try {
        when {
            !Shizuku.pingBinder() || Shizuku.isPreV11() -> ShizukuState.NOT_RUNNING
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuState.READY
            else -> ShizukuState.NO_PERMISSION
        }
    } catch (t: Throwable) {
        ShizukuState.NOT_RUNNING
    }

    fun ready() = state() == ShizukuState.READY

    fun exec(cmd: String): Boolean = try {
        // newProcess est privé depuis l'API 13 mais reste la voie la plus simple.
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        m.isAccessible = true
        val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        p.waitFor() == 0
    } catch (t: Throwable) {
        false
    }

    fun forceStop(pkg: String) = exec("am force-stop $pkg")

    /** Interdit (ou réautorise) l'exécution en arrière-plan de l'appli. */
    fun restrictBackground(pkg: String, restrict: Boolean): Boolean {
        val mode = if (restrict) "ignore" else "allow"
        val bucket = if (restrict) "restricted" else "active"
        return exec(
            "cmd appops set $pkg RUN_ANY_IN_BACKGROUND $mode; " +
                "cmd appops set $pkg RUN_IN_BACKGROUND $mode; " +
                "am set-standby-bucket $pkg $bucket 2>/dev/null; true"
        )
    }
}
