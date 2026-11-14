package com.alex.mathmole

import com.alex.mathmole.logic.QuestionVoice
import com.alex.mathmole.model.Level
import com.alex.mathmole.model.Question
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 题面朗读音频拼接测试：验证「数字 + 加/减 + 数字」的三段结构
 * （不读"等于几"，保证读题总时长控制在 2 秒以内）。
 * R.raw 常量在本地单元测试中可正常引用。
 */
class QuestionVoiceTest {

    @Test
    fun idsFor_addition_usesThreeClips() {
        val q = Question(
            text = "7 + 2 = ?", answer = 9, max = 10, level = Level.L10,
            a = 7, b = 2, isAddition = true,
        )
        val ids = QuestionVoice.idsFor(q)
        assertEquals(3, ids.size)
        assertEquals(R.raw.num_7, ids[0])
        assertEquals(R.raw.op_add, ids[1])
        assertEquals(R.raw.num_2, ids[2])
    }

    @Test
    fun idsFor_subtraction_usesMinusClip() {
        val q = Question(
            text = "9 - 4 = ?", answer = 5, max = 10, level = Level.L10,
            a = 9, b = 4, isAddition = false,
        )
        val ids = QuestionVoice.idsFor(q)
        assertEquals(3, ids.size)
        assertEquals(R.raw.num_9, ids[0])
        assertEquals(R.raw.op_sub, ids[1])
        assertEquals(R.raw.num_4, ids[2])
    }

    @Test
    fun idsFor_boundaryNumbers_areInRange() {
        listOf(0, 10, 100).forEach { value ->
            val q = Question(
                text = "$value + 0 = ?", answer = value, max = 100, level = Level.L100,
                a = value, b = 0, isAddition = true,
            )
            val ids = QuestionVoice.idsFor(q)
            assertEquals(3, ids.size)
            assertTrue(ids.all { it > 0 })
        }
    }
}
