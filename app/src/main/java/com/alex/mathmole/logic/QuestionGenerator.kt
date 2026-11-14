package com.alex.mathmole.logic

import com.alex.mathmole.model.Level
import com.alex.mathmole.model.Question
import kotlin.random.Random

/**
 * 题目与选项生成器（纯逻辑，无 Android 依赖，便于单元测试）。
 *
 * 出题规则（与难度档位的"统一过渡规则"一致）：
 * - 加减混合，各约 50%；
 * - 加法：两个加数 >= 1 且和 <= 本题上限；
 * - 减法：被减数 <= 本题上限，减数 >= 1，差 >= 0（一年级不出现负数）；
 * - 前 10 题固定在本档"起步区间"（上一档上限）内，
 *   第 11~15 题在 [起步区间, 本档上限] 间随机实现平滑过渡，
 *   第 16 题起满本档范围。
 */
object QuestionGenerator {

    /**
     * 生成一道新题。
     * @param level 当前难度档位
     * @param questionIndex 主流程题号（从 1 开始），决定过渡规则取值
     */
    fun generate(level: Level, questionIndex: Int): Question {
        val max = maxOfQuestion(level, questionIndex)
        val isAddition = Random.nextBoolean()
        val a: Int
        val b: Int
        if (isAddition) {
            // 加法：a >= 1，b >= 1，且 a + b <= max
            a = Random.nextInt(1, max)              // 1 .. max-1
            b = Random.nextInt(1, max - a + 1)      // 1 .. max-a
        } else {
            // 减法：a ∈ [1, max]，b ∈ [1, a]，差 >= 0
            a = Random.nextInt(1, max + 1)
            b = Random.nextInt(1, a + 1)
        }
        val answer = if (isAddition) a + b else a - b
        val op = if (isAddition) "+" else "-"
        return Question(
            text = "$a $op $b = ?",
            answer = answer,
            max = max,
            level = level,
            a = a,
            b = b,
            isAddition = isAddition,
        )
    }

    /**
     * 计算本题数值上限（过渡规则核心）：
     * 前 10 题 → 起步区间上限；11~15 题 → 在起步与满档间随机；16 题起 → 本档上限。
     */
    fun maxOfQuestion(level: Level, questionIndex: Int): Int = when {
        questionIndex <= 10 -> level.warmupLimit
        questionIndex <= 15 -> Random.nextInt(level.warmupLimit, level.limit + 1)
        else -> level.limit
    }

    /**
     * 生成 6 个选项：1 个正确答案 + 5 个不重复的随机干扰项，最后打乱顺序。
     * 干扰项优先贴近正确答案（±1 ~ ±10 随机浮动），不足时从 [0, max] 全范围兜底补齐。
     */
    fun generateOptions(question: Question): List<Int> {
        val options = mutableSetOf(question.answer)
        // ±1 ~ ±10 的浮动候选，随机顺序以避免固定规律
        val deltas = (1..10).flatMap { listOf(it, -it) }.shuffled()
        for (d in deltas) {
            if (options.size >= 6) break
            val candidate = question.answer + d
            if (candidate in 0..question.max && candidate !in options) {
                options.add(candidate)
            }
        }
        // 兜底：从 [0, max] 全范围随机补齐（本题上限至少为 10，候选充足，理论上很少触发）
        var guard = 0
        while (options.size < 6 && guard++ < 500) {
            val candidate = Random.nextInt(0, question.max + 1)
            if (candidate !in options) options.add(candidate)
        }
        return options.shuffled()
    }
}
