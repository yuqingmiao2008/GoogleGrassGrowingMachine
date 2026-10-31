package io.github.yuqingmiao2008.grassmachine.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 全部可调参数。字段改动会自动持久化。
 */
data class Settings(
    // ---- 生草（翻译）参数 ----
    val times: Int = 8,
    val timeoutSec: Int = 15,
    val retries: Int = 3,
    val concurrency: Int = 8,
    val minIntervalMs: Int = 0,
    val finalLang: String = "zh-cn",
    val randomOrder: Boolean = true,
    val separator: String = "\n。！？!?；;",
    val selectedLangs: Set<String> = LangData.ALL_CODES.toSet(),
    val excludedLangs: Set<String> = emptySet(),

    // ---- 网络 / IP ----
    val useApiKey: Boolean = false,
    val apiKey: String = "",
    val useScannedIp: Boolean = true,
    val ipList: List<String> = emptyList(),
    val activeIp: String = "",
    val scanSamples: Int = 600,
    val scanThreads: Int = 96,
    val scanTimeoutMs: Int = 2500,
    val rootHosts: Boolean = false,

    // ---- TTS / 音频 ----
    val ttsEngine: String = "google_first",
    val ttsTarget: String = "translated",
    val ttsRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val audioPrefix: String = "生草",
    val audioSubdir: Boolean = false,

    // ---- 外观 ----
    val themeMode: String = "system",
    val dynamicColor: Boolean = false,
    val fontSizeSp: Int = 14,

    // ---- 保活 ----
    val foregroundService: Boolean = true,

    // ---- 其它 ----
    val firstRun: Boolean = true,
)

// ---- JSON 小工具（顶层扩展函数，方便任意文件直接调用） ----
fun JSONObject.str(k: String, d: String = ""): String = optString(k, d)
fun JSONObject.int(k: String, d: Int): Int = optInt(k, d)
fun JSONObject.bool(k: String, d: Boolean): Boolean = optBoolean(k, d)
fun JSONObject.float(k: String, d: Float): Float = optDouble(k, d.toDouble()).toFloat()
fun JSONObject.strList(k: String): List<String> {
        val a = optJSONArray(k) ?: return emptyList()
        return (0 until a.length()).map { a.optString(it) }
    }

fun JSONObject.strSet(k: String): Set<String> = strList(k).toSet()
fun JSONArray.toStrList(): List<String> = (0 until length()).map { optString(it) }
fun JSONArray.toStrSet(): Set<String> = toStrList().toSet()
fun jsonArrayOf(items: Collection<String>): JSONArray = JSONArray().apply { items.forEach { put(it) } }

class SettingsStore(context: Context) {

    private val sp = context.getSharedPreferences("ggm_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<Settings> = _state.asStateFlow()
    val value: Settings get() = _state.value

    private fun read(): Settings {
        val raw = sp.getString(KEY, null) ?: return Settings()
        return runCatching { decode(JSONObject(raw)) }.getOrElse { Settings() }
    }

    fun update(block: (Settings) -> Settings) {
        val next = block(_state.value)
        _state.value = next
        sp.edit().putString(KEY, encode(next).toString()).apply()
    }

    fun exportJson(): String = encode(_state.value).toString(2)

    fun importJson(text: String): Boolean = runCatching {
        val s = decode(JSONObject(text))
        _state.value = s
        sp.edit().putString(KEY, encode(s).toString()).apply()
        true
    }.getOrElse { false }

    companion object {
        private const val KEY = "json"

        fun encode(s: Settings): JSONObject = JSONObject().apply {
            put("times", s.times)
            put("timeoutSec", s.timeoutSec)
            put("retries", s.retries)
            put("concurrency", s.concurrency)
            put("minIntervalMs", s.minIntervalMs)
            put("finalLang", s.finalLang)
            put("randomOrder", s.randomOrder)
            put("separator", s.separator)
            put("selectedLangs", jsonArrayOf(s.selectedLangs))
            put("excludedLangs", jsonArrayOf(s.excludedLangs))
            put("useApiKey", s.useApiKey)
            put("apiKey", s.apiKey)
            put("useScannedIp", s.useScannedIp)
            put("ipList", jsonArrayOf(s.ipList))
            put("activeIp", s.activeIp)
            put("scanSamples", s.scanSamples)
            put("scanThreads", s.scanThreads)
            put("scanTimeoutMs", s.scanTimeoutMs)
            put("rootHosts", s.rootHosts)
            put("ttsEngine", s.ttsEngine)
            put("ttsTarget", s.ttsTarget)
            put("ttsRate", s.ttsRate.toDouble())
            put("ttsPitch", s.ttsPitch.toDouble())
            put("audioPrefix", s.audioPrefix)
            put("audioSubdir", s.audioSubdir)
            put("themeMode", s.themeMode)
            put("dynamicColor", s.dynamicColor)
            put("fontSizeSp", s.fontSizeSp)
            put("foregroundService", s.foregroundService)
            put("firstRun", s.firstRun)
        }

        fun decode(o: JSONObject): Settings = Settings(
            times = o.int("times", 8).coerceIn(1, 200),
            timeoutSec = o.int("timeoutSec", 15).coerceIn(1, 120),
            retries = o.int("retries", 3).coerceIn(0, 20),
            concurrency = o.int("concurrency", 8).coerceIn(1, 64),
            minIntervalMs = o.int("minIntervalMs", 0).coerceIn(0, 5000),
            finalLang = o.str("finalLang", "zh-cn"),
            randomOrder = o.bool("randomOrder", true),
            separator = o.str("separator", "\n。！？!?；;"),
            selectedLangs = o.strSet("selectedLangs").ifEmpty { LangData.ALL_CODES.toSet() },
            excludedLangs = o.strSet("excludedLangs"),
            useApiKey = o.bool("useApiKey", false),
            apiKey = o.str("apiKey"),
            useScannedIp = o.bool("useScannedIp", true),
            ipList = o.strList("ipList"),
            activeIp = o.str("activeIp"),
            scanSamples = o.int("scanSamples", 600).coerceIn(50, 5000),
            scanThreads = o.int("scanThreads", 96).coerceIn(4, 512),
            scanTimeoutMs = o.int("scanTimeoutMs", 2500).coerceIn(300, 15000),
            rootHosts = o.bool("rootHosts", false),
            ttsEngine = o.str("ttsEngine", "google_first"),
            ttsTarget = o.str("ttsTarget", "translated"),
            ttsRate = o.float("ttsRate", 1.0f).coerceIn(0.3f, 2.5f),
            ttsPitch = o.float("ttsPitch", 1.0f).coerceIn(0.3f, 2.5f),
            audioPrefix = o.str("audioPrefix", "生草"),
            audioSubdir = o.bool("audioSubdir", false),
            themeMode = o.str("themeMode", "system"),
            dynamicColor = o.bool("dynamicColor", false),
            fontSizeSp = o.int("fontSizeSp", 14).coerceIn(10, 30),
            foregroundService = o.bool("foregroundService", true),
            firstRun = o.bool("firstRun", true),
        )
    }
}
