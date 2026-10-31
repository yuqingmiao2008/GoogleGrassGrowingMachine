package io.github.yuqingmiao2008.grassmachine.core

import io.github.yuqingmiao2008.grassmachine.data.LangData
import io.github.yuqingmiao2008.grassmachine.data.Line
import io.github.yuqingmiao2008.grassmachine.data.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.random.Random

enum class RowStatus { PENDING, RUNNING, OK, FAILED }

data class JobRow(
    val index: Int,
    val original: String,
    val text: String,
    val stepDone: Int = 0,
    val status: RowStatus = RowStatus.PENDING,
    val error: String? = null,
)

data class JobState(
    val rows: List<JobRow> = emptyList(),
    val chains: List<String> = emptyList(),
    val running: Boolean = false,
    val step: Int = 0,
    val stepDoneCount: Int = 0,
    val stepTotal: Int = 0,
    val langLabel: String = "",
    val error: String? = null,
    val finished: Boolean = false,
    val log: List<String> = emptyList(),
) {
    val failedCount: Int get() = rows.count { it.status == RowStatus.FAILED }
    val okCount: Int get() = rows.count { it.stepDone >= chains.size && chains.isNotEmpty() }
    val progress: Float
        get() = if (chains.isEmpty()) 0f else {
            val per = rows.size.coerceAtLeast(1)
            (rows.sumOf { it.stepDone }.toFloat() / (per * chains.size))
        }
}

/**
 * 生草（多轮翻译）任务引擎。运行在 Application 作用域，切换页面 / 旋转屏幕都不会中断。
 */
class TranslateJob(
    private val scope: CoroutineScope,
    private val settingsProvider: () -> Settings,
) {
    private val _state = MutableStateFlow(JobState())
    val state: StateFlow<JobState> = _state.asStateFlow()

    private var job: Job? = null

    companion object {
        /** 与桌面版一致：按分隔符切句，逐个保留分隔符并换行 */
        fun splitText(text: String, separator: String): List<String> {
            var t = text.replace("\r\n", "\n")
            for (sep in separator) {
                if (sep == '\n') continue
                t = t.replace(sep.toString(), "$sep\n")
            }
            return t.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        }
    }

    fun prepare(text: String, separator: String) {
        val lines = splitText(text, separator)
        _state.value = JobState(
            rows = lines.mapIndexed { i, s -> JobRow(i, s, s) },
            chains = emptyList(),
            finished = false,
            error = null,
            log = _state.value.log,
        )
    }

    fun clear() {
        job?.cancel()
        _state.value = JobState(log = _state.value.log)
    }

    fun stop() {
        job?.cancel()
        job = null
        _state.update { it.copy(running = false, error = "已手动停止") }
    }

    fun clearLog() = _state.update { it.copy(log = emptyList()) }

    private fun log(msg: String) {
        _state.update { st ->
            val nl = (st.log + msg).takeLast(300)
            st.copy(log = nl)
        }
    }

    /** 生草主流程 */
    fun start(resume: Boolean = false) {
        val s = settingsProvider()
        val cur = _state.value
        if (cur.rows.isEmpty() || cur.running) return

        val chains = if (resume && cur.chains.isNotEmpty()) cur.chains else pickChains(s)
        if (resume) {
            _state.update {
                it.copy(
                    running = true, error = null, finished = false,
                    rows = it.rows.map { r -> r.copy(status = RowStatus.PENDING, error = null) },
                )
            }
        } else {
            _state.update {
                it.copy(
                    chains = chains, running = true, error = null, finished = false, step = 0,
                    rows = it.rows.map { r -> r.copy(text = r.original, stepDone = 0, status = RowStatus.PENDING, error = null) },
                )
            }
        }
        log("开始生草：${cur.rows.size} 行，${chains.size} 轮，并发 ${s.concurrency}")
        job = scope.launch(Dispatchers.IO) { runInternal(s, chains) }
    }

    private fun pickChains(s: Settings): List<String> {
        val pool = (s.selectedLangs - s.excludedLangs).filter { it != s.finalLang }
            .ifEmpty { LangData.ALL_CODES.filter { it != s.finalLang } }
        val rnd = Random(System.currentTimeMillis())
        val out = ArrayList<String>(s.times)
        var prev = ""
        for (i in 0 until s.times - 1) {
            var cand = ""
            var guard = 0
            do {
                cand = if (s.randomOrder) pool[rnd.nextInt(pool.size)]
                else pool[(pool.size * i / (s.times - 1 + 1)) % pool.size]
                guard++
            } while ((cand == prev || cand == "zh-cn" && prev.isEmpty()) && guard < 20)
            out.add(cand)
            prev = cand
        }
        out.add(s.finalLang)
        return out
    }

    private suspend fun runInternal(s: Settings, chains: List<String>) {
        try {
            for (step in chains.indices) {
                val dest = chains[step]
                val src = if (step == 0) "auto" else chains[step - 1]
                val todo = _state.value.rows.filter { it.stepDone == step }
                if (todo.isEmpty()) continue
                _state.update {
                    it.copy(
                        step = step,
                        langLabel = "${LangData.label(src)} → ${LangData.label(dest)}",
                        stepDoneCount = 0,
                        stepTotal = todo.size,
                    )
                }
                log("—— 第 ${step + 1}/${chains.size} 轮：${LangData.name(src)} → ${LangData.name(dest)}")
                translateStep(s, step, src, dest)
                val failedNow = _state.value.rows.count { it.status == RowStatus.FAILED && it.stepDone == step }
                if (failedNow > 0) {
                    log("本轮有 $failedNow 行失败（重试 ${s.retries} 次后仍失败）")
                }
            }
            val st = _state.value
            val done = st.rows.count { it.stepDone >= chains.size }
            _state.update {
                it.copy(
                    running = false, finished = true,
                    langLabel = "完成：$done/${st.rows.size} 行",
                    error = if (it.failedCount > 0) "有 ${it.failedCount} 行未完成，可点「重试失败行」继续" else null,
                )
            }
            log("生草结束：成功 $done/${st.rows.size} 行")
        } catch (e: CancellationException) {
            _state.update { it.copy(running = false) }
            log("任务已停止")
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(running = false, error = "${e.javaClass.simpleName}: ${e.message}") }
            log("任务失败：${e.message}")
        } finally {
            job = null
        }
    }

    private suspend fun translateStep(s: Settings, step: Int, src: String, dest: String) = coroutineScope {
        val sem = Semaphore(s.concurrency.coerceIn(1, 64))
        val timeout = s.timeoutSec * 1000L
        val apiKey = if (s.useApiKey) s.apiKey.ifBlank { null } else null
        var done = 0

        _state.value.rows.filter { it.stepDone == step }.map { row ->
            async(Dispatchers.IO) {
                sem.withPermit {
                    currentCoroutineContext().ensureActive()
                    updateRow(row.index) { it.copy(status = RowStatus.RUNNING) }

                    var attempt = 0
                    var result: String? = null
                    var err: String? = null
                    while (attempt <= s.retries) {
                        try {
                            result = Translator.translate(row.text, src, dest, timeout, apiKey)
                            break
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            err = e.message ?: e.javaClass.simpleName
                            attempt++
                            if (attempt <= s.retries) {
                                val wait = (300L * (1 shl (attempt - 1).coerceAtMost(5))) + Random.nextLong(0, 250)
                                delay(wait)
                            }
                        }
                    }

                    if (result != null) {
                        updateRow(row.index) {
                            it.copy(text = result, stepDone = step + 1, status = RowStatus.OK, error = null)
                        }
                    } else {
                        updateRow(row.index) { it.copy(status = RowStatus.FAILED, error = err) }
                    }
                    done++
                    _state.update { it.copy(stepDoneCount = done) }
                    if (s.minIntervalMs > 0) delay(s.minIntervalMs.toLong())
                }
            }
        }.awaitAll()
    }

    private fun updateRow(index: Int, block: (JobRow) -> JobRow) {
        _state.update { st ->
            val rows = st.rows.toMutableList()
            val r = rows.getOrNull(index) ?: return@update st
            rows[index] = block(r)
            st.copy(rows = rows)
        }
    }

    /** 结果导出用 */
    fun pairs(): List<Line> = _state.value.rows.map { Line(it.original, it.text) }
}
