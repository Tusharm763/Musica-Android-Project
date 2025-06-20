package com.musicfirebase.app

//noinspection SuspiciousImport
import android.R
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.musicfirebase.app.databinding.ActivityPlayerBinding
import com.musicfirebase.app.model.Song
import com.musicfirebase.app.service.MusicNotificationService

class PlayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPlayerBinding
    private var exoPlayer: ExoPlayer? = null
    private var songs: ArrayList<Song> = arrayListOf()
    private var currentSongPosition = 0
    private lateinit var audioManager: AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var updateSeekBarRunnable: Runnable? = null
    private var isUserSeeking = false
    private var currentVolume = 0.6f
    private var notificationService: MusicNotificationService? = null
    private var isServiceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicNotificationService.MusicBinder
            notificationService = binder.getService()
            isServiceBound = true
            updateNotification()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            notificationService = null
            isServiceBound = false
        }
    }

    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.musicfirebase.app.PLAY_PAUSE" -> {
                    togglePlayPause()
                }
                "com.musicfirebase.app.PREVIOUS" -> {
                    exoPlayer?.seekToPrevious()
                }
                "com.musicfirebase.app.NEXT" -> {
                    exoPlayer?.seekToNext()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        @Suppress("UNCHECKED_CAST")
        songs = intent.getSerializableExtra("songs_list") as? ArrayList<Song> ?: arrayListOf()
        currentSongPosition = intent.getIntExtra("song_position", 0)

        initializePlayer()
        setupUI()
        setupClickListeners()
        setupSeekBar()
        setupVolumeControls()
        startSeekBarUpdate()

        startNotificationService()

        registerNotificationReceiver()
    }

    private fun startNotificationService() {
        val serviceIntent = Intent(this, MusicNotificationService::class.java)
        startService(serviceIntent)
        bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE)
    }

    private fun registerNotificationReceiver() {
        val intentFilter = IntentFilter().apply {
            addAction("com.musicfirebase.app.PLAY_PAUSE")
            addAction("com.musicfirebase.app.PREVIOUS")
            addAction("com.musicfirebase.app.NEXT")
        }
        registerReceiver(notificationReceiver, intentFilter, RECEIVER_NOT_EXPORTED)
    }

    private fun updateNotification() {
        if (isServiceBound && songs.isNotEmpty()) {
            val currentSong = songs.getOrNull(currentSongPosition)
            val currentPos = exoPlayer?.currentPosition ?: 0
            val duration = exoPlayer?.duration ?: 0
            val isPlaying = exoPlayer?.isPlaying == true //?: false

            notificationService?.updateNotification(currentSong, isPlaying, currentPos, duration)
        }
    }

    private fun initializePlayer() {
        exoPlayer = ExoPlayer.Builder(this).build()

        val mediaItems = songs.map { song -> MediaItem.fromUri(song.url) }

        exoPlayer?.apply {
            setMediaItems(mediaItems)
            seekToDefaultPosition(currentSongPosition)
            prepare()
            addListener(playerListener)
            volume = currentVolume
        }
    }

    private fun setupUI() {
        if (songs.isNotEmpty()) {
            binding.textSongName.text = songs[currentSongPosition].name
        }
        updateVolumeDisplay()
    }

    private fun setupSeekBar() {
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) binding.textCurrentTime.text = formatTime(progress.toLong())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = true
                stopSeekBarUpdate()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = false
                exoPlayer?.seekTo(seekBar?.progress?.toLong() ?: 0)
                startSeekBarUpdate()
                updateNotification()
            }
        })
    }

    private fun setupVolumeControls() {
        binding.volumeSeekBar.apply {
            max = 100
            progress = (currentVolume * 100).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        currentVolume = progress / 100f
                        exoPlayer?.volume = currentVolume
                        updateVolumeDisplay()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        binding.buttonVolumeUp.setOnClickListener {
            adjustVolume(0.1f)
        }
        binding.buttonVolumeDown.setOnClickListener {
            adjustVolume(-0.1f)
        }
        binding.buttonMute.setOnClickListener {
            toggleMute()
        }
    }

    private fun adjustVolume(delta: Float) {
        currentVolume = (currentVolume + delta).coerceIn(0f, 1f)
        exoPlayer?.volume = currentVolume
        binding.volumeSeekBar.progress = (currentVolume * 100).toInt()
        updateVolumeDisplay()

        val volumePercent = (currentVolume * 100).toInt()
        Toast.makeText(this, "Volume: $volumePercent%", Toast.LENGTH_SHORT).show()
    }

    private var previousVolume = 0.5f
    private var isMuted = false

    private fun toggleMute() {
        if (isMuted) {
            currentVolume = previousVolume
            exoPlayer?.volume = currentVolume
            binding.buttonMute.setImageResource(R.drawable.ic_lock_silent_mode_off)
            isMuted = false
            Toast.makeText(this, "Un Muted", Toast.LENGTH_SHORT).show()
        } else {
            previousVolume = currentVolume
            currentVolume = 0f
            exoPlayer?.volume = currentVolume
            binding.buttonMute.setImageResource(R.drawable.ic_lock_silent_mode)
            isMuted = true
            Toast.makeText(this, "Muted", Toast.LENGTH_SHORT).show()
        }

        binding.volumeSeekBar.progress = (currentVolume * 100).toInt()
        updateVolumeDisplay()
    }

    @SuppressLint("SetTextI18n")
    private fun updateVolumeDisplay() {
        val volumePercent = (currentVolume * 100).toInt()
        binding.textVolumeLevel.text = "$volumePercent%"
    }

    private fun startSeekBarUpdate() {
        updateSeekBarRunnable = object : Runnable {
            override fun run() {
                if (!isUserSeeking && exoPlayer != null) {
                    val currentPosition = exoPlayer?.currentPosition ?: 0
                    val duration = exoPlayer?.duration ?: 0

                    if (duration > 0) {
                        binding.seekBar.max = duration.toInt()
                        binding.seekBar.progress = currentPosition.toInt()

                        binding.textCurrentTime.text = formatTime(currentPosition)
                        binding.textTotalTime.text = formatTime(duration)

                        updateNotification()
                    }
                }
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(updateSeekBarRunnable!!)
    }

    private fun stopSeekBarUpdate() {
        updateSeekBarRunnable?.let { handler.removeCallbacks(it) }
    }

    @SuppressLint("DefaultLocale")
    private fun formatTime(milliseconds: Long): String {
        val totalSeconds = milliseconds / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    private fun setupClickListeners() {
        binding.buttonPlayPause.setOnClickListener {
            togglePlayPause()
        }

        binding.buttonPrevious.setOnClickListener {
            exoPlayer?.seekToPrevious()
        }

        binding.buttonNext.setOnClickListener {
            exoPlayer?.seekToNext()
        }

        binding.buttonFastForward.setOnClickListener {
            val currentPosition = exoPlayer?.currentPosition ?: 0
            val newPosition = currentPosition + 10000
            exoPlayer?.seekTo(newPosition)
            Toast.makeText(this, "+10s", Toast.LENGTH_SHORT).show()
            updateNotification()
        }

        binding.buttonRewind.setOnClickListener {
            val currentPosition = exoPlayer?.currentPosition ?: 0
            val newPosition = (currentPosition - 10000).coerceAtLeast(0)
            exoPlayer?.seekTo(newPosition)
            Toast.makeText(this, "-10s", Toast.LENGTH_SHORT).show()
            updateNotification()
        }
    }

    private fun togglePlayPause() {
        if (exoPlayer?.isPlaying == true) {
            exoPlayer?.pause()
            binding.buttonPlayPause.setImageResource(R.drawable.ic_media_play)
        } else {
            exoPlayer?.play()
            binding.buttonPlayPause.setImageResource(R.drawable.ic_media_pause)
        }
        updateNotification()
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    binding.buttonPlayPause.setImageResource(
                        if (exoPlayer?.isPlaying == true) R.drawable.ic_media_pause
                        else R.drawable.ic_media_play
                    )

                    val duration = exoPlayer?.duration ?: 0
                    if (duration > 0) {
                        binding.seekBar.max = duration.toInt()
                        binding.textTotalTime.text = formatTime(duration)
                    }
                    updateNotification()
                }

                Player.STATE_BUFFERING -> {
//                    binding.progressBar.visibility = View.VISIBLE
                }

                Player.STATE_ENDED -> {
                    exoPlayer?.seekToNext()
                }

                Player.STATE_IDLE -> {
                    binding.progressBar.visibility = View.GONE
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e("ExoPlayer", "Playback error: ${error.message}", error)
            Toast.makeText(this@PlayerActivity, "Playback error: ${error.message}", Toast.LENGTH_SHORT).show()
        }

        @SuppressLint("SetTextI18n")
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            currentSongPosition = exoPlayer?.currentMediaItemIndex ?: 0
            if (currentSongPosition < songs.size) {
                binding.textSongName.text = songs[currentSongPosition].name

                binding.seekBar.progress = 0
                binding.textCurrentTime.text = "0:00"
                binding.textTotalTime.text = "0:00"

                updateNotification()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            binding.buttonPlayPause.setImageResource(
                if (isPlaying) R.drawable.ic_media_pause else R.drawable.ic_media_play
            )
            updateNotification()
        }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        return when (keyCode) {
            android.view.KeyEvent.KEYCODE_VOLUME_UP -> {
                adjustVolume(0.1f)
                true
            }
            android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> {
                adjustVolume(-0.1f)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onPause() {
        super.onPause()
        stopSeekBarUpdate()
    }

    override fun onResume() {
        super.onResume()
        startSeekBarUpdate()
    }

    @SuppressLint("ImplicitSamInstance")
    override fun onDestroy() {
        super.onDestroy()
        stopSeekBarUpdate()

        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
        stopService(Intent(this, MusicNotificationService::class.java))
        unregisterReceiver(notificationReceiver)
        exoPlayer?.release()
        exoPlayer = null
    }
}
