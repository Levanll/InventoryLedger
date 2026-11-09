package cn.levanll.inventory.i18n

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 应用内语言。
 *
 * 为什么不用 Android 原生 values-en + stringResource：
 *   1. 原生方案需要 AppCompat 的 setApplicationLocales 才能运行时切换，
 *      本项目基于 ComponentActivity，引入 AppCompat 只为切语言不划算
 *   2. 原生方案切换后需要重建 Activity，会丢失当前页面状态
 *   3. Compose 的 mutableStateOf 天然可观察，切语言时相关组件自动重组
 *
 * 语言选择存 SharedPreferences：不持久化的话每次冷启动都回到中文，
 * 英文用户得反复切换，这是实际会被抱怨的缺陷。
 */
enum class Lang(val code: String, val label: String) {
    ZH("zh", "中文"),
    EN("en", "English");

    companion object {
        fun fromCode(c: String?): Lang = if (c == EN.code) EN else ZH
    }
}

object I18n {

    private const val PREF = "i18n"
    private const val KEY_LANG = "lang"

    private var prefs: android.content.SharedPreferences? = null

    /** 当前语言。用 mutableStateOf 让 Compose 能观察到变化并重组 */
    var lang: Lang by mutableStateOf(Lang.ZH)
        private set

    /** 在 Application / Activity 启动时调用一次，恢复上次选择 */
    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs = p
        lang = Lang.fromCode(p.getString(KEY_LANG, null))
    }

    fun set(l: Lang) {
        lang = l
        prefs?.edit()?.putString(KEY_LANG, l.code)?.apply()
    }

    /**
     * 取当前语言下的文案。
     *
     * 用法：I18n.s("库存", "Inventory")
     * 中文在前、英文在后，与项目其他地方的对照习惯一致。
     */
    fun s(zh: String, en: String): String = if (lang == Lang.ZH) zh else en

    /** 切换语言并持久化 */
    fun toggle(): Lang {
        set(if (lang == Lang.ZH) Lang.EN else Lang.ZH)
        return lang
    }
}
