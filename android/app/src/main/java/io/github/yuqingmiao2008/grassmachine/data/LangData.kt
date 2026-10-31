package io.github.yuqingmiao2008.grassmachine.data

import java.util.Locale

/**
 * Google 翻译支持的全部语种（约 110+），中文名称与上游桌面版保持一致。
 */
object LangData {

    /** 语种代码 -> 中文名 */
    val NAMES: Map<String, String> = linkedMapOf(
        "af" to "南非荷兰语", "sq" to "阿尔巴尼亚语", "am" to "阿姆哈拉语", "ar" to "阿拉伯语",
        "hy" to "亚美尼亚语", "az" to "阿塞拜疆语", "eu" to "巴斯克语", "be" to "白俄罗斯语",
        "bn" to "孟加拉语", "bs" to "波斯尼亚语", "bg" to "保加利亚语", "ca" to "加泰罗尼亚语",
        "ceb" to "宿务语", "ny" to "齐切瓦语", "zh-cn" to "中文（简体）", "zh-tw" to "中文（繁体）",
        "co" to "科西嘉语", "hr" to "克罗地亚语", "cs" to "捷克语", "da" to "丹麦语",
        "nl" to "荷兰语", "en" to "英语", "eo" to "世界语", "et" to "爱沙尼亚语",
        "tl" to "菲律宾语", "fi" to "芬兰语", "fr" to "法语", "fy" to "弗里斯兰语",
        "gl" to "加利西亚语", "ka" to "格鲁吉亚语", "de" to "德语", "el" to "希腊语",
        "gu" to "古吉拉特语", "ht" to "海地克里奥尔语", "ha" to "豪萨语", "haw" to "夏威夷语",
        "iw" to "希伯来语", "hi" to "印地语", "hmn" to "苗语", "hu" to "匈牙利语",
        "is" to "冰岛语", "ig" to "伊博语", "id" to "印度尼西亚语", "ga" to "爱尔兰语",
        "it" to "意大利语", "ja" to "日语", "jw" to "爪哇语", "kn" to "卡纳达语",
        "kk" to "哈萨克语", "km" to "高棉语", "ko" to "朝鲜语", "ku" to "库尔德语（库尔曼吉语）",
        "ky" to "吉尔吉斯语", "lo" to "老挝语", "la" to "拉丁语", "lv" to "拉脱维亚语",
        "lt" to "立陶宛语", "lb" to "卢森堡语", "mk" to "马其顿语", "mg" to "马达加斯加语",
        "ms" to "马来语", "ml" to "马拉雅拉姆语", "mt" to "马耳他语", "mi" to "毛利语",
        "mr" to "马拉地语", "mn" to "蒙古语", "my" to "缅甸语", "ne" to "尼泊尔语",
        "no" to "挪威语", "or" to "奥迪亚语", "ps" to "普什图语", "fa" to "波斯语",
        "pl" to "波兰语", "pt" to "葡萄牙语", "pa" to "旁遮普语", "ro" to "罗马尼亚语",
        "ru" to "俄语", "sm" to "萨摩亚语", "gd" to "苏格兰盖尔语", "sr" to "塞尔维亚语",
        "st" to "塞索托语", "sn" to "绍纳语", "sd" to "信德语", "si" to "僧伽罗语",
        "sk" to "斯洛伐克语", "sl" to "斯洛文尼亚语", "so" to "索马里语", "es" to "西班牙语",
        "su" to "巽他语", "sw" to "斯瓦希里语", "sv" to "瑞典语", "tg" to "塔吉克语",
        "ta" to "泰米尔语", "te" to "泰卢固语", "th" to "泰语", "tr" to "土耳其语",
        "uk" to "乌克兰语", "ur" to "乌尔都语", "ug" to "维吾尔语", "uz" to "乌兹别克语",
        "vi" to "越南语", "cy" to "威尔士语", "xh" to "科萨语", "yi" to "意第绪语",
        "yo" to "约鲁巴语", "zu" to "祖鲁语",
        // Google 后续新增
        "bho" to "博杰普尔语", "doi" to "多格拉语", "ee" to "埃维语", "gom" to "孔卡尼语",
        "ilo" to "伊洛卡诺语", "kri" to "克里奥尔语", "mni" to "曼尼普尔语", "nso" to "北索托语",
        "oci" to "奥克语", "qu" to "克丘亚语", "sa" to "梵语", "ti" to "提格利尼亚语",
        "ts" to "聪加语", "tk" to "土库曼语", "ak" to "阿坎语", "ay" to "艾马拉语",
        "bm" to "班巴拉语", "dv" to "迪维希语", "ff" to "富拉语", "gn" to "瓜拉尼语",
        "kl" to "格陵兰语", "lg" to "卢干达语", "ln" to "林加拉语", "lus" to "米佐语",
        "mai" to "迈蒂利语", "om" to "奥罗莫语", "rw" to "卢旺达语", "tt" to "鞑靼语",
    )

    val ALL_CODES: List<String> = NAMES.keys.toList()

    /** 常用语种（设置页优先展示） */
    val COMMON: List<String> = listOf(
        "zh-cn", "zh-tw", "en", "ja", "ko", "ru", "fr", "de", "es", "pt",
        "it", "ar", "th", "vi", "id", "ms", "tr", "hi", "nl", "pl", "uk", "sv",
    )

    /** 按中文名拼音排序的全部语种（用于选择列表） */
    val ALL_SORTED: List<String> by lazy {
        ALL_CODES.sortedBy { NAMES[it] ?: it }
    }

    fun name(code: String): String = NAMES[code] ?: code

    fun label(code: String): String = if (code == "auto") "自动检测" else "${name(code)} ($code)"

    /** Google 语种代码 -> java Locale（供系统 TTS 使用） */
    fun toLocale(code: String): Locale = when (code.lowercase()) {
        "zh-cn" -> Locale.SIMPLIFIED_CHINESE
        "zh-tw" -> Locale.TRADITIONAL_CHINESE
        "iw", "he" -> Locale("iw", "IL")
        "jw", "jv" -> Locale("jw", "ID")
        "tl" -> Locale("tl", "PH")
        else -> {
            val c = code.lowercase()
            if (c.contains("-")) {
                val p = c.split("-")
                Locale(p[0], p[1].uppercase())
            } else {
                Locale(c)
            }
        }
    }
}
