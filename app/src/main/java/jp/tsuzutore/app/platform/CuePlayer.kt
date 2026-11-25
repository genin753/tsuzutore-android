package jp.tsuzutore.app.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import jp.tsuzutore.app.domain.AppSettings
import java.util.Locale

/** Uses installed, offline Japanese voices only. No stream volume or ringer changes. */
class CuePlayer(private val context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31)
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    else context.getSystemService(Vibrator::class.java)
    private var speech: TextToSpeech? = null
    private var offlineVoiceReady = false
    private val tone: ToneGenerator? = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 70) }.getOrNull()

    init {
        speech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val tts = speech
                val voice = tts?.voices?.firstOrNull {
                    it.locale.language == Locale.JAPANESE.language && !it.isNetworkConnectionRequired
                }
                if (voice != null) {
                    tts.voice = voice
                    tts.setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    offlineVoiceReady = true
                }
            }
        }
    }

    fun play(text: String, settings: AppSettings, countdown: Boolean = false) {
        val audible = audio.ringerMode == AudioManager.RINGER_MODE_NORMAL
        if (settings.soundEnabled && audible) {
            runCatching { tone?.startTone(if (countdown) ToneGenerator.TONE_PROP_BEEP else ToneGenerator.TONE_PROP_ACK, if (countdown) 100 else 200) }
        }
        if (settings.vibrationEnabled) {
            runCatching { vibrator?.vibrate(VibrationEffect.createOneShot(if (countdown) 60 else 200, VibrationEffect.DEFAULT_AMPLITUDE)) }
        }
        if (settings.voiceEnabled && audible && offlineVoiceReady) {
            runCatching { speech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tsuzutore-cue") }
        }
    }

    fun preview(settings: AppSettings) = play("つづトレ。次のセットを始めましょう", settings)
    fun silence() { speech?.stop(); tone?.stopTone(); vibrator?.cancel() }
}
