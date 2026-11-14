package com.alex.mathmole.model

/**
 * 一道口算题。
 *
 * @param text    题面文本，如 "3 + 5 = ?"
 * @param answer  正确答案（减法保证 >= 0，不出现负数）
 * @param max     本题数值上限：用于限定干扰项取值范围，与出题时的过渡规则一致
 * @param level   所属难度档位
 * @param a       左操作数（读题音频拼接用）
 * @param b       右操作数（读题音频拼接用）
 * @param isAddition 是否加法（决定读题时播「加」还是「减」）
 */
data class Question(
    val text: String,
    val answer: Int,
    val max: Int,
    val level: Level,
    val a: Int = 0,
    val b: Int = 0,
    val isAddition: Boolean = true,
) {
    /** 错题收录去重标识：同一题面只收录一次 */
    val key: String get() = text
}
