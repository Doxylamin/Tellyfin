package app.tellyfin.androidtv.diagnostics

import android.app.Application
import app.tellyfin.androidtv.BuildConfig
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.android.core.SentryAndroid

/** The `sentry` flavor's implementation — the only place io.sentry.* is referenced. */
object CrashReporting {

    private val verbose = BuildConfig.DEBUG || BuildConfig.PRERELEASE

    fun init(application: Application) {
        SentryAndroid.init(application) { options ->
            options.dsn = BuildConfig.SENTRY_DSN
            options.environment = when {
                BuildConfig.DEBUG -> "debug"
                BuildConfig.PRERELEASE -> "beta"
                else -> "release"
            }
            options.release = "tellyfin@${BuildConfig.VERSION_NAME}"
            // Logs stream to the dashboard continuously, independent of any captured event —
            // fine for debug/beta diagnosis, too verbose (and too much real-user telemetry) to
            // leave on for every routine action in a release build.
            options.logs.isEnabled = verbose
        }
    }

    fun addBreadcrumb(message: String, category: String) {
        Sentry.addBreadcrumb(message, category)
        Sentry.logger().info(message)
    }

    /** Streams a diagnostic log line to Sentry — debug/beta builds only, a no-op in releases. */
    fun log(message: String) {
        if (verbose) Sentry.logger().info(message)
    }

    /** Always active, in every build — a real exception is exactly what production reporting
     *  is for. Returns a short ref the user can quote, or null if nothing was actually reported. */
    fun captureException(throwable: Throwable): String? =
        Sentry.captureException(throwable).toString().take(8)

    /** A synthetic diagnostic message (not a real exception) — debug/beta only. Production
     *  shouldn't get one of these from every user who happens to hit a slow network; it should
     *  only ever report actual stack traces via captureException above. */
    fun captureMessage(message: String, level: ReportLevel) {
        if (!verbose) return
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
