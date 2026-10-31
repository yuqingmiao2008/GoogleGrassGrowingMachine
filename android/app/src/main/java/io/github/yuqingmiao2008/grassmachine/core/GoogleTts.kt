package io.github.yuqingmiao2008.grassmachine.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Google 翻译的在线 TTS（translate_tts 端点）。返回 MP3，可保存到 Download。
 *
 * 端点单次请求对文本长度有限制，所以按标点切成若干段再拼接（MP3 帧可直接相连）。
 */
object GoogleTts {

    private const val BASE = "https://translate.googleapis.com/translate_tts"

    /** 把长文本切成适合单次请求的小段 */
    fun chunk(text: String, maxLen: Int = 170): List<String> {
        val clean = text.replace("\r\n", "\n").trim()
        if (clean.isEmpty()) return emptyList()
        if (clean.length <= maxLen) return listOf(clean)

        val out = ArrayList<String>()
        val delimiters = charArrayOf('。', '！', '？', '；', '…', '\n', '.', '!', '?', ';', '，', ',', ' ')
        var sb = StringBuilder()
        for (ch in clean) {
            sb.append(ch)
            val isDelim = delimiters.contains(ch)
            if ((isDelim && sb.length >= maxLen * 6 / 10) || sb.length >= maxLen) {
                out.add(sb.toString().trim())
                sb = StringBuilder()
            }
        }
        if (sb.isNotBlank()) out.add(sb.toString().trim())
        // 二次兜底：极长无标点段落硬切
        return out.flatMap { seg ->
            if (seg.length <= maxLen) listOf(seg)
            else seg.chunked(maxLen)
        }.filter { it.isNotBlank() }
    }

    private fun langParam(code: String): String {
        val c = code.lowercase()
        return if (c.contains("-")) {
            val p = c.split("-")
            "${p[0]}-${p[1].uppercase()}"
        } else c
    }

    private suspend fun fetchChunk(
        text: String,
        lang: String,
        timeoutMs: Long,
        attempts: Int = 3,
    ): ByteArray = withContext(Dispatchers.IO) {
        var last: Exception? = null
        repeat(attempts) { i ->
            try {
                val url = BASE.toHttpUrl().newBuilder()
                    .addQueryParameter("ie", "UTF-8")
                    .addQueryParameter("client", "gtx")
                    .addQueryParameter("tl", langParam(lang))
                    .addQueryParameter("q", text)
                    .build()
                val req = Request.Builder().url(url).header("User-Agent", Net.UA).build()
                Net.forRequest(Net.activeIp, timeoutMs).newCall(req).execute().use { resp ->
                    if (resp.code != 200) throw IOException("TTS 返回 HTTP ${resp.code}")
                    val bytes = resp.body?.bytes() ?: throw IOException("TTS 响应为空")
                    if (bytes.size < 200) throw IOException("TTS 响应过短，可能被拦截")
                    return@withContext bytes
                }
            } catch (e: Exception) {
                last = e
                if (i < attempts - 1) delay(500L * (i + 1))
            }
        }
        throw IOException("TTS 合成失败：${last?.message}", last)
    }

    /** 合成一段文本，返回完整 MP3 字节 */
    suspend fun synthesize(
        text: String,
        lang: String,
        timeoutMs: Long,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ByteArray = withContext(Dispatchers.IO) {
        val chunks = chunk(text)
        if (chunks.isEmpty()) return@withContext ByteArray(0)
        val out = ByteArrayOutputStream()
        chunks.forEachIndexed { i, c ->
            val bytes = fetchChunk(c, lang, timeoutMs)
            out.write(if (i == 0) bytes else stripId3(bytes))
            onProgress(i + 1, chunks.size)
        }
        out.toByteArray()
    }

    /** 逐行合成，返回 (行号, MP3) 列表，用于打包 zip */
    suspend fun synthesizeLines(
        lines: List<String>,
        lang: String,
        timeoutMs: Long,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): List<Pair<Int, ByteArray>> = withContext(Dispatchers.IO) {
        val result = ArrayList<Pair<Int, ByteArray>>()
        lines.forEachIndexed { i, line ->
            val bytes = synthesize(line, lang, timeoutMs)
            if (bytes.isNotEmpty()) result.add(i to bytes)
            onProgress(i + 1, lines.size)
        }
        result
    }

    /** 去掉后续分段的 ID3v2 头，避免拼接后播放器识别错乱 */
    fun stripId3(data: ByteArray): ByteArray {
        if (data.size < 10) return data
        if (!(data[0] == 'I'.code.toByte() && data[1] == 'D'.code.toByte() && data[2] == '3'.code.toByte())) return data
        val size = ((data[6].toInt() and 0x7F) shl 21) or
            ((data[7].toInt() and 0x7F) shl 14) or
            ((data[8].toInt() and 0x7F) shl 7) or
            (data[9].toInt() and 0x7F)
        var total = 10 + size
        if (data[5].toInt() and 0x10 != 0) total += 10
        return if (total in 1 until data.size) data.copyOfRange(total, data.size) else data
    }
}
