package io.github.yuqingmiao2008.grassmachine.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * 全局网络入口。
 *
 * 核心思路（等价于桌面版的 GoogleTranslateIpCheck）：把扫描得到的最优 Google 边缘 IP
 * 直接注入 OkHttp 的 DNS 解析结果，于是所有对 *.googleapis.com 的请求都会走这个 IP，
 * 不需要 root，也不需要改 hosts。
 */
object Net {

    const val UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/131.0.0.0 Mobile Safari/537.36"

    private val pool = ConnectionPool(16, 5, TimeUnit.MINUTES)
    private val dispatcher = Dispatcher()

    /** 当前生效的 Google 边缘 IP（null 表示使用系统 DNS） */
    @Volatile
    var activeIp: String? = null
        private set

    private val _activeIpFlow = MutableStateFlow<String?>(null)

    /** 供 UI 观察当前生效 IP */
    val activeIpFlow: StateFlow<String?> = _activeIpFlow.asStateFlow()

    private val googleHosts = listOf(
        "googleapis.com", "google.com", "gstatic.com", "googleusercontent.com", "googleapis.cn",
    )

    fun isGoogleHost(host: String): Boolean {
        val h = host.lowercase()
        return googleHosts.any { h == it || h.endsWith(".$it") }
    }

    private fun dnsFor(ip: String?): Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> =
            if (ip != null && isGoogleHost(hostname)) {
                listOf(InetAddress.getByName(ip))
            } else {
                Dns.SYSTEM.lookup(hostname)
            }
    }

    /** 基础客户端（长超时），单个请求的时间限制由调用方用 callTimeout 控制 */
    fun base(ip: String? = activeIp): OkHttpClient = OkHttpClient.Builder()
        .connectionPool(pool)
        .dispatcher(dispatcher)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .pingInterval(0, TimeUnit.SECONDS)
        .dns(dnsFor(ip))
        .build()

    /** 指定 IP + 超时的客户端（扫描校验、单次请求使用，开销很小，共享连接池） */
    fun forRequest(ip: String?, timeoutMs: Long): OkHttpClient = base(ip).newBuilder()
        .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .build()

    fun setActiveIp(ip: String?) {
        activeIp = ip?.trim()?.takeIf { it.isNotEmpty() }
        _activeIpFlow.value = activeIp
    }
}
