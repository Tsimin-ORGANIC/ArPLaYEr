package com.tsimin.arplayer

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class AudioService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var session: MediaSession? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val url = intent?.getStringExtra("url")
        if (url.isNullOrEmpty()) return START_NOT_STICKY
        val title = intent.getStringExtra("title").orEmpty()
        val artist = intent.getStringExtra("artist").orEmpty()
        val referer = intent.getStringExtra("referer") ?: "https://www.bilibili.com/"
        val cookie = intent.getStringExtra("cookie").orEmpty()

        val headers = HashMap<String, String>()
        headers["Referer"] = referer
        if (cookie.isNotEmpty()) headers["Cookie"] = cookie
        val dataSource = DefaultHttpDataSource.Factory()
            .setUserAgent(Http.mediaUserAgent())
            .setDefaultRequestProperties(headers)
            .setAllowCrossProtocolRedirects(true)
        val item = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .build()
            )
            .build()
        val p = ensurePlayer()
        p.setMediaSource(
            ProgressiveMediaSource.Factory(dataSource).createMediaSource(item)
        )
        p.prepare()
        p.play()
        return START_NOT_STICKY
    }

    private fun ensurePlayer(): ExoPlayer {
        player?.let { return it }
        val p = ExoPlayer.Builder(this).build()
        p.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true
        )
        p.setHandleAudioBecomingNoisy(true)
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) stopSelf()
            }

            override fun onPlayerError(error: PlaybackException) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        applicationContext,
                        "音频播放失败，可能链接已过期",
                        Toast.LENGTH_LONG
                    ).show()
                }
                stopSelf()
            }
        })
        session = MediaSession.Builder(this, p).build()
        player = p
        return p
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.release()
        player?.release()
        session = null
        player = null
        super.onDestroy()
    }
}
