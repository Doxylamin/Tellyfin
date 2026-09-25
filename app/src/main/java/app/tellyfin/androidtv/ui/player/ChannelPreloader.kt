package app.tellyfin.androidtv.ui.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.preload.PreloadMediaSource
import androidx.media3.exoplayer.upstream.Allocator
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import app.tellyfin.androidtv.diagnostics.CrashReporting
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Warms up the next channel's stream while the preview banner counts down, so confirming the
 * switch hands the player an already-buffered source instead of waiting on Jellyfin to open
 * the tuner and ExoPlayer to fill its start-up buffer.
 *
 * Uses [PreloadMediaSource] rather than a second ExoPlayer: it loads data without renderers,
 * so it never needs a second hardware decoder — cheap TV sticks may not have one, and could
 * steal the one the current channel is playing on.
 *
 * All methods are main-thread only.
 */
@OptIn(UnstableApi::class)
class ChannelPreloader(
    private val context: Context,
    private val player: ExoPlayer,
    private val allocator: Allocator,
    private val dataSourceFactory: DataSource.Factory,
    private val onPreloadFailed: (UUID) -> Unit,
    /** Enough is buffered that switching to this channel now starts instantly. */
    private val onPreloadReady: (UUID) -> Unit
) {
    private class Preload(
        val channelId: UUID,
        val maxBitrate: Int?,
        val source: PreloadMediaSource,
        val errors: PreloadErrorPolicy,
        val progress: BufferCap
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pending: Preload? = null
    // Bumped per start(); a callback posted for an older preload (cancelled, replaced — even by
    // a new preload of the same channel) sees a different value and is dropped.
    private var generation = 0

    // A preloaded source keeps its child source alive even after the player drops it, so
    // it has to be released explicitly once the player has moved on to something else.
    private var playing: PreloadMediaSource? = null

    fun start(channelId: UUID, maxBitrate: Int?, url: String) {
        cancel()
        val startedGeneration = ++generation
        val progress = BufferCap(channelId) {
            mainHandler.post { if (generation == startedGeneration && pending != null) onPreloadReady(channelId) }
        }
        val errors = PreloadErrorPolicy { error ->
            diag("preload $channelId failed at +${progress.elapsedMs()}ms: $error")
            mainHandler.post { onPreloadFailed(channelId) }
        }
        val factory = PreloadMediaSource.Factory(
            DefaultMediaSourceFactory(dataSourceFactory).setLoadErrorHandlingPolicy(errors),
            progress,
            requireNotNull(player.trackSelector),
            DefaultBandwidthMeter.getSingletonInstance(context),
            Array(player.rendererCount) { player.getRenderer(it).capabilities },
            allocator,
            player.playbackLooper
        )
        val source = factory.createMediaSource(MediaItem.fromUri(url))
        source.preload(/* startPositionUs = */ C.TIME_UNSET)
        diag("preload $channelId started")
        pending = Preload(channelId, maxBitrate, source, errors, progress)
    }

    /**
     * Detaches the preload for [channelId] if it's healthy and was made at the same bitrate.
     * Any other pending preload is released — it's stale the moment a switch happens.
     */
    fun take(channelId: UUID, maxBitrate: Int?): PreloadMediaSource? {
        val preload = pending ?: run {
            diag("take $channelId: nothing preloaded")
            return null
        }
        pending = null
        val matches = preload.channelId == channelId && preload.maxBitrate == maxBitrate
        val state = "age +${preload.progress.elapsedMs()}ms, ${preload.progress.describe()}"
        if (matches && preload.errors.handOver()) {
            diag("take $channelId: handing over preload ($state)")
            return preload.source
        }
        diag("take $channelId: discarding preload for ${preload.channelId} (matches=$matches, $state)")
        preload.source.releasePreloadMediaSource()
        return null
    }

    /** Call right after the player's source was replaced, with the preload now in use (if any). */
    fun onPlayerSourceSet(source: PreloadMediaSource?) {
        // Posted to the playback looper behind the player's own source swap, so the old
        // source is no longer in use by the time it's released.
        playing?.releasePreloadMediaSource()
        playing = source
    }

    fun cancel() {
        pending?.let {
            diag("preload ${it.channelId} cancelled at +${it.progress.elapsedMs()}ms, ${it.progress.describe()}")
            it.source.releasePreloadMediaSource()
        }
        pending = null
    }

    /** Must run before the player is released — that quits the looper releases are posted to. */
    fun release() {
        cancel()
        onPlayerSourceSet(null)
    }

    /**
     * Stop once enough is buffered for an instant start; no need to hold two full live streams.
     * Also records how far the preload got, for the diagnostic log. Called on the playback thread.
     */
    private class BufferCap(
        private val channelId: UUID,
        private val onTargetReached: () -> Unit
    ) : PreloadMediaSource.PreloadControl {
        @Volatile private var reached = false
        private val startedAt = SystemClock.elapsedRealtime()
        @Volatile private var preparedAtMs = -1L
        @Volatile private var bufferedUs = 0L

        fun elapsedMs() = SystemClock.elapsedRealtime() - startedAt
        fun describe() = "prepared=${if (preparedAtMs >= 0) "+${preparedAtMs}ms" else "no"}, buffered=${bufferedUs / 1000}ms"

        override fun onTimelineRefreshed(source: PreloadMediaSource): Boolean {
            diag("preload $channelId timeline at +${elapsedMs()}ms")
            return true
        }

        override fun onPrepared(source: PreloadMediaSource): Boolean {
            preparedAtMs = elapsedMs()
            diag("preload $channelId prepared at +${preparedAtMs}ms")
            return true
        }

        override fun onContinueLoadingRequested(source: PreloadMediaSource, bufferedPositionUs: Long): Boolean {
            bufferedUs = bufferedPositionUs
            val keepGoing = bufferedPositionUs < TARGET_BUFFER_US
            if (!keepGoing && !reached) {
                reached = true
                diag("preload $channelId reached ${bufferedPositionUs / 1000}ms at +${elapsedMs()}ms")
                onTargetReached()
            }
            return keepGoing
        }
    }

    private companion object {
        const val TARGET_BUFFER_US = 2_000_000L
    }
}

/**
 * While preloading, any load error fails the preload immediately instead of retrying — it's
 * holding a second tuner on a speculative request, and the regular switch path still works.
 * Once handed over to the player, the source behaves like any other and gets normal retries.
 *
 * Error callbacks arrive on the loading thread, handOver() on main — hence the atomic state.
 */
@OptIn(UnstableApi::class)
private class PreloadErrorPolicy(private val onFailed: (Throwable) -> Unit) : LoadErrorHandlingPolicy {
    private val default = DefaultLoadErrorHandlingPolicy()
    private val state = AtomicInteger(PRELOADING)

    /** @return false if the preload already failed and must not be used. */
    fun handOver(): Boolean =
        state.compareAndSet(PRELOADING, HANDED_OVER) || state.get() == HANDED_OVER

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        if (state.get() == HANDED_OVER) return default.getRetryDelayMsFor(loadErrorInfo)
        if (state.compareAndSet(PRELOADING, FAILED)) onFailed(loadErrorInfo.exception)
        return C.TIME_UNSET
    }

    override fun getFallbackSelectionFor(
        fallbackOptions: LoadErrorHandlingPolicy.FallbackOptions,
        loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo
    ): LoadErrorHandlingPolicy.FallbackSelection? =
        default.getFallbackSelectionFor(fallbackOptions, loadErrorInfo)

    override fun getMinimumLoadableRetryCount(dataType: Int): Int =
        default.getMinimumLoadableRetryCount(dataType)

    private companion object {
        const val PRELOADING = 0
        const val FAILED = 1
        const val HANDED_OVER = 2
    }
}

/** Temporary pre-buffering diagnostics: logcat plus Sentry logs (beta builds), as Fire TV hides logcat. */
internal fun diag(message: String) {
    Log.d("TellyfinPreload", message)
    CrashReporting.log("[preload] $message")
}
