package io.github.yuqingmiao2008.grassmachine.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

enum class TranslateErrorKind { NETWORK, BLOCKED, RATE_LIMIT, PARSE, EMPTY, CANCELLED }

class TranslateException(
    message: String,
    val kind: TranslateErrorKind,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Google 翻译通道：
 *  - 默认走免密钥的 gtx 端点（POST 表单，支持长文本），配合 IP 注入；
 *  - 可在设置里填写 Google Cloud Translation API Key 走官方通道（更稳定）。
 */
object Translator {

    private const val GTX_URL = "https://translate.googleapis.com/translate_a/single"
    private const val API_URL = "https://translation.googleapis.com/language/translate/v2"

    suspend fun translate(
        text: String,
        src: String,
        dest: String,
        timeoutMs: Long,
        apiKey: String? = null,
    ): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext text
        if (dest == src && src != "auto") return@withContext text
        val client = Net.forRequest(Net.activeIp, timeoutMs)
        if (!apiKey.isNullOrBlank()) {
            translateByApi(client, text, src, dest, apiKey)
        } else {
            translateByGtx(client, text, src, dest)
        }
    }

    private fun translateByGtx(
        client: okhttp3.OkHttpClient,
        text: String,
        src: String,
        dest: String,
    ): String {
        val body = FormBody.Builder()
            .add("client", "gtx")
            .add("sl", src)
            .add("tl", dest)
            .add("dt", "t")
            .add("q", text)
            .build()
        val req = Request.Builder()
            .url(GTX_URL)
            .header("User-Agent", Net.UA)
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .post(body)
            .build()

        val (code, respBody) = try {
            client.newCall(req).execute().use { it.code to (it.body?.string() ?: "") }
        } catch (e: IOException) {
            throw TranslateException("网络请求失败：${e.message}", TranslateErrorKind.NETWORK, e)
        }

        when {
            code == 429 -> throw TranslateException("请求过于频繁（429），请降低并发或稍后重试", TranslateErrorKind.RATE_LIMIT)
            code == 302 || code == 403 -> throw TranslateException(
                "被 Google 拦截（HTTP $code），请先在「IP 检查」页扫描并启用可用 IP",
                TranslateErrorKind.BLOCKED,
            )
            code != 200 -> throw TranslateException("翻译接口返回 HTTP $code", TranslateErrorKind.NETWORK)
            respBody.trimStart().startsWith("<") -> throw TranslateException(
                "接口返回了网页而不是数据，通常是被风控拦截，请更换 IP 或稍后重试",
                TranslateErrorKind.BLOCKED,
            )
        }

        val sb = StringBuilder()
        try {
            val root = JSONArray(respBody)
            val segs = root.optJSONArray(0) ?: return ""
            for (i in 0 until segs.length()) {
                val seg = segs.optJSONArray(i) ?: continue
                val v = seg.opt(0)
                if (v is String) sb.append(v)
            }
        } catch (e: Exception) {
            throw TranslateException("解析翻译结果失败：${e.message}", TranslateErrorKind.PARSE, e)
        }
        val out = sb.toString()
        if (out.isBlank()) throw TranslateException("翻译结果为空", TranslateErrorKind.EMPTY)
        return out
    }

    private fun translateByApi(
        client: okhttp3.OkHttpClient,
        text: String,
        src: String,
        dest: String,
        apiKey: String,
    ): String {
        val body = FormBody.Builder()
            .add("q", text)
            .add("target", dest)
            .apply { if (src != "auto") add("source", src) }
            .add("format", "text")
            .build()
        val req = Request.Builder()
            .url("$API_URL?key=$apiKey")
            .header("User-Agent", Net.UA)
            .post(body)
            .build()
        val (code, respBody) = try {
            client.newCall(req).execute().use { it.code to (it.body?.string() ?: "") }
        } catch (e: IOException) {
            throw TranslateException("网络请求失败：${e.message}", TranslateErrorKind.NETWORK, e)
        }
        if (code == 403 || code == 400) {
            throw TranslateException("API Key 无效或无权限（HTTP $code），请检查设置里的密钥", TranslateErrorKind.BLOCKED)
        }
        if (code != 200) throw TranslateException("官方接口返回 HTTP $code", TranslateErrorKind.NETWORK)
        return try {
            val tr = JSONObject(respBody)
                .getJSONObject("data")
                .getJSONArray("translations")
                .getJSONObject(0)
                .getString("translatedText")
            htmlUnescape(tr)
        } catch (e: Exception) {
            throw TranslateException("解析官方接口结果失败：${e.message}", TranslateErrorKind.PARSE, e)
        }
    }

    fun htmlUnescape(s: String): String = s
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")

    /** 网络诊断：返回当前 IP / 直连 / 端点可用情况 */
    suspend fun diagnose(log: (String) -> Unit) = withContext(Dispatchers.IO) {
        log("当前注入 IP：${Net.activeIp ?: "（未指定，使用系统 DNS）"}")
        val ip = Net.activeIp
        if (ip != null) {
            val ms = IpScanner().verify(ip, 8000)
            log(if (ms != null) "✓ $ip 翻译接口可用（${ms}ms）" else "✗ $ip 翻译接口不可用")
        }
        try {
            client3s().newCall(
                Request.Builder().url("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=en&dt=t&q=test")
                    .header("User-Agent", Net.UA).build()
            ).execute().use {
                log("端点直连测试：HTTP ${it.code}，长度 ${it.body?.contentLength() ?: -1}")
            }
        } catch (e: Exception) {
            log("端点直连测试失败：${e.javaClass.simpleName} ${e.message}")
        }
    }

    private fun client3s() = Net.forRequest(Net.activeIp, 6000)
}
