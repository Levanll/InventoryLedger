package cn.levanll.inventory.i18n

/**
 * 单位名称的中英对照。
 *
 * 数据库里单位存中文（"件"、"盒"…），因为：
 *   1. 单位参与步进计算（Constants.stepOfUnit），算法按中文判定
 *   2. 用户录入的原始值不该因切语言而改写
 *
 * 显示时再翻译。这样切换语言不会动数据，只是换了个标签。
 */
object UnitNames {

    private val MAP: Map<String, String> = mapOf(
        "件" to "pcs",
        "个" to "pc",
        "瓶" to "bottle",
        "包" to "pack",
        "盒" to "box",
        "袋" to "bag",
        "支" to "tube",
        "克" to "g",
        "毫升" to "ml"
    )

    fun of(zhUnit: String): String {
        if (I18n.lang == Lang.ZH) return zhUnit
        return MAP[zhUnit] ?: zhUnit
    }
}
