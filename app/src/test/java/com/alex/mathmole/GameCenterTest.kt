package com.alex.mathmole

import com.alex.mathmole.logic.GameCenter
import com.alex.mathmole.model.Level
import com.alex.mathmole.model.Question
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 游戏中心状态测试：会话重置、错题收录去重、错题巩固快照、解锁推进。
 * GameCenter 为全局单例，每个用例开始前先 switchLevel 重置状态。
 */
class GameCenterTest {

    @Before
    fun reset() {
        GameCenter.switchLevel(Level.L10)
        GameCenter.unlockedIndex = 0
    }

    @Test
    fun switchLevel_resetsSessionState() {
        // 模拟答题产生各类状态
        GameCenter.switchLevel(Level.L20)
        GameCenter.beginMainQuestion()
        GameCenter.beginMainQuestion()
        repeat(15) { GameCenter.registerCorrect() }
        GameCenter.recordWrong(Question("2 + 3 = ?", 5, 10, Level.L20))
        assertEquals(2, GameCenter.questionIndex)
        assertEquals(15, GameCenter.correctCount)
        assertEquals(15, GameCenter.streak)
        assertEquals(1, GameCenter.wrongBook.size)

        // 切换档位 = 全新会话：一切清零
        GameCenter.switchLevel(Level.L30)
        assertEquals(0, GameCenter.questionIndex)
        assertEquals(0, GameCenter.correctCount)
        assertEquals(0, GameCenter.streak)
        assertTrue(GameCenter.wrongBook.isEmpty())
        assertEquals(GameCenter.Phase.MAIN, GameCenter.phase)
    }

    @Test
    fun recordWrong_deduplicatesByQuestionKey() {
        GameCenter.recordWrong(Question("3 + 5 = ?", 8, 10, Level.L10))
        GameCenter.recordWrong(Question("3 + 5 = ?", 8, 10, Level.L10))
        GameCenter.recordWrong(Question("9 - 4 = ?", 5, 10, Level.L10))
        // 同题面只收录一次，不同题面正常收录
        assertEquals(2, GameCenter.wrongBook.size)
    }

    @Test
    fun startReview_snapshotsWrongBook() {
        GameCenter.recordWrong(Question("2 + 3 = ?", 5, 10, Level.L10))
        GameCenter.recordWrong(Question("9 - 4 = ?", 5, 10, Level.L10))
        GameCenter.startReview()

        assertEquals(GameCenter.Phase.REVIEW, GameCenter.phase)
        assertEquals(2, GameCenter.reviewTotal)
        assertEquals(2, GameCenter.reviewQueue.size)

        // 巩固轮答对移除队首；清空即通关
        GameCenter.removeCurrentReviewQuestion()
        assertEquals(1, GameCenter.reviewDone())
        GameCenter.removeCurrentReviewQuestion()
        assertTrue(GameCenter.reviewQueue.isEmpty())
    }

    @Test
    fun unlockNext_advancesOneLevel() {
        GameCenter.switchLevel(Level.L20)
        GameCenter.unlockedIndex = 1
        val next = GameCenter.unlockNext()
        assertEquals(Level.L30, next)
        assertEquals(2, GameCenter.unlockedIndex)
    }

    @Test
    fun unlockNext_returnsNullAtTopLevel() {
        GameCenter.switchLevel(Level.L100)
        GameCenter.unlockedIndex = Level.entries.lastIndex
        assertNull(GameCenter.unlockNext())
        assertEquals(Level.entries.lastIndex, GameCenter.unlockedIndex)
    }

    @Test
    fun trophies_lightUpByCorrectCount() {
        GameCenter.switchLevel(Level.L10)
        repeat(9) { GameCenter.registerCorrect() }
        assertTrue(!GameCenter.bronzeLit())
        GameCenter.registerCorrect() // 第 10 次答对
        assertTrue(GameCenter.bronzeLit() && !GameCenter.silverLit())
        repeat(10) { GameCenter.registerCorrect() } // 累计 20
        assertTrue(GameCenter.silverLit() && !GameCenter.goldLit())
        repeat(10) { GameCenter.registerCorrect() } // 累计 30
        assertTrue(GameCenter.goldLit())
    }
}
