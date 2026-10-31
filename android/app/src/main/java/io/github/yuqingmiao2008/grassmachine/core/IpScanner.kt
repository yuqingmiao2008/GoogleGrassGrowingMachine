package io.github.yuqingmiao2008.grassmachine.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager
import kotlin.random.Random

/**
 * Google 边缘 IP 扫描器（移植自 GoogleTranslateIpCheck 的思路，但不需要 root）。
 *
 * 两阶段：
 *  1. TCP + TLS(SNI) 握手，按握手耗时排序 —— 先快速筛掉不通的 IP；
 *  2. 对排名靠前的 IP 发一次真实翻译请求，必须 200 且返回合法 JSON —— 因为国内不少
 *     Google IP 能握手但拿到的是 404 / 证书不匹配，只测握手会得到假可用结果。
 */
class IpScanner {

    data class Result(val ip: String, val latencyMs: Long)

    private val sni = "translate.googleapis.com"

    private val trustAll = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private val sslContext: SSLContext by lazy {
        SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustAll), SecureRandom()) }
    }

    /** Google 前端常用网段（与 GoogleTranslateIpCheck 使用的候选范围一致） */
    private val ranges = listOf(
        "142.250.0.0" to 15,
        "142.251.0.0" to 16,
        "172.217.0.0" to 16,
        "216.58.192.0" to 19,
        "108.177.0.0" to 17,
        "74.125.0.0" to 16,
        "173.194.0.0" to 16,
        "209.85.128.0" to 17,
        "64.233.160.0" to 19,
        "72.14.192.0" to 18,
        "216.239.32.0" to 19,
        "203.208.32.0" to 19,
        "142.252.0.0" to 16,
        "172.253.0.0" to 16,
    )

    private fun ipToLong(ip: String): Long {
        val p = ip.split(".")
        return (p[0].toLong() shl 24) or (p[1].toLong() shl 16) or (p[2].toLong() shl 8) or p[3].toLong()
    }

    private fun longToIp(v: Long): String =
        "${(v shr 24) and 255}.${(v shr 16) and 255}.${(v shr 8) and 255}.${v and 255}"

    fun candidates(sampleCount: Int): List<String> {
        val rnd = Random(System.currentTimeMillis())
        val per = (sampleCount / ranges.size).coerceAtLeast(6)
        val out = LinkedHashSet<String>()
        for ((base, bits) in ranges) {
            val size = 1L shl (32 - bits)
            val start = ipToLong(base)
            repeat(per) { out.add(longToIp(start + rnd.nextLong(size))) }
        }
        return out.toList()
    }

    private fun probeTls(ip: String, timeoutMs: Int): Long? {
        val t0 = System.nanoTime()
        var raw: Socket? = null
        var ssl: SSLSocket? = null
        return try {
            raw = Socket()
            raw.connect(InetSocketAddress(ip, 443), timeoutMs)
            raw.soTimeout = timeoutMs
            ssl = sslContext.socketFactory.createSocket(raw, sni, 443, true) as SSLSocket
            ssl.soTimeout = timeoutMs
            ssl.startHandshake()
            (System.nanoTime() - t0) / 1_000_000
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { ssl?.close() }
            runCatching { raw?.close() }
        }
    }

    /** 真实请求校验：必须 200 且返回合法翻译 JSON */
    fun verify(ip: String, timeoutMs: Long): Long? {
        val t0 = System.nanoTime()
        val client = Net.forRequest(ip, timeoutMs)
        return try {
            val req = Request.Builder()
                .url(
                    "https://translate.googleapis.com/translate_a/single" +
                        "?client=gtx&sl=auto&tl=en&dt=t&q=grass"
                )
                .header("User-Agent", Net.UA)
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.code == 200 && body.trimStart().startsWith("[[[")) {
                    (System.nanoTime() - t0) / 1_000_000
                } else {
                    null
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * 执行扫描。
     * @param sampleCount 取样 IP 数量
     * @param threads 并发线程数
     * @param timeoutMs 单 IP 超时
     */
    suspend fun scan(
        sampleCount: Int,
        threads: Int,
        timeoutMs: Long,
        log: (String) -> Unit,
        onProgress: (done: Int, total: Int) -> Unit,
    ): List<Result> = coroutineScope {
        val candidates = candidates(sampleCount)
        log("候选 IP ${candidates.size} 个，并发 $threads，超时 ${timeoutMs}ms")
        log("阶段 1/2：TCP + TLS 握手探测…")

        val sem = Semaphore(threads.coerceIn(1, 512))
        var done = 0
        val total = candidates.size

        val phase1 = candidates.map { ip ->
            async(Dispatchers.IO) {
                sem.withPermit {
                    val ms = probeTls(ip, timeoutMs.toInt())
                    if (!isActive) return@withPermit null
                    done++
                    if (done % 60 == 0 || done == total) onProgress(done, total)
                    ms?.let { Result(ip, it) }
                }
            }
        }.awaitAll().filterNotNull().sortedBy { it.latencyMs }

        if (phase1.isEmpty()) {
            log("阶段 1 未发现可握手的 IP，请检查网络或加大取样量")
            return@coroutineScope emptyList()
        }
        log("阶段 1 完成：${phase1.size}/${total} 个 IP 可握手")

        val toVerify = phase1.take(40)
        log("阶段 2/2：对前 ${toVerify.size} 个 IP 做真实翻译请求校验…")
        var vDone = 0
        val verified = toVerify.map { r ->
            async(Dispatchers.IO) {
                sem.withPermit {
                    val ms = verify(r.ip, timeoutMs.coerceAtLeast(3000))
                    vDone++
                    onProgress(vDone, toVerify.size)
                    ms?.let {
                        log("  ✓ ${r.ip} 可用（${it}ms）")
                        Result(r.ip, it)
                    }
                }
            }
        }.awaitAll().filterNotNull().sortedBy { it.latencyMs }

        log(if (verified.isEmpty()) "阶段 2 未找到真正可用的 IP（可能被风控/全部 404）"
        else "扫描完成，可用 IP ${verified.size} 个，最快 ${verified.first().ip}（${verified.first().latencyMs}ms）")
        verified
    }
}
