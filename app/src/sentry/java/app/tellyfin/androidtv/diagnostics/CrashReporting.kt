package app.tellyfin.androidtv.diagnostics

import android.app.Application
import app.tellyfin.androidtv.BuildConfig
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.android.core.SentryAndroid

/** The `sentry` flavor's implementation — the only place io.sentry.* is referenced. */
object CrashReporting {

    fun init(application: Application) {
        SentryAndroid.init(application) { options ->
            options.dsn = BuildConfig.SENTRY_DSN
            options.environment = if (BuildConfig.DEBUG) "debug" else "release"
            options.release = "tellyfin@${BuildConfig.VERSION_NAME}"
            // Logs show up on the dashboard as they're sent, independent of any event/error —
            // unlike breadcrumbs, which are only visible once attached to a captured event.
            options.logs.isEnabled = true
        }
    }

    fun addBreadcrumb(message: String, category: String) {
        Sentry.addBreadcrumb(message, category)
        Sentry.logger().info(message)
    }

    /** Returns a short ref the user can quote, or null if nothing was actually reported. */
    fun captureException(throwable: Throwable): String? =
        Sentry.captureException(throwable).toString().take(8)

    fun captureMessage(message: String, level: ReportLevel) {
        Sentry.captureMessage(message, level.toSentryLevel())
        Sentry.logger().log(level.toSentryLogLevel(), message)
    }

    private fun ReportLevel.toSentryLevel(): SentryLevel = when (this) {
        ReportLevel.INFO -> SentryLevel.INFO
        ReportLevel.WARNING -> SentryLevel.WARNING
        ReportLevel.ERROR -> SentryLevel.ERROR
    }

    private fun ReportLevel.toSentryLogLevel(): io.sentry.SentryLogLevel = when (this) {
        ReportLevel.INFO -> io.sentry.SentryLogLevel.INFO
        ReportLevel.WARNING -> io.sentry.SentryLogLevel.WARN
        ReportLevel.ERROR -> io.sentry.SentryLogLevel.ERROR
    }
}
