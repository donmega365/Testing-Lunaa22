package com.luna.assistant
import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import android.speech.*
import android.speech.tts.TextToSpeech
import java.util.Locale

class LunaService : Service() {
    private lateinit var tts: TextToSpeech
    private var sr: SpeechRecognizer? = null
    private val h = Handler(Looper.getMainLooper())
    private var awaiting = false
    private var alive = true

    override fun onCreate() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("luna", "Luna", NotificationManager.IMPORTANCE_LOW))
        startForeground(1, Notification.Builder(this, "luna").setContentTitle("Luna sun rahi hai").setSmallIcon(android.R.drawable.ic_btn_speak_now).build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        tts = TextToSpeech(this) { tts.language = Locale.getDefault(); listen() }
    }
    private fun say(s: String) { h.post { tts.speak(s, TextToSpeech.QUEUE_ADD, null, null) } }
    private fun process(t: String) {
        val l = t.lowercase()
        if (!l.contains("luna") && !awaiting) return
        val cmd = l.replace(Regex("(hello|hey|hi)?\\s*luna"), "").trim()
        if (cmd.isEmpty()) { awaiting = true; say("Yes Sir"); return }
        awaiting = false
        Actions.handle(this, cmd, ::say)
    }
    private fun listen() {
        if (!alive) return
        sr?.destroy()
        sr = SpeechRecognizer.createSpeechRecognizer(this).also { r ->
            r.setRecognitionListener(object : RecognitionListener {
                override fun onResults(b: Bundle?) { b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { process(it) }; h.postDelayed({ listen() }, 400) }
                override fun onError(e: Int) { h.postDelayed({ listen() }, 1000) }
                override fun onReadyForSpeech(p: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(b: Bundle?) {}
                override fun onEvent(t: Int, b: Bundle?) {}
            })
            r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true))
        }
    }
    override fun onStartCommand(i: Intent?, f: Int, s: Int) = START_STICKY
    override fun onBind(i: Intent?): IBinder? = null
    override fun onDestroy() { alive = false; sr?.destroy(); tts.shutdown() }
}
