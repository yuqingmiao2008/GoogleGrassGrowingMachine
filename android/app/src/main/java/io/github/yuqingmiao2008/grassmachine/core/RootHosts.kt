package io.github.yuqingmiao2008.grassmachine.core

/**
 * 可选实验功能：已 root 的设备可以把扫描到的 IP 写进系统 hosts，
 * 让其它应用（例如 Google 翻译 App）也能用。未 root 的设备完全不需要它，
 * 因为本应用自身通过 DNS 注入的方式工作。
 */
object RootHosts {

    data class Res(val ok: Boolean, val out: String)

    private const val BEGIN = "# >>> GGM BEGIN >>>"
    private const val END = "# <<< GGM END <<<"

    fun isRooted(): Boolean = runCatching {
        val p = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor() == 0 && out.contains("uid=0")
    }.getOrDefault(false)

    fun apply(ips: List<String>): Res {
        if (ips.isEmpty()) return Res(false, "没有可用 IP")
        val lines = buildString {
            append(BEGIN).append('\n')
            ips.take(8).forEach { append(it).append(" translate.googleapis.com\n") }
            append(END).append('\n')
        }
        val script = """
            TMP=/data/local/tmp/ggm_hosts
            cp /system/etc/hosts ${'$'}TMP 2>/dev/null || cat /system/etc/hosts > ${'$'}TMP
            cp /system/etc/hosts /data/local/tmp/hosts.ggm.bak 2>/dev/null
            sed -i '/# >>> GGM BEGIN >>>/,/# <<< GGM END >>>/d' ${'$'}TMP 2>/dev/null
            cat >> ${'$'}TMP <<'GGMEOF'
$lines
GGMEOF
            mount -o rw,remount /system 2>/dev/null || mount -o rw,remount / 2>/dev/null
            if cat ${'$'}TMP > /system/etc/hosts 2>/dev/null; then
              mount -o ro,remount /system 2>/dev/null
              echo GGM_OK
            else
              echo GGM_FAIL
            fi
        """.trimIndent()
        return exec(script)
    }

    fun restore(): Res = exec(
        """
        cp /data/local/tmp/hosts.ggm.bak /system/etc/hosts 2>/dev/null && echo GGM_OK || echo GGM_FAIL
        """.trimIndent()
    )

    private fun exec(script: String): Res = runCatching {
        val p = ProcessBuilder("su", "-c", script).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText().trim()
        val code = p.waitFor()
        Res(code == 0 && out.contains("GGM_OK"), out.ifEmpty { "退出码 $code" })
    }.getOrElse { Res(false, "执行失败：${it.message}") }
}
