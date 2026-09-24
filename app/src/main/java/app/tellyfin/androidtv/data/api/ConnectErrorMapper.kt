package app.tellyfin.androidtv.data.api

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.SecureConnectionException
import org.jellyfin.sdk.api.client.exception.TimeoutException as SdkTimeoutException

/**
 * Translates the raw exceptions a connect/login attempt can throw into text a user can act
 * on. The SDK and okhttp otherwise surface things like "Unknown IO error occured!" verbatim.
 */
fun Throwable.toUserMessage(): String = when (this) {
    is UnknownHostException -> "Can't find that server — check the address"
    is ConnectException, is SocketTimeoutException, is SdkTimeoutException ->
        "Couldn't reach the server — check it's running and reachable on this network"
    is SSLException, is SecureConnectionException ->
        "Secure connection failed — check the server's HTTPS setup"
    is InvalidStatusException -> if (status == 401) {
        "Incorrect username or password"
    } else {
        "Server returned an error (HTTP $status)"
    }
    else -> "Couldn't connect to the server"
}

/**
 * Whether this error is worth sending to Sentry. A clean HTTP status from the server (wrong
 * password, a 500, etc.) means the request round-tripped fine — there's nothing ambiguous to
 * diagnose. Everything else (unreachable host, timeout, TLS failure, an unrecognized
 * exception) is worth capturing.
 */
fun Throwable.isReportable(): Boolean = this !is InvalidStatusException
