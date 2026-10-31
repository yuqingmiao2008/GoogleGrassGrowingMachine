package io.github.yuqingmiao2008.grassmachine.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Line(val src: String, val dst: String)

data class HistoryRecord(
    val id: Long,
    val time: Long,
    val times: Int,
    val chains: List<String>,
    val lines: List<Line>,
    val favorite: Boolean = false,
) {
    val preview: String get() = lines.firstOrNull()?.dst?.takeIf { it.isNotBlank() }
        ?: lines.firstOrNull()?.src.orEmpty()
}

/** 生草历史，保存在应用私有目录的 JSON 文件中（最多保留 300 条） */
class HistoryStore(context: Context) {

    private val file = File(context.filesDir, "history.json")
    private val _flow = MutableStateFlow(load())
    val flow: StateFlow<List<HistoryRecord>> = _flow.asStateFlow()

    private fun load(): List<HistoryRecord> = runCatching {
        if (!file.exists()) emptyList() else parse(JSONArray(file.readText()))
    }.getOrElse { emptyList() }

    private fun persist(list: List<HistoryRecord>) {
        runCatching { file.writeText(encode(list).toString()) }
    }

    fun add(times: Int, chains: List<String>, lines: List<Line>): HistoryRecord {
        val rec = HistoryRecord(
            id = System.currentTimeMillis(),
            time = System.currentTimeMillis(),
            times = times,
            chains = chains,
            lines = lines,
        )
        val next = (listOf(rec) + _flow.value).take(300)
        _flow.value = next
        persist(next)
        return rec
    }

    fun toggleFavorite(id: Long) {
        val next = _flow.value.map { if (it.id == id) it.copy(favorite = !it.favorite) else it }
        _flow.value = next
        persist(next)
    }

    fun delete(id: Long) {
        val next = _flow.value.filterNot { it.id == id }
        _flow.value = next
        persist(next)
    }

    fun clear() {
        _flow.value = emptyList()
        persist(emptyList())
    }

    fun exportJson(): String = encode(_flow.value).toString(2)

    fun importJson(text: String): Boolean = runCatching {
        val list = parse(JSONArray(text))
        _flow.value = list
        persist(list)
        true
    }.getOrElse { false }

    companion object {
        fun encode(list: List<HistoryRecord>): JSONArray = JSONArray().apply {
            list.forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id)
                    put("time", r.time)
                    put("times", r.times)
                    put("chains", jsonArrayOf(r.chains))
                    put("favorite", r.favorite)
                    put("lines", JSONArray().apply {
                        r.lines.forEach { l ->
                            put(JSONObject().put("src", l.src).put("dst", l.dst))
                        }
                    })
                })
            }
        }

        fun parse(arr: JSONArray): List<HistoryRecord> = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val linesArr = o.optJSONArray("lines") ?: JSONArray()
            val lines = (0 until linesArr.length()).mapNotNull { j ->
                val lo = linesArr.optJSONObject(j) ?: return@mapNotNull null
                Line(lo.optString("src"), lo.optString("dst"))
            }
            HistoryRecord(
                id = o.optLong("id", i.toLong()),
                time = o.optLong("time", 0L),
                times = o.optInt("times", 0),
                chains = o.strList("chains"),
                lines = lines,
                favorite = o.optBoolean("favorite", false),
            )
        }
    }
}
