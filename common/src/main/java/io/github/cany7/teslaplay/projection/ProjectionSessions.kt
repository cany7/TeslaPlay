package io.github.cany7.teslaplay.projection

/** Application-scoped coordinator; the foreground service owns its shutdown. */
internal val projectionSessions = ProjectionRuntime()

@Synchronized internal fun registerProjectionSources(context: android.content.Context) {
    if (projectionSessions.sources().isNotEmpty()) return
    val app = context.applicationContext
    io.github.cany7.teslaplay.projection.androidauto.AndroidAutoMode.entries.forEach { mode ->
        projectionSessions.register(io.github.cany7.teslaplay.projection.androidauto.AndroidAutoSource(app, mode) {
            BrowserProjectionVideo(app, 1280, 720, VideoIngressKind.ENCODED)
        })
    }
    projectionSessions.register(io.github.cany7.teslaplay.projection.vnc.VncSource(app) { width, height ->
        BrowserProjectionVideo(app, width, height, VideoIngressKind.FRAMEBUFFER)
    })
    VideoIngressKind.entries.forEach { kind ->
        projectionSessions.register(SyntheticSource(kind,
            { ingress, width, height -> BrowserProjectionVideo(app, width, height, ingress) },
            { app.assets.open("projection/demo.h264clip") }))
    }
}
