package com.alex.mathmole

import com.alex.mathmole.logic.QuestionGenerator
import com.alex.mathmole.model.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 题目生成器纯逻辑测试：验证答案正确性、范围约束、过渡规则与选项合法性。
 * 全部为随机属性测试，每个用例重复采样以覆盖更多组合。
 */
class QuestionGeneratorTest {

    /** 覆盖各档位与关键题号（起步区 / 过渡区 / 满档区）的样本点 */
    private val sampleIndexes = listOf(1, 5, 10, 11, 13, 15, 16, 25, 30, 60)

    /** 解析题面文本为 (a, 操作符, b) */
    private fun parse(text: String): Triple<Int, String, Int> {
        val body = text.removeSuffix("= ?").trim()
        val parts = body.split(" ")
        assertEquals("题面格式异常: $text", 3, parts.size)
        return Triple(parts[0].toInt(), parts[1], parts[2].toInt())
    }

    @Test
    fun generate_answerMatchesExpression() {
        repeat(200) {
            Level.entries.forEach { level ->
                sampleIndexes.forEach { index ->
                    val q = QuestionGenerator.generate(level, index)
                    val (a, op, b) = parse(q.text)
                    val expected = if (op == "+") a + b else a - b
                    assertEquals("题面: ${q.text}", expected, q.answer)
                }
            }
        }
    }

    @Test
    fun generate_operandsAndAnswerWithinRules() {
        repeat(200) {
            Level.entries.forEach { level ->
                sampleIndexes.forEach { index ->
                    val q = QuestionGenerator.generate(level, index)
                    val (a, op, b) = parse(q.text)
                    // 结构化字段与题面必须一致（读题音频依赖 a/b/isAddition）
                    assertEquals("题面: ${q.text}", a, q.a)
                    assertEquals("题面: ${q.text}", b, q.b)
                    assertEquals("题面: ${q.text}", op == "+", q.isAddition)
                    // 操作数均为正整数
                    assertTrue("题面: ${q.text}", a >= 1 && b >= 1)
                    // 加法：和不超过本题上限；减法：被减数不超过本题上限
                    if (op == "+") {
                        assertTrue("题面: ${q.text}", a + b <= q.max)
                    } else {
                        assertTrue("题面: ${q.text}", a <= q.max)
                        // 一年级不出现负数
                        assertTrue("题面: ${q.text}", a - b >= 0)
                    }
                    // 答案落在 [0, max] 内
                    assertTrue("题面: ${q.text}", q.answer in 0..q.max)
                }
            }
        }
    }

    @Test
    fun maxOfQuestion_followsTransitionRule() {
        Level.entries.forEach { level ->
            // 前 10 题：固定在起步区间（上一档上限）
            (1..10).forEach { index ->
                assertEquals(
                    "档位: ${level.name} 题号: $index",
                    level.warmupLimit,
                    QuestionGenerator.maxOfQuestion(level, index),
                )
            }
            // 第 16 题起：满本档范围
            (16..40).forEach { index ->
                assertEquals(
                    "档位: ${level.name} 题号: $index",
                    level.limit,
                    QuestionGenerator.maxOfQuestion(level, index),
                )
            }
        }
        // 第 11~15 题：在起步区间与满档之间随机（重复采样校验边界）
        Level.entries.forEach { level ->
            (11..15).forEach { index ->
                repeat(200) {
                    val max = QuestionGenerator.maxOfQuestion(level, index)
                    assertTrue(
                        "档位: ${level.name} 题号: $index 上限: $max",
                        max in level.warmupLimit..level.limit,
                    )
                }
            }
        }
    }

    @Test
    fun generateOptions_sixDistinctIncludingAnswer() {
        repeat(200) {
            Level.entries.forEach { level ->
                sampleIndexes.forEach { index ->
                    val q = QuestionGenerator.generate(level, index)
                    val options = QuestionGenerator.generateOptions(q)
                    // 恰好 6 个选项且互不重复
                    assertEquals("题面: ${q.text}", 6, options.size)
                    assertEquals("题面: ${q.text}", 6, options.toSet().size)
                    // 必须包含正确答案
                    assertTrue("题面: ${q.text}", q.answer in options)
                    // 全部落在 [0, max] 内
                    options.forEach { option ->
                        assertTrue("题面: ${q.text} 选项: $option", option in 0..q.max)
                    }
                }
            }
        }
    }
}
