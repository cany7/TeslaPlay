package io.github.cany7.teslaplay

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.session.MediaSession
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import io.github.cany7.teslaplay.host.R
import io.github.cany7.teslaplay.media.CarPlayMediaSessionBridge
import io.github.cany7.teslaplay.media.CarPlayMediaSource
import io.github.cany7.teslaplay.media.MediaCommand
import io.github.cany7.teslaplay.media.MediaCommandResult
import io.github.cany7.teslaplay.media.NowPlayingState
import io.github.cany7.teslaplay.shared.R as UiR
import io.github.cany7.teslaplay.web.RootAccess
import io.github.cany7.teslaplay.web.WebSession
import io.github.cany7.teslaplay.projection.projectionSessions
import java.util.concurrent.CompletableFuture

/** Keeps the connection and its media session alive while no dashboard is visible. */
class TeslaPlaySessionService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var media: CarPlayMediaSessionBridge
    private var sessionSubscription: AutoCloseable? = null
    private var listenerSubscription: AutoCloseable? = null
    private val notificationStartedAt = System.currentTimeMillis()
    private data class NotificationContent(
        val title: String, val text: String, val token: MediaSession.Token?, val artwork: Bitmap?,
        val playing: Boolean, val commandGeneration: Long, val serviceTypes: Int,
    )
    private var publishedNotification: NotificationContent? = null
    private val main = Handler(Looper.getMainLooper())
    private var destroyed = false
    private var currentState = NowPlayingState()
    private var currentSession: MediaSession? = null
    private var currentArtwork: Bitmap? = null

    override fun onCreate() {
        super.onCreate()
        io.github.cany7.teslaplay.web.ManagedHttps.schedule(this)
        io.github.cany7.teslaplay.projection.registerProjectionSources(this)
        if (
            io.github.cany7.teslaplay.web.TeslaHttpCompatibility.config(this).let {
                it.enabled && it.mode == io.github.cany7.teslaplay.network.TeslaHttpMode.ROOT
            }
        )
            RootAccess.checkOnStartup()
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    getString(UiR.string.ui_carplay_service),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        media =
            CarPlayMediaSessionBridge(this) { state, session, artwork ->
                currentState = state
                currentSession = session
                currentArtwork = artwork
                if (!destroyed) updateNotification()
            }
        browserMediaBridge = media
        listenerSubscription = WebSession.observeListeners {
            main.post { if (!destroyed) updateNotification() }
        }
        sessionSubscription =
            projectionSessions.subscribe { _ ->
                main.post {
                    if (!destroyed) {
                        val current = projectionSessions.snapshot()
                        media.bind(current?.media, current?.mediaOutput,
                            systemAudioEnabled = (current as? io.github.cany7.teslaplay.projection.CarPlaySourceSession)?.carBluetoothAudio != true)
                        updateNotification()
                    }
                }
            }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("WakelockTimeout") // Owned by this foreground service, released in onDestroy.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            media.close()
            runWebAction { WebSession.stop() }
            projectionSessions.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        if (
            intent?.action == ACTION_MEDIA &&
                !currentState.available &&
                !projectionSessions.hasSession()
        ) {
            stopSelf()
            return START_NOT_STICKY
        }
        updateNotification()
        if (intent?.action == ACTION_MEDIA) {
            if (intent.getLongExtra(EXTRA_GENERATION, -1) == media.commandGeneration) {
                intent.getStringExtra(EXTRA_COMMAND)?.let { name ->
                    MediaCommand.entries.firstOrNull { it.name == name }?.let(media::command)
                }
            }
            return START_NOT_STICKY
        }
        runWebAction {
            if (!destroyed) WebSession.start(this)
            updateNotification()
            val acquireWakeLock = {
                if (!destroyed && WebSession.running && wakeLock == null) {
                    wakeLock =
                        getSystemService(PowerManager::class.java)
                            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TeslaPlay:server")
                            .apply { acquire() }
                }
            }
            if (Looper.myLooper() == Looper.getMainLooper()) acquireWakeLock()
            else main.post { acquireWakeLock() }
        }
        return START_NOT_STICKY
    }

    private fun runWebAction(action: () -> Unit) {
        // Ordinary Web listeners are unprivileged; Tesla routing has its own worker.
        action()
    }

    private fun mediaAction(command: MediaCommand, icon: Int, title: Int): Notification.Action {
        val intent =
            Intent(this, TeslaPlaySessionService::class.java)
                .setAction(ACTION_MEDIA)
                .setData(Uri.parse("teslaplay://media/${media.commandGeneration}/${command.name}"))
                .putExtra(EXTRA_COMMAND, command.name)
                .putExtra(EXTRA_GENERATION, media.commandGeneration)
        val pending =
            PendingIntent.getService(
                this,
                command.ordinal + 10,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        return Notification.Action.Builder(
                Icon.createWithResource(this, icon),
                getString(title),
                pending,
            )
            .build()
    }

    private fun updateNotification() {
        if (destroyed) return
        var types = 0
        if (Build.VERSION.SDK_INT >= 29) {
            types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            if (currentSession != null && currentState.available && currentState.playing ||
                projectionSessions.snapshot()?.let { it.capabilities.audioPlayback && !it.capabilities.browserAudio } == true)
                types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            if (
                Build.VERSION.SDK_INT >= 30 &&
                    projectionSessions.snapshot()?.capabilities?.microphone == true &&
                    AirPlayPersistence.loadMicrophoneSource(this) != CarPlayMicrophoneSource.DISABLED &&
                    checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
            ) {
                types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
        }
        val session = currentSession?.takeIf { currentState.available }
        val content = if (session != null) {
            NotificationContent(
                currentState.title?.takeIf { it.isNotBlank() }
                    ?: currentState.appName?.takeIf { it.isNotBlank() } ?: getString(R.string.app_name),
                listOfNotNull(currentState.artist, currentState.album).filter { it.isNotBlank() }.joinToString(" · "),
                session.sessionToken, currentArtwork, currentState.playing, media.commandGeneration, types,
            )
        } else {
            val endpoint = WebSession.httpsListenerPort?.takeIf { WebSession.httpsReady }?.let { "HTTPS · $it" }
                ?: WebSession.httpListenerPort?.let { "HTTP · $it" }
            NotificationContent(getString(R.string.app_name),
                endpoint?.let { getString(UiR.string.notification_streaming_running, it) }
                    ?: getString(UiR.string.ui_preparing_streaming_service),
                null, null, false, 0, types)
        }
        if (publishedNotification == content) return
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, TeslaPlayActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val builder =
            Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_teslaplay_notification)
                .setContentIntent(open)
                .setOngoing(true)
                .setWhen(notificationStartedAt)
                .setOnlyAlertOnce(true)
        if (session != null) {
            builder
                .setContentTitle(content.title)
                .setContentText(content.text)
                .setLargeIcon(currentArtwork)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(
                    mediaAction(
                        MediaCommand.PREVIOUS,
                        R.drawable.ic_media_skip_previous,
                        R.string.notification_action_previous,
                    )
                )
                .addAction(
                    mediaAction(
                        if (currentState.playing) MediaCommand.PAUSE else MediaCommand.PLAY,
                        if (currentState.playing) R.drawable.ic_media_pause
                        else R.drawable.ic_media_play,
                        if (currentState.playing) R.string.notification_action_pause
                        else R.string.notification_action_play,
                    )
                )
                .addAction(
                    mediaAction(
                        MediaCommand.NEXT,
                        R.drawable.ic_media_skip_next,
                        R.string.notification_action_next,
                    )
                )
                .setStyle(
                    Notification.MediaStyle()
                        .setMediaSession(session.sessionToken)
                        .setShowActionsInCompactView(0, 1, 2)
                )
        } else {
            val stop =
                PendingIntent.getService(
                    this,
                    1,
                    Intent(this, TeslaPlaySessionService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            builder
                .setContentTitle(content.title)
                .setContentText(content.text)
                .addAction(
                    Notification.Action.Builder(
                            Icon.createWithResource(this, R.drawable.ic_media_stop),
                            getString(UiR.string.ui_stop_service),
                            stop,
                        )
                        .build()
                )
        }
        val notification = builder.build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, types)
        else startForeground(NOTIFICATION_ID, notification)
        publishedNotification = content
    }

    override fun onDestroy() {
        destroyed = true
        if (browserMediaBridge === media) browserMediaBridge = null
        sessionSubscription?.close()
        listenerSubscription?.close()
        media.close()
        runWebAction { WebSession.stop() }
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        projectionSessions.stop()
        super.onDestroy()
    }

    companion object {
        // Only accessed on the main thread, like the native MediaSession callbacks.
        private var browserMediaBridge: CarPlayMediaSessionBridge? = null
        internal fun browserMediaCommand(source: CarPlayMediaSource, generation: Long, command: MediaCommand): CompletableFuture<MediaCommandResult> =
            browserMediaBridge?.browserCommand(source, generation, command)
                ?: CompletableFuture.completedFuture(MediaCommandResult.UNAVAILABLE)

        internal fun browserMediaSeek(source: CarPlayMediaSource, generation: Long, track: Long, positionMs: Long): CompletableFuture<MediaCommandResult> =
            browserMediaBridge?.browserSeek(source, generation, track, positionMs)
                ?: CompletableFuture.completedFuture(MediaCommandResult.UNAVAILABLE)

        const val ACTION_STOP = "io.github.cany7.teslaplay.DISCONNECT"
        private const val ACTION_MEDIA = "io.github.cany7.teslaplay.MEDIA_COMMAND"
        private const val EXTRA_COMMAND = "command"
        private const val EXTRA_GENERATION = "generation"
        private const val CHANNEL = "teslaplay_connection"
        internal const val NOTIFICATION_ID = 1
    }
}
