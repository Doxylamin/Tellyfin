package app.tellyfin.androidtv.diagnostics

import android.app.Application

/**
 * The `noSentry` flavor's implementation — a true no-op. No telemetry SDK is even a dependency
 * of this flavor, so nothing here can ever send anything anywhere, by construction rather than
 * by a runtime flag.
 */
object CrashReporting {
    fun init(application: Application) {}

    fun addBreadcrumb(message: String, category: String) {}

    fun captureException(throwable: Throwable): String? = null

    fun captureMessage(message: String, level: ReportLevel) {}
}
