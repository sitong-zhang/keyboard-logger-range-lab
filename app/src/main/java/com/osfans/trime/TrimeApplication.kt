package com.osfans.trime

import android.app.Application
import android.os.Process
import cat.ereza.customactivityoncrash.config.CaocConfig
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.settings.LogActivity
import timber.log.Timber

/**
 * Custom Application class.
 * Application class will only be created once when the app run,
 * so you can init a "global" class here, whose methods serve other
 * classes everywhere.
 */
class TrimeApplication : Application() {
    companion object {
        private var instance: TrimeApplication? = null
        private var lastPid: Int? = null

        fun getInstance() =
            instance ?: throw IllegalStateException("Trime application is not created!")

        fun getLastPid() = lastPid
    }

    override fun onCreate() {
        super.onCreate()
        // Local crash monitor must be installed first; it survives even if
        // every later initialisation step fails.
        try {
            com.keyrecord.app.CrashMonitor.install(this)
        } catch (e: Throwable) {
            // ignored
        }
        // Disable CustomActivityOnCrash: its error activity depends on complex
        // UI and can crash again, showing a blank screen instead of the error.
        CaocConfig.Builder
            .create()
            .enabled(false)
            .apply()
        instance = this
        try {
            if (BuildConfig.DEBUG) {
                Timber.plant(Timber.DebugTree())
            }
            val prefs = AppPrefs.initDefault(this)
            prefs.initDefaultPreferences()
            // record last pid for crash logs
            val appPrefs = AppPrefs.defaultInstance()
            val currentPid = Process.myPid()
            appPrefs.general.pid.apply {
                lastPid = this
                Timber.d("Last pid is $lastPid. Set it to current pid: $currentPid")
            }
            appPrefs.general.pid = currentPid
        } catch (e: Throwable) {
            // Record the failure but never crash the whole process here,
            // otherwise every entry point (settings, log viewer) goes blank.
            try {
                com.keyrecord.app.CrashMonitor.saveApplicationError(this, e)
            } catch (ignored: Throwable) {
            }
            return
        }
    }
}
