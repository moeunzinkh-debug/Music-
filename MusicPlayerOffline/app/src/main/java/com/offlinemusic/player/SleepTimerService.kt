package com.offlinemusic.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.concurrent.TimeUnit

class SleepTimerService : Service() {
    
    // អថេរសំខាន់ៗ
    private var countDownTimer: CountDownTimer? = null
    private var remainingTimeMillis: Long = 0
    private var isTimerRunning = false
    private var timerDurationMinutes = 0
    
    // Callback interface
    interface SleepTimerListener {
        fun onTimerTick(remainingMillis: Long)
        fun onTimerFinish()
        fun onTimerCancel()
    }
    
    private var listener: SleepTimerListener? = null
    
    // Binder for activity binding
    inner class SleepTimerBinder : Binder() {
        fun getService(): SleepTimerService = this@SleepTimerService
    }
    
    private val binder = SleepTimerBinder()
    
    // Notification
    private val CHANNEL_ID = "sleep_timer_channel"
    private val NOTIFICATION_ID = 102
    private lateinit var notificationManager: NotificationManager
    
    companion object {
        // Actions
        const val ACTION_START_TIMER = "com.offlinemusic.player.START_TIMER"
        const val ACTION_STOP_TIMER = "com.offlinemusic.player.STOP_TIMER"
        const val ACTION_PAUSE_TIMER = "com.offlinemusic.player.PAUSE_TIMER"
        const val ACTION_RESUME_TIMER = "com.offlinemusic.player.RESUME_TIMER"
        const val ACTION_UPDATE_TIMER = "com.offlinemusic.player.UPDATE_TIMER"
        
        // Extras
        const val EXTRA_MINUTES = "extra_minutes"
        const val EXTRA_REMAINING_MILLIS = "extra_remaining_millis"
        
        // Broadcast actions
        const val BROADCAST_TIMER_TICK = "com.offlinemusic.player.TIMER_TICK"
        const val BROADCAST_TIMER_FINISH = "com.offlinemusic.player.TIMER_FINISH"
        const val BROADCAST_TIMER_CANCEL = "com.offlinemusic.player.TIMER_CANCEL"
        
        /**
         * ចាប់ផ្តើមសេវាកម្ម
         */
        fun startService(context: Context, minutes: Int) {
            val intent = Intent(context, SleepTimerService::class.java).apply {
                action = ACTION_START_TIMER
                putExtra(EXTRA_MINUTES, minutes)
            }
            
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
            val intent = Intent(context, SleepTimerService::class.java).apply {
                action = ACTION_STOP_TIMER
            }
            context.startService(intent)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d("SleepTimerService", "Service onCreate")
        
        // Initialize notification manager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        
        // Start as foreground service
        startForeground(NOTIFICATION_ID, createNotification(0, false))
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("SleepTimerService", "onStartCommand: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START_TIMER -> {
                val minutes = intent.getIntExtra(EXTRA_MINUTES, 15)
                startTimer(minutes)
            }
            
            ACTION_STOP_TIMER -> {
                stopTimer()
                stopSelf()
            }
            
            ACTION_PAUSE_TIMER -> {
                pauseTimer()
            }
            
            ACTION_RESUME_TIMER -> {
                val remaining = intent.getLongExtra(EXTRA_REMAINING_MILLIS, 0)
                if (remaining > 0) {
                    resumeTimer(remaining)
                }
            }
            
            ACTION_UPDATE_TIMER -> {
                updateNotification()
            }
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    /**
     * ចាប់ផ្តើមម៉ោងគេង
     */
    fun startTimer(minutes: Int) {
        Log.d("SleepTimerService", "Starting timer for $minutes minutes")
        
        // Cancel existing timer if any
        countDownTimer?.cancel()
        
        timerDurationMinutes = minutes
        remainingTimeMillis = minutes * 60 * 1000L
        
        createCountDownTimer(remainingTimeMillis).start()
        isTimerRunning = true
        
        updateNotification()
        sendTimerStartedBroadcast(minutes)
        
        Log.d("SleepTimerService", "Timer started: $remainingTimeMillis ms")
    }
    
    /**
     * បង្កើត CountDownTimer
     */
    private fun createCountDownTimer(durationMillis: Long): CountDownTimer {
        return object : CountDownTimer(durationMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                remainingTimeMillis = millisUntilFinished
                
                // Update notification every 30 seconds or when less than 1 minute
                if (millisUntilFinished % 30000 == 0L || millisUntilFinished < 60000) {
                    updateNotification()
                }
                
                // Send broadcast for UI updates
                sendTimerTickBroadcast(millisUntilFinished)
                
                // Log every minute for debugging
                if (millisUntilFinished % 60000 == 0L) {
                    val minutesLeft = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished)
                    Log.d("SleepTimerService", "Timer tick: $minutesLeft minutes left")
                }
            }
            
            override fun onFinish() {
                Log.d("SleepTimerService", "Timer finished")
                
                remainingTimeMillis = 0
                isTimerRunning = false
                
                // Send finish broadcast
                sendTimerFinishBroadcast()
                
                // Update notification
                updateNotification()
                
                // Auto-stop music if service is available
                autoStopMusic()
                
                // Stop service after 5 seconds
                Handler(mainLooper).postDelayed({
                    stopSelf()
                }, 5000)
            }
        }
    }
    
    /**
     * ផ្អាកម៉ោងគេង
     */
    fun pauseTimer() {
        if (isTimerRunning && remainingTimeMillis > 0) {
            countDownTimer?.cancel()
            isTimerRunning = false
            
            updateNotification()
            sendTimerPausedBroadcast()
            
            Log.d("SleepTimerService", "Timer paused: $remainingTimeMillis ms left")
        }
    }
    
    /**
     * បន្តម៉ោងគេង
     */
    fun resumeTimer(remainingMillis: Long) {
        if (!isTimerRunning && remainingMillis > 0) {
            remainingTimeMillis = remainingMillis
            
            createCountDownTimer(remainingMillis).start()
            isTimerRunning = true
            
            updateNotification()
            sendTimerResumedBroadcast()
            
            Log.d("SleepTimerService", "Timer resumed: $remainingMillis ms left")
        }
    }
    
    /**
     * បញ្ឈប់ម៉ោងគេង
     */
    fun stopTimer() {
        countDownTimer?.cancel()
        isTimerRunning = false
        remainingTimeMillis = 0
        
        updateNotification()
        sendTimerCancelBroadcast()
        
        Log.d("SleepTimerService", "Timer stopped")
    }
    
    /**
     * ទទួលបានពេលវេលានៅសល់ (គិតជានាទី)
     */
    fun getRemainingMinutes(): Int {
        return TimeUnit.MILLISECONDS.toMinutes(remainingTimeMillis).toInt()
    }
    
    /**
     * ទទួលបានពេលវេលានៅសល់ (គិតជាមីល្លីវិនាទី)
     */
    fun getRemainingMillis(): Long {
        return remainingTimeMillis
    }
    
    /**
     * តើម៉ោងគេងកំពុងដំណើរការឬទេ
     */
    fun isTimerActive(): Boolean {
        return isTimerRunning || remainingTimeMillis > 0
    }
    
    /**
     * តើម៉ោងគេងកំពុងដំណើរការឬទេ
     */
    fun isTimerRunning(): Boolean {
        return isTimerRunning
    }
    
    /**
     * កំណត់ listener
     */
    fun setSleepTimerListener(listener: SleepTimerListener) {
        this.listener = listener
    }
    
    /**
     * បង្កើត Notification Channel
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ម៉ោងគេង",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "ការជូនដំណឹងសម្រាប់ម៉ោងគេង"
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * បង្កើត Notification
     */
    private fun createNotification(remainingMinutes: Int, isRunning: Boolean): Notification {
        // Intent for opening app
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Stop button intent
        val stopIntent = Intent(this, SleepTimerService::class.java).apply {
            action = ACTION_STOP_TIMER
        }
        val pendingStopIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Pause/Resume button intent
        val pauseResumeIntent = Intent(this, SleepTimerService::class.java).apply {
            action = if (isRunning && remainingMinutes > 0) {
                ACTION_PAUSE_TIMER
            } else if (remainingMinutes > 0) {
                putExtra(EXTRA_REMAINING_MILLIS, remainingTimeMillis)
                ACTION_RESUME_TIMER
            } else {
                ACTION_STOP_TIMER
            }
        }
        val pendingPauseResumeIntent = PendingIntent.getService(
            this, 2, pauseResumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Format time string
        val timeString = if (remainingMinutes >= 60) {
            val hours = remainingMinutes / 60
            val minutes = remainingMinutes % 60
            "${hours}ម ${minutes}ន"
        } else {
            "${remainingMinutes}ន"
        }
        
        // Create notification
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ម៉ោងគេង")
            .setContentText(
                if (isRunning) "នៅសល់: $timeString" 
                else if (remainingMinutes > 0) "ផ្អាក: $timeString"
                else "បានបញ្ចប់"
            )
            .setSmallIcon(R.drawable.ic_sleep)
            .setContentIntent(pendingOpenIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setWhen(System.currentTimeMillis())
            
            // Add action buttons
            .addAction(
                if (isRunning) R.drawable.ic_pause else R.drawable.ic_play,
                if (isRunning) "ផ្អាក" else "បន្ត",
                pendingPauseResumeIntent
            )
            .addAction(
                R.drawable.ic_stop,
                "ឈប់",
                pendingStopIntent
            )
            
            .build()
    }
    
    /**
     * អាប់ដេត Notification
     */
    private fun updateNotification() {
        val remainingMinutes = getRemainingMinutes()
        val notification = createNotification(remainingMinutes, isTimerRunning)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * ឈប់ចម្រៀងដោយស្វ័យប្រវត្តិ
     */
    private fun autoStopMusic() {
        try {
            // Send broadcast to stop music
            val intent = Intent("STOP_MUSIC_ACTION")
            sendBroadcast(intent)
            
            // Or stop music service directly
            val stopIntent = Intent(this, MusicService::class.java).apply {
                action = MusicService.ACTION_STOP
            }
            startService(stopIntent)
            
            Log.d("SleepTimerService", "Auto-stopped music")
        } catch (e: Exception) {
            Log.e("SleepTimerService", "Error auto-stopping music", e)
        }
    }
    
    /**
     * ផ្ញើ broadcast នៅពេលចាប់ផ្តើមម៉ោងគេង
     */
    private fun sendTimerStartedBroadcast(minutes: Int) {
        val intent = Intent(BROADCAST_TIMER_TICK).apply {
            putExtra("action", "started")
            putExtra("minutes", minutes)
            putExtra("total_minutes", minutes)
        }
        sendBroadcast(intent)
        listener?.onTimerTick(remainingTimeMillis)
    }
    
    /**
     * ផ្ញើ broadcast នៅពេលម៉ោងគេងដំណើរការ
     */
    private fun sendTimerTickBroadcast(millisUntilFinished: Long) {
        val intent = Intent(BROADCAST_TIMER_TICK).apply {
            putExtra("action", "tick")
            putExtra("remaining_millis", millisUntilFinished)
            putExtra("remaining_minutes", TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished))
        }
        sendBroadcast(intent)
        listener?.onTimerTick(millisUntilFinished)
    }
    
    /**
     * ផ្ញើ broadcast នៅពេលម៉ោងគេងផ្អាក
     */
    private fun sendTimerPausedBroadcast() {
        val intent = Intent(BROADCAST_TIMER_TICK).apply {
            putExtra("action", "paused")
            putExtra("remaining_millis", remainingTimeMillis)
        }
        sendBroadcast(intent)
    }
    
    /**
     * ផ្ញើ broadcast នៅពេលម៉ោងគេងបន្ត
     */
    private fun sendTimerResumedBroadcast() {
        val intent = Intent(BROADCAST_TIMER_TICK).apply {
            putExtra("action", "resumed")
            putExtra("remaining_millis", remainingTimeMillis)
        }
        sendBroadcast(intent)
    }
    
    /**
     * ផ្ញើ broadcast នៅពេលម៉ោងគេងបញ្ចប់
     */
    private fun sendTimerFinishBroadcast() {
        val intent = Intent(BROADCAST_TIMER_FINISH)
        sendBroadcast(intent)
        listener?.onTimerFinish()
    }
    
    /**
     * ផ្ញើ broadcast នៅពេលម៉ោងគេងត្រូវបានបោះបង់
     */
    private fun sendTimerCancelBroadcast() {
        val intent = Intent(BROADCAST_TIMER_CANCEL)
        sendBroadcast(intent)
        listener?.onTimerCancel()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d("SleepTimerService", "Service onDestroy")
        
        // Cancel timer if still running
        countDownTimer?.cancel()
        
        // Remove notification
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
