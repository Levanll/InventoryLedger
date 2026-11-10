package cn.levanll.inventory.i18n

/**
 * 流水备注（`stock_record.note`）的显示翻译。
 *
 * 为什么需要单独一张表，而不是写入时就用 `I18n.s()`：
 *
 * `note` 是写进数据库的历史事实，一旦落库就固定了。若写入时按当时的语言存，
 * 用户之后切语言，旧记录的语言就和新记录对不上 —— 同一页明细里中英混杂。
 *
 * 所以约定是：**存中性、稳定的键，显示时再翻译**。
 * 但历史数据已经写死了中文（"列表快速加量" 等），无法追溯改写，
 * 因此这里同时登记中文原文与新的英文键，两种都能翻译。
 *
 * 新增备注时：写入用英文键（见 InventoryService），并在这里登记中文对照。
 */
object RecordNotes {

    /** 中文原文 → 英文 */
    private val ZH_TO_EN: Map<String, String> = mapOf(
        "列表快速加量" to "Quick add",
        "列表快速减量" to "Quick remove",
        "复用已归零条目" to "Reused zeroed item",
        "从已用完恢复" to "Restored from used-up"
    )

    /** 英文键 → 中文（写入新记录用英文，显示回中文时用这张表） */
    private val EN_TO_ZH: Map<String, String> = ZH_TO_EN.entries.associate { (zh, en) -> en to zh }

    /**
     * 翻译一条备注。
     *
     * 「撤销 #12」这类带序号的备注用前缀匹配单独处理 ——
     * 它是动态拼接的，无法穷举。
     */
    fun of(note: String?): String? {
        if (note == null) return null
        val trimmed = note.trim()
        if (trimmed.isEmpty()) return null

        return if (I18n.lang == Lang.ZH) {
            // 英文键 → 中文；认不出的（含用户自定义、中文原文）原样返回
            EN_TO_ZH[trimmed] ?: translateRevert(trimmed, toChinese = true) ?: trimmed
        } else {
            // 中文原文 → 英文；认不出的原样返回
            ZH_TO_EN[trimmed] ?: translateRevert(trimmed, toChinese = false) ?: trimmed
        }
    }

    /** 「撤销 #12」/ "Revert #12" 互转；不是这个格式返回 null */
    private fun translateRevert(note: String, toChinese: Boolean): String? {
        val zhPrefix = "撤销 #"
        val enPrefix = "Revert #"
        return when {
            toChinese && note.startsWith(enPrefix) -> zhPrefix + note.removePrefix(enPrefix)
            !toChinese && note.startsWith(zhPrefix) -> enPrefix + note.removePrefix(zhPrefix)
            else -> null
        }
    }
}
