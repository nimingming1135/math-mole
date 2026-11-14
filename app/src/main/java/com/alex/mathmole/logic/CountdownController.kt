package com.alex.mathmole.logic

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * 答题倒计时控制器（绝对时间基准，完全独立于任何交互）。
 *
 * 设计原则（对应"倒计时绝不能被点击打断"的要求）：
 * - 题目一出现就 [start]：此时就把"宽限期结束时刻"和"归零时刻"全部算定；
 * - 读题时间以 [delayMs] 形式作为宽限期，由计时器自己跳过，
 *   **不依赖语音播完回调**——点击打断读题也不会影响倒计时；
 * - 每 100ms 用「归零时刻 − 当前时刻」重算剩余，因此不会累积误差、不会停滞；
 * - 只有退到后台、打开设置弹窗才会 [pause]；答题/答错/动效一律不干预。
 */
class CountdownController(
    private val onTick: (seconds: Int) -> Unit,
    private val onBeep: (seconds: Int) -> Unit,
    private val onFinish: () -> Unit,
) {

    private val handler = Handler(Looper.getMainLooper())

    /** 宽限期结束时刻（此之前不扣时间，显示满格） */
    private var beginAt = 0L

    /** 归零时刻 */
    private var endAt = 0L

    /** 剩余毫秒（暂停时保存） */
    private var remainingMs = 0L

    /** 暂停时剩余的宽限期 */
    private var pendingDelayMs = 0L

    private var running = false
    private var lastBeepSecond = -1

    /** 是否正在计时 */
    val isRunning: Boolean get() = running

    /**
     * 开始一轮倒计时。
     * @param totalSeconds 总答题秒数
     * @param delayMs      读题宽限期（此期间显示满格、不扣时间）
     */
    fun start(totalSeconds: Int, delayMs: Long = 0L) {
        handler.removeCallbacks(ticker)
        val totalMs = totalSeconds.coerceAtLeast(1) * 1000L
        remainingMs = totalMs
        val now = SystemClock.elapsedRealtime()
        beginAt = now + delayMs.coerceAtLeast(0L)
        endAt = beginAt + totalMs
        lastBeepSecond = -1
        running = true
        onTick(totalSeconds.coerceAtLeast(1))
        handler.post(ticker)
    }

    /** 100ms 精度的 tick：全部基于绝对时刻计算，点击与动效都影响不到 */
    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            val now = SystemClock.elapsedRealtime()
            // 读题宽限期：显示满格，不扣时间
            if (now < beginAt) {
                handler.postDelayed(this, TICK_INTERVAL_MS)
                return
            }
            val left = endAt - now
            if (left <= 0L) {
                remainingMs = 0L
                running = false
                handler.removeCallbacks(this)
                onTick(0)
                onFinish()
                return
            }
            remainingMs = left
            val sec = secondsLeft(left)
            onTick(sec)
            // 最后 3 秒逐秒嘟嘟（每秒只响一次）
            if (sec in 1..3 && sec != lastBeepSecond) {
                lastBeepSecond = sec
                onBeep(sec)
            }
            handler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

    /** 剩余秒数（向上取整） */
    private fun secondsLeft(leftMs: Long): Int = ((leftMs + 999L) / 1000L).toInt()

    /** 暂停（仅用于退到后台 / 打开设置弹窗） */
    fun pause() {
        if (!running) return
        val now = SystemClock.elapsedRealtime()
        remainingMs = (endAt - now).coerceAtLeast(0L)
        pendingDelayMs = (beginAt - now).coerceAtLeast(0L)
        running = false
        handler.removeCallbacks(ticker)
    }

    /** 从暂停处继续（保留剩余宽限期） */
    fun resume() {
        if (running || remainingMs <= 0L) return
        val now = SystemClock.elapsedRealtime()
        beginAt = now + pendingDelayMs
        endAt = beginAt + remainingMs
        running = true
        handler.post(ticker)
    }

    /** 停止并清空 */
    fun stop() {
        running = false
        remainingMs = 0L
        pendingDelayMs = 0L
        lastBeepSecond = -1
        handler.removeCallbacksAndMessages(null)
    }

    /** 页面销毁时调用 */
    fun shutdown() {
        stop()
        handler.removeCallbacksAndMessages(null)
    }

    private companion object {
        /** tick 间隔：比 1 秒更密，显示的秒数与真实剩余一致 */
        const val TICK_INTERVAL_MS = 100L
    }
}
