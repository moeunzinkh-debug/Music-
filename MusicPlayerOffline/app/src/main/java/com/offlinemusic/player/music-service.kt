package com.offlinemusic.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import java.io.IOException

class MusicService : Service(), MediaPlayer.OnCompletionListener {
    
    // អថេរសំខាន់ៗ
    private var mediaPlayer: MediaPlayer? = null
    private var currentSongPath: String? = null
    private var isPrepared = false
    
    // For controlling from activity
    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }
    
    private val binder = MusicBinder()
    
    // Notification
    private val CHANNEL_ID = "music_player_channel"
    private val NOTIFICATION_ID = 101
    private lateinit var notificationManager: NotificationManager
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize MediaPlayer
        mediaPlayer = MediaPlayer().apply {
            setOnCompletionListener(this@MusicService)
            setOnPreparedListener {
                isPrepared = true
                sendBroadcast(Intent("MUSIC_PREPARED"))
            }
            setOnErrorListener { _, what, extra ->
                sendBroadcast(Intent("MUSIC_ERROR").apply {
                    putExtra("what", what)
                    putExtra("extra", extra)
                })
                true
            }
        }
        
        // Initialize notification manager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val songPath = intent.getStringExtra(EXTRA_SONG_PATH)
                if (songPath != null) {
                    playSong(songPath)
                }
            }
            ACTION_PAUSE -> {
                pauseSong()
            }
            ACTION_RESUME -> {
                resumeSong()
            }
            ACTION_STOP -> {
                stopSong()
            }
            ACTION_SEEK_TO -> {
                val position = intent.getIntExtra(EXTRA_SEEK_POSITION, 0)
                seekTo(position)
            }
            ACTION_NEXT -> {
                // Send broadcast for next song
                sendBroadcast(Intent(ACTION_NEXT))
            }
            ACTION_PREVIOUS -> {
                // Send broadcast for previous song
                sendBroadcast(Intent(ACTION_PREVIOUS))
            }
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    /**
     * ចាក់ចម្រៀងថ្មី
     */
    fun playSong(songPath: String) {
        try {
            // Reset if playing another song
            if (currentSongPath != songPath) {
                mediaPlayer?.reset()
                mediaPlayer?.setDataSource(songPath)
                mediaPlayer?.prepareAsync()
                currentSongPath = songPath
            } else {
                // Same song, just play
                if (!mediaPlayer!!.isPlaying) {
                    mediaPlayer?.start()
                    updateNotification()
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            sendBroadcast(Intent("MUSIC_ERROR").apply {
                putExtra("error", "មិនអាចបើកឯកសារចម្រៀង")
            })
        }
    }
    
    /**
     * ផ្អាកចម្រៀង
     */
    fun pauseSong() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
            updateNotification()
            sendBroadcast(Intent("MUSIC_PAUSED"))
        }
    }
    
    /**
     * បន្តចាក់ចម្រៀង
     */
    fun resumeSong() {
        if (!mediaPlayer?.isPlaying!! && isPrepared) {
            mediaPlayer?.start()
            updateNotification()
            sendBroadcast(Intent("MUSIC_RESUMED"))
        }
    }
    
    /**
     * ឈប់ចាក់ចម្រៀង
     */
    fun stopSong() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.reset()
            isPrepared = false
            currentSongPath = null
        }
        stopForeground(true)
        stopSelf()
        sendBroadcast(Intent("MUSIC_STOPPED"))
    }
    
    /**
     * រំកិលទៅកន្លែងចង់ស្តាប់
     */
    fun seekTo(position: Int) {
        if (isPrepared && position in 0..(mediaPlayer?.duration ?: 0)) {
            mediaPlayer?.seekTo(position)
            sendBroadcast(Intent("MUSIC_SEEKED").apply {
                putExtra("position", position)
            })
        }
    }
    
    /**
     * ទទួលបានពេលវេលាបច្ចុប្បន្ន
     */
    fun getCurrentPosition(): Int {
        return mediaPlayer?.currentPosition ?: 0
    }
    
    /**
     * ទទួលបានពេលវេលាសរុប
     */
    fun getDuration(): Int {
        return if (isPrepared) mediaPlayer?.duration ?: 0 else 0
    }
    
    /**
     * តើកំពុងចាក់ឬទេ
     */
    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying ?: false
    }
    
    /**
     * តើបានត្រៀមរួចឬនៅ
     */
    fun isSongPrepared(): Boolean {
        return isPrepared
    }
    
    /**
     * បិទសេវាកម្ម
     */
    fun shutdown() {
        mediaPlayer?.release()
        mediaPlayer = null
        stopForeground(true)
        stopSelf()
    }
    
    /**
     * នៅពេលចម្រៀងចប់
     */
    override fun onCompletion(mp: MediaPlayer?) {
        sendBroadcast(Intent("MUSIC_COMPLETED"))
        
        // Auto play next if needed
        val sharedPrefs = getSharedPreferences("music_prefs", Context.MODE_PRIVATE)
        if (sharedPrefs.getBoolean("auto_next", true)) {
            sendBroadcast(Intent(ACTION_NEXT))
        }
    }
    
    /**
     * បង្កើត Notification Channel
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Player",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "ការជូនដំណឹងសម្រាប់កម្មវិធីចាក់ចម្រៀង"
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * បង្កើត Notification
     */
    private fun createNotification(isPlaying: Boolean): Notification {
        // Intent for opening app
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Previous button intent
        val prevIntent = Intent(this, MusicService::class.java).apply {
            action = ACTION_PREVIOUS
        }
        val pendingPrevIntent = PendingIntent.getService(
            this, 1, prevIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Play/Pause button intent
        val playPauseIntent = Intent(this, MusicService::class.java).apply {
            action = if (isPlaying) ACTION_PAUSE else ACTION_RESUME
        }
        val pendingPlayPauseIntent = PendingIntent.getService(
            this, 2, playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Next button intent
        val nextIntent = Intent(this, MusicService::class.java).apply {
            action = ACTION_NEXT
        }
        val pendingNextIntent = PendingIntent.getService(
            this, 3, nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Stop button intent
        val stopIntent = Intent(this, MusicService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStopIntent = PendingIntent.getService(
            this, 4, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Create notification
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("កំពុងចាក់ចម្រៀង")
            .setContentText(currentSongPath?.split("/")?.last()?.replace(".mp3", "") ?: "ចម្រៀង")
            .setSmallIcon(R.drawable.ic_music_note)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.ic_music_note))
            .setContentIntent(pendingOpenIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setWhen(System.currentTimeMillis())
            
            // Add action buttons
            .addAction(
                android.R.drawable.ic_media_previous,
                "មុន",
                pendingPrevIntent
            )
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "ផ្អាក" else "ចាក់",
                pendingPlayPauseIntent
            )
            .addAction(
                android.R.drawable.ic_media_next,
                "បន្ទាប់",
                pendingNextIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "ឈប់",
                pendingStopIntent
            )
            
            .build()
    }
    
    /**
     * អាប់ដេត Notification
     */
    private fun updateNotification() {
        val isPlaying = mediaPlayer?.isPlaying ?: false
        val notification = createNotification(isPlaying)
        
        // Start foreground service
        startForeground(NOTIFICATION_ID, notification)
    }
    
    /**
     * នៅពេលដក Service ចេញពី Foreground
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        shutdown()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
    
    companion object {
        // Actions
        const val ACTION_PLAY = "com.offlinemusic.player.ACTION_PLAY"
        const val ACTION_PAUSE = "com.offlinemusic.player.ACTION_PAUSE"
        const val ACTION_RESUME = "com.offlinemusic.player.ACTION_RESUME"
        const val ACTION_STOP = "com.offlinemusic.player.ACTION_STOP"
        const val ACTION_SEEK_TO = "com.offlinemusic.player.ACTION_SEEK_TO"
        const val ACTION_NEXT = "com.offlinemusic.player.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.offlinemusic.player.ACTION_PREVIOUS"
        
        // Extras
        const val EXTRA_SONG_PATH = "song_path"
        const val EXTRA_SEEK_POSITION = "seek_position"
        
        /**
         * ចាប់ផ្តើមសេវាកម្ម
         */
        fun startService(context: Context) {
            val intent = Intent(context, MusicService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        /**
         * បញ្ឈប់សេវាកម្ម
         */
        fun stopService(context: Context) {
            val intent = Intent(context, MusicService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
