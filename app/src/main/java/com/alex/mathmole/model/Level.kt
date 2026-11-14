package com.alex.mathmole.model

/**
 * 难度关卡：六档难度，初始仅解锁第一档。
 *
 * @param limit       本档题目数值上限（加法结果、减法被减数都不超过它）
 * @param warmupLimit 本档"起步区间"上限，即上一档的上限：
 *                    前 10 题固定在该范围内（10 以内档没有上一档，取自身），
 *                    第 11~15 题在 [warmupLimit, limit] 间平滑过渡，第 16 题起满档位范围。
 * @param displayName 界面展示名称
 */
enum class Level(val limit: Int, val warmupLimit: Int, val displayName: String) {
    L10(10, 10, "10以内"),
    L20(20, 10, "20以内"),
    L30(30, 20, "30以内"),
    L50(50, 30, "50以内"),
    L80(80, 50, "80以内"),
    L100(100, 80, "100以内");

    /** 下一档关卡；已是最高档则返回 null */
    fun next(): Level? = entries.getOrNull(ordinal + 1)

    companion object {
        /** 初始默认档位（初始仅解锁 10 以内） */
        val DEFAULT: Level = L10
    }
}
