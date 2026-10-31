package io.github.yuqingmiao2008.grassmachine.core

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import io.github.yuqingmiao2008.grassmachine.data.LangData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

/**
 * 系统 TTS 朗读封装。引擎优先级可配置：优先 Google TTS（若已安装），其次系统默认引擎。
 */
class SystemTts(private val context: Context) {

    companion object {
        const val GOOGLE_TTS_PKG = "com.google.android.tts"
        private const val UTTER_PREFIX = "ggm:"
    }

    data class State(
        val ready: Boolean = false,
        val engineLabel: String = "",
        val speaking: Boolean = false,
        val current: Int = 0,
        val total: Int = 0,
        val message: String = "",
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var tts: TextToSpeech? = null
    private var pendingLines: List<String>? = null
    private var pendingLang: String = "zh-cn"
    private var pendingPref: String = "google_first"
    private var desiredEngine: String? = null

    fun isGoogleTtsInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo(GOOGLE_TTS_PKG, 0)
        true
    }.getOrDefault(false)

    fun init(pref: String) {
        pendingPref = pref
        shutdown()
        desiredEngine = resolveEngine(pref)
        val listener = TextToSpeech.OnInitListener { status -> onInit(status) }
        tts = if (desiredEngine != null) {
            TextToSpeech(context.applicationContext, listener, desiredEngine)
        } else {
            TextToSpeech(context.applicationContext, listener)
        }
    }

    private fun resolveEngine(pref: String): String? {
        val googleOk = isGoogleTtsInstalled()
        return when (pref) {
            "google_first", "google_only" -> if (googleOk) GOOGLE_TTS_PKG else null
            else -> null
        }
    }

    private fun onInit(status: Int) {
        val t = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            // Google 引擎初始化失败时回退系统默认引擎
            if (desiredEngine != null) {
                desiredEngine = null
                runCatching {
                    tts?.shutdown()
                    tts = TextToSpeech(context.applicationContext) { s -> onInit(s) }
                }
                _state.update { it.copy(message = "Google TTS 初始化失败，已回退系统引擎") }
            } else {
                _state.update { it.copy(ready = false, message = "系统 TTS 初始化失败") }
            }
            return
        }
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                val idx = utteranceId?.removePrefix(UTTER_PREFIX)?.toIntOrNull() ?: return
                _state.update { st ->
                    val next = (idx + 1).coerceAtMost(st.total)
                    st.copy(current = next, speaking = next < st.total)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _state.update { it.copy(speaking = false, message = "朗读出错") }
            }
        })
        val engineLabel = runCatching {
            val pkg = t.defaultEngine ?: ""
            if (pkg.isBlank()) return@runCatching "系统默认引擎"
            val app = context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(pkg, 0)
            ).toString()
            "$app ($pkg)"
        }.getOrDefault("系统默认引擎")
        _state.value = State(ready = true, engineLabel = engineLabel)

        pendingLines?.let { lines ->
            pendingLines = null
            speak(lines, pendingLang, 1.0f, 1.0f)
        }
    }

    fun speak(lines: List<String>, langCode: String, rate: Float, pitch: Float) {
        val t = tts
        if (t == null || !_state.value.ready) {
            pendingLines = lines
            pendingLang = langCode
            if (t == null) init(pendingPref)
            return
        }
        val clean = lines.map { it.trim() }.filter { it.isNotEmpty() }
        if (clean.isEmpty()) return
        runCatching {
            t.stop()
            t.setSpeechRate(rate.coerceIn(0.3f, 2.5f))
            t.setPitch(pitch.coerceIn(0.3f, 2.5f))
        }
        val msg = applyLanguage(t, langCode)
        clean.forEachIndexed { i, line ->
            val params = Bundle()
            val queue = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            t.speak(line, queue, params, "$UTTER_PREFIX$i")
        }
        _state.update {
            it.copy(speaking = true, total = clean.size, current = 0, message = msg)
        }
    }

    private fun applyLanguage(t: TextToSpeech, langCode: String): String {
        val res = runCatching { t.setLanguage(LangData.toLocale(langCode)) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            val base = langCode.substringBefore("-")
            val res2 = runCatching { t.setLanguage(Locale(base)) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
            return if (res2 == TextToSpeech.LANG_MISSING_DATA || res2 == TextToSpeech.LANG_NOT_SUPPORTED) {
                "当前语音引擎不支持「${LangData.name(langCode)}」，已用默认语音朗读"
            } else {
                "该语种语音缺失，已用「${LangData.name(base)}」朗读"
            }
        }
        return ""
    }

    fun stop() {
        runCatching { tts?.stop() }
        _state.update { it.copy(speaking = false, current = 0) }
    }

    fun shutdown() {
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        _state.value = State()
    }
}
