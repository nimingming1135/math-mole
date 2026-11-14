package com.alex.mathmole.logic

import com.alex.mathmole.model.Level
import com.alex.mathmole.model.Question

/**
 * 全局游戏状态单例（仅存内存，退出 App 自动清空错题与计数）。
 *
 * 职责：
 * - 保存当前档位、会话题号、累计答对（奖杯依据）、连对数；
 * - 维护错题本与错题巩固阶段的快照队列；
 * - 提供"切换档位即全新会话"的语义；
 * - unlockedIndex 是唯一需要持久化的状态（由 MainActivity 启动时从本地恢复）。
 */
object GameCenter {

    /** 阶段：主答题流程 / 错题巩固 */
    enum class Phase { MAIN, REVIEW }

    /** 每次会话的目标题数（答满即进入错题巩固或直接完成） */
    const val TOTAL_GOAL = 30

    /** 奖杯点亮门槛：铜 / 银 / 金 */
    const val TROPHY_BRONZE_AT = 10
    const val TROPHY_SILVER_AT = 20
    const val TROPHY_GOLD_AT = 30

    /** 当前难度档位 */
    var level: Level = Level.DEFAULT
        private set

    /** 当前已解锁的最高档位索引（持久化状态，MainActivity 启动时恢复） */
    var unlockedIndex = 0

    /** 主流程当前题号（1..30） */
    var questionIndex = 0
        private set

    /** 会话累计答对数（奖杯依据） */
    var correctCount = 0
        private set

    /** 当前连对数（每连对 3 题触发星星飘落特效） */
    var streak = 0
        private set

    /** 错题本：主流程中首次答错的题（仅内存，退出 App 自动清空） */
    val wrongBook = mutableListOf<Question>()

    /** 当前阶段 */
    var phase = Phase.MAIN
        private set

    /** 错题巩固快照队列与总数（用于展示"已消灭/总数"进度） */
    var reviewQueue = mutableListOf<Question>()
        private set
    var reviewTotal = 0
        private set

    /** 当前展示中的题目（屏幕旋转等重建时恢复用） */
    var currentQuestion: Question? = null

    /** 30 题目标是否已达成 */
    val isGoalReached: Boolean get() = questionIndex >= TOTAL_GOAL

    /** 奖杯点亮判定 */
    fun bronzeLit() = correctCount >= TROPHY_BRONZE_AT
    fun silverLit() = correctCount >= TROPHY_SILVER_AT
    fun goldLit() = correctCount >= TROPHY_GOLD_AT

    /** 当前档位是否为初始可玩状态（启动时调用一次即可） */
    fun startAt(level: Level) = switchLevel(level)

    /** 切换档位 = 开启全新会话：题目计数、奖杯、连对、错题本全部清零 */
    fun switchLevel(newLevel: Level) {
        level = newLevel
        questionIndex = 0
        correctCount = 0
        streak = 0
        wrongBook.clear()
        reviewQueue.clear()
        reviewTotal = 0
        phase = Phase.MAIN
        currentQuestion = null
    }

    /** 主流程开始一道新题（题号 +1，并按过渡规则生成） */
    fun beginMainQuestion(): Question {
        questionIndex++
        return QuestionGenerator.generate(level, questionIndex).also { currentQuestion = it }
    }

    /** 主流程中答错：收录进错题本（同题面只收录一次） */
    fun recordWrong(q: Question) {
        if (wrongBook.none { it.key == q.key }) wrongBook.add(q)
    }

    /** 答对一题：累计答对数与连对数（奖杯点亮与星星特效的依据） */
    fun registerCorrect() {
        correctCount++
        streak++
    }

    /** 30 题完成且存在错题：进入错题巩固阶段（复制快照队列，避免遍历中修改） */
    fun startReview() {
        reviewQueue = wrongBook.toMutableList()
        reviewTotal = reviewQueue.size
        phase = Phase.REVIEW
    }

    /** 巩固轮当前题目（队列首题；答对才移除，答错保留循环重试） */
    fun currentReviewQuestion(): Question = reviewQueue.first()

    /** 巩固轮答对：从快照队列移除当前题 */
    fun removeCurrentReviewQuestion() {
        reviewQueue.removeAt(0)
    }

    /** 巩固进度：已消灭的错题数 */
    fun reviewDone() = reviewTotal - reviewQueue.size

    /** 阶段完成时解锁下一档；返回新解锁的档位，已是最高档则返回 null */
    fun unlockNext(): Level? {
        val next = level.next() ?: return null
        if (next.ordinal > unlockedIndex) unlockedIndex = next.ordinal
        return next
    }
}
