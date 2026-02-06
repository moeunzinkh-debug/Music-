package com.offlinemusic.player

import android.media.MediaPlayer
import android.os.*
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.offlinemusic.player.databinding.ActivityMainBinding
import java.util.*
import kotlin.concurrent.timerTask

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var mediaPlayer: MediaPlayer
    private var isPlaying = false
    private var timer: Timer? = null
    private var sleepTimer: Timer? = null
    private var currentSongIndex = 0
    
    // បញ្ជីចម្រៀងគំរូ (អ្នកអាចបន្ថែមតាមតម្រូវការ)
    private val sampleSongs = listOf(
        "ចម្រៀងទី ១ - សំលេងសប្បាយ",
        "ចម្រៀងទី ២ - សំនៀងអារម្មណ៍",
        "ចម្រៀងទី ៣ - បទពីដើម",
        "ចម្រៀងទី ៤ - ចងចាំ",
        "ចម្រៀងទី ៥ - សុបិន្ត"
    )
    
    // បញ្ជីពណ៌ Gradient
    private val gradients = listOf(
        intArrayOf(0xFF6A11CB.toInt(), 0xFF2575FC.toInt()),  // ពណ៌ធ្លាក់ទឹកកក
        intArrayOf(0xFFFAD961.toInt(), 0xFFF76B1C.toInt()),  // ពណ៌ព្រឹកល្ងាច
        intArrayOf(0xFFF093FB.toInt(), 0xFFF5576C.toInt()),  // ពណ៌ផ្កាឈូក
        intArrayOf(0xFF4FACFE.toInt(), 0xFF00F2FE.toInt()),  // ពណ៌មេឃ
        intArrayOf(0xFF43E97B.toInt(), 0xFF38F9D7.toInt()),  // ពណ៌ព្រៃ
        intArrayOf(0xFFFA709A.toInt(), 0xFFFEE140.toInt())   // ពណ៌ផ្កាឈូករសៀល
    )
    private var currentGradient = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // លាក់ status bar ដើម្បីបង្ហាញពេញអេក្រង់
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        
        // បង្កើត MediaPlayer
        mediaPlayer = MediaPlayer.create(this, R.raw.sample)
        if (mediaPlayer == null) {
            Toast.makeText(this, "មិនអាចបង្កើត MediaPlayer បានទេ", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        setupUI()
        setupListeners()
        updateSongInfo()
    }
    
    private fun setupUI() {
        // កំណត់ព័ត៌មានចម្រៀង
        binding.tvSongTitle.text = sampleSongs[currentSongIndex]
        
        // កំណត់ seekbar
        binding.seekBar.max = mediaPlayer.duration
        
        // ចាប់ផ្តើមអាប់ដេតពេលវេលា
        startProgressUpdate()
        
        // បង្ហាញពេលវេលាសរុប
        binding.tvTotalTime.text = formatTime(mediaPlayer.duration)
    }
    
    private fun setupListeners() {
        // ប៊ូតុងចាក់/ផ្អាក
        binding.btnPlayPause.setOnClickListener {
            if (isPlaying) {
                pauseMusic()
            } else {
                playMusic()
            }
        }
        
        // ប៊ូតុងចម្រៀងបន្ទាប់
        binding.btnNext.setOnClickListener {
            playNextSong()
        }
        
        // ប៊ូតុងចម្រៀងមុន
        binding.btnPrev.setOnClickListener {
            playPreviousSong()
        }
        
        // ម៉ោងគេង
        binding.btnSleep15.setOnClickListener { setSleepTimer(15) }
        binding.btnSleep30.setOnClickListener { setSleepTimer(30) }
        binding.btnSleep60.setOnClickListener { setSleepTimer(60) }
        binding.btnCancelSleep.setOnClickListener { cancelSleepTimer() }
        
        // ផ្លាស់ប្តូរពណ៌
        binding.btnChangeTheme.setOnClickListener {
            changeGradientTheme()
        }
        
        // Seekbar listener
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && mediaPlayer.isPlaying) {
                    mediaPlayer.seekTo(progress)
                }
                binding.tvCurrentTime.text = formatTime(progress)
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // គ្មានអ្វីត្រូវធ្វើ
            }
            
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // គ្មានអ្វីត្រូវធ្វើ
            }
        })
    }
    
    private fun playMusic() {
        try {
            if (!mediaPlayer.isPlaying) {
                mediaPlayer.start()
                isPlaying = true
                binding.btnPlayPause.text = "⏸"
                
                // ផ្លាស់ប្តូរពណ៌ប៊ូតុង
                binding.btnPlayPause.setBackgroundColor(
                    ContextCompat.getColor(this, R.color.pause_button)
                )
                
                startProgressUpdate()
                Toast.makeText(this, "កំពុងចាក់ចម្រៀង", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "មិនអាចចាក់ចម្រៀងបានទេ", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun pauseMusic() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
            isPlaying = false
            binding.btnPlayPause.text = "▶"
            
            // ផ្លាស់ប្តូរពណ៌ប៊ូតុង
            binding.btnPlayPause.setBackgroundColor(
                ContextCompat.getColor(this, R.color.play_button)
            )
            
            timer?.cancel()
            Toast.makeText(this, "ផ្អាកចម្រៀង", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun playNextSong() {
        currentSongIndex = (currentSongIndex + 1) % sampleSongs.size
        restartPlayer()
        Toast.makeText(this, "ចម្រៀងបន្ទាប់", Toast.LENGTH_SHORT).show()
    }
    
    private fun playPreviousSong() {
        currentSongIndex = if (currentSongIndex - 1 < 0) {
            sampleSongs.size - 1
        } else {
            currentSongIndex - 1
        }
        restartPlayer()
        Toast.makeText(this, "ចម្រៀងមុន", Toast.LENGTH_SHORT).show()
    }
    
    private fun restartPlayer() {
        mediaPlayer.reset()
        mediaPlayer = MediaPlayer.create(this, R.raw.sample)
        updateSongInfo()
        
        if (isPlaying) {
            mediaPlayer.start()
        }
    }
    
    private fun updateSongInfo() {
        binding.tvSongTitle.text = sampleSongs[currentSongIndex]
        binding.seekBar.max = mediaPlayer.duration
        binding.tvTotalTime.text = formatTime(mediaPlayer.duration)
        binding.tvCurrentTime.text = "00:00"
        binding.seekBar.progress = 0
    }
    
    private fun startProgressUpdate() {
        timer?.cancel()
        timer = Timer()
        
        timer?.scheduleAtFixedRate(timerTask {
            runOnUiThread {
                if (mediaPlayer.isPlaying) {
                    val currentPos = mediaPlayer.currentPosition
                    binding.seekBar.progress = currentPos
                    binding.tvCurrentTime.text = formatTime(currentPos)
                    
                    // ប្រសិនបើចម្រៀងចប់ បើកចម្រៀងបន្ទាប់
                    if (currentPos >= mediaPlayer.duration - 100) {
                        playNextSong()
                    }
                }
            }
        }, 0, 1000)
    }
    
    private fun formatTime(milliseconds: Int): String {
        val totalSeconds = milliseconds / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
    
    private fun setSleepTimer(minutes: Int) {
        cancelSleepTimer()
        
        binding.tvSleepTimer.text = "ម៉ោងគេង: $minutes នាទី"
        binding.tvSleepTimer.visibility = View.VISIBLE
        
        Toast.makeText(this, "កំណត់ម៉ោងគេង $minutes នាទី", Toast.LENGTH_SHORT).show()
        
        sleepTimer = Timer()
        sleepTimer?.schedule(timerTask {
            runOnUiThread {
                pauseMusic()
                binding.tvSleepTimer.text = "ឈប់ចាក់ហើយ!"
                
                // លុបអត្ថបទបន្ទាប់ពី ៣ វិនាទី
                Handler(Looper.getMainLooper()).postDelayed({
                    binding.tvSleepTimer.visibility = View.GONE
                }, 3000)
                
                Toast.makeText(this@MainActivity, 
                    "ម៉ោងគេងចប់! ចម្រៀងត្រូវបានឈប់", 
                    Toast.LENGTH_LONG).show()
            }
        }, minutes * 60 * 1000L)
    }
    
    private fun cancelSleepTimer() {
        sleepTimer?.cancel()
        binding.tvSleepTimer.visibility = View.GONE
        Toast.makeText(this, "បោះបង់ម៉ោងគេង", Toast.LENGTH_SHORT).show()
    }
    
    private fun changeGradientTheme() {
        currentGradient = (currentGradient + 1) % gradients.size
        val gradient = gradients[currentGradient]
        
        // បង្កើត GradientDrawable
        val gradientDrawable = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            gradient
        )
        gradientDrawable.cornerRadius = 0f
        gradientDrawable.gradientType = android.graphics.drawable.GradientDrawable.LINEAR_GRADIENT
        
        // អនុវត្ត gradient ថ្មី
        binding.mainLayout.background = gradientDrawable
        
        // ធ្វើអោយមានចលនា
        binding.mainLayout.animate()
            .scaleX(1.05f)
            .scaleY(1.05f)
            .setDuration(300)
            .withEndAction {
                binding.mainLayout.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(300)
                    .start()
            }
            .start()
        
        Toast.makeText(this, "ផ្លាស់ប្តូរពណ៌ថ្មី", Toast.LENGTH_SHORT).show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        sleepTimer?.cancel()
        mediaPlayer.release()
    }
}
