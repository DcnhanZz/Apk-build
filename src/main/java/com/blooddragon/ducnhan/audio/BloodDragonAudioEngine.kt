// ╔══════════════════════════════════════════════════════════╗
// ║   Blood Dragon Audio Engine                             ║
// ║   Phonk · EDM · Vinahouse Battle Soundtrack            ║
// ╚══════════════════════════════════════════════════════════╝
package com.blooddragon.ducnhan.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import com.blooddragon.ducnhan.models.AudioTrack
import kotlinx.coroutines.flow.*

private const val TAG = "BloodDragon_Audio"

class BloodDragonAudioEngine(private val context: Context) {

    companion object {
        /** Playlist mặc định — người dùng import file nhạc để kích hoạt playback */
        val DEFAULT_PLAYLIST = listOf(
            AudioTrack("PHONK MODE ACTIVATED", "Phonk 🔥"),
            AudioTrack("GHOST RIDER - DRIFT PHONK", "Phonk 🔥"),
            AudioTrack("MANIAC - PHONK REMIX",      "Phonk 🔥"),
            AudioTrack("EDM BATTLE HYMN v2",         "EDM ⚡"),
            AudioTrack("TECHNO OVERDRIVE 2049",      "EDM ⚡"),
            AudioTrack("BASS CANNON DROP",           "EDM ⚡"),
            AudioTrack("VINAHOUSE ĐỈNH CAO",        "Vinahouse 🎶"),
            AudioTrack("NONSTOP BAY PHÒNG Vol.3",   "Vinahouse 🎶"),
            AudioTrack("CYBERPUNK OST REMIX",        "Synthwave 🌆"),
            AudioTrack("SYNTHWAVE DRAGON THEME",     "Synthwave 🌆")
        )
    }

    private var mediaPlayer: MediaPlayer? = null
    private var currentIndex = 0
    private var currentPlaylist: List<AudioTrack> = DEFAULT_PLAYLIST

    // State
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentTrackName = MutableStateFlow("")
    val currentTrackName: StateFlow<String> = _currentTrackName.asStateFlow()

    private val audioAttrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    // ─── Playback Control ────────────────────────────────────
    fun play(track: AudioTrack, playlist: List<AudioTrack>) {
        currentPlaylist = playlist
        currentIndex = playlist.indexOf(track).coerceAtLeast(0)
        _currentTrackName.value = track.name

        if (track.filePath == null) {
            // Placeholder mode — UI shows "playing" but no actual audio
            // Người dùng cần import file nhạc vào /sdcard/BloodDragon/music/
            Log.d(TAG, "Placeholder track: ${track.name} — import to /sdcard/BloodDragon/music/")
            _isPlaying.value = true
            return
        }

        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(audioAttrs)
                when {
                    track.filePath.startsWith("http") ->
                        setDataSource(track.filePath)
                    else ->
                        setDataSource(context, Uri.parse(track.filePath))
                }
                setOnPreparedListener {
                    start()
                    _isPlaying.value = true
                    Log.i(TAG, "▶ Playing: ${track.name}")
                }
                setOnCompletionListener {
                    Log.d(TAG, "Track completed — auto next")
                    _isPlaying.value = false
                    playNext(currentPlaylist)
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    _isPlaying.value = false
                    false
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Play error: ${e.message}")
        }
    }

    fun playByIndex(index: Int, playlist: List<AudioTrack>) {
        val track = playlist.getOrNull(index) ?: return
        play(track, playlist)
    }

    fun pause() {
        mediaPlayer?.pause()
        _isPlaying.value = false
    }

    fun resume() {
        mediaPlayer?.start()
        _isPlaying.value = true
    }

    fun togglePlay(playlist: List<AudioTrack>) {
        when {
            _isPlaying.value              -> pause()
            mediaPlayer != null           -> resume()
            else                          -> playByIndex(currentIndex, playlist)
        }
    }

    fun playNext(playlist: List<AudioTrack>) {
        currentIndex = (currentIndex + 1) % playlist.size
        playByIndex(currentIndex, playlist)
    }

    fun playPrev(playlist: List<AudioTrack>) {
        currentIndex = if (currentIndex > 0) currentIndex - 1 else playlist.lastIndex
        playByIndex(currentIndex, playlist)
    }

    fun shuffle(playlist: List<AudioTrack>) {
        currentIndex = (playlist.indices).random()
        playByIndex(currentIndex, playlist)
    }

    fun setVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        mediaPlayer?.setVolume(v, v)
    }

    fun getCurrentTrackName(playlist: List<AudioTrack>): String =
        playlist.getOrNull(currentIndex)?.name ?: ""

    fun getCurrentGenre(playlist: List<AudioTrack>): String =
        playlist.getOrNull(currentIndex)?.genre ?: ""

    fun getPositionSec(): Int = (mediaPlayer?.currentPosition ?: 0) / 1000
    fun getDurationSec(): Int = (mediaPlayer?.duration ?: 0) / 1000

    fun seekTo(seconds: Int) {
        mediaPlayer?.seekTo(seconds * 1000)
    }

    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
        _isPlaying.value = false
        Log.d(TAG, "AudioEngine released")
    }
}
