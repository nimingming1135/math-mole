package com.alex.mathmole.logic

import android.content.Context
import android.content.res.Resources
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.alex.mathmole.R

/**
 * 预制音频播放器（SoundPool 为主，精确排期，零回调依赖）。
 *
 * 为什么这么做：
 * - 旧版用 TTS 回调推进流程，引擎不回调就"卡死"；改用 MediaPlayer 后，
 *   每段音频都要新建 AudioTrack（启动开销约 200~300ms），三段的读题被拖到 2s 以上；
 * - 现在改为 **SoundPool + 时长表精确排期**：启动开销仅约 30ms，
 *   读题总时长 ≈ 各段时长之和，稳定控制在 2 秒内；
 * - 且**完全不依赖播放回调**（时长由音频文件头精确解析），流程推进是确定性的，
 *   从结构上杜绝"等待回调把界面锁死"。
 *
 * 时长表在启动预加载时从 WAV 文件头解析（约 1ms/个，无需解码）。
 */
class SoundManager(context: Context) {

    private val appContext = context.applicationContext
    private val resources: Resources = appContext.resources
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 语音开关（设置项，持久化由 SettingsStore 负责） */
    @Volatile
    var voiceEnabled = true

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    /** resId -> soundId */
    private val soundIds = HashMap<Int, Int>()

    /** resId -> 时长（毫秒），由 WAV 文件头解析得到 */
    private val durations = HashMap<Int, Long>()

    /** 已加载完成的 soundId */
    private val loadedIds = HashSet<Int>()

    /** 语音排期任务与正在播放的流（停止时统一清理） */
    private val scheduledTasks = ArrayList<Runnable>()
    private val activeStreams = ArrayList<Int>()

    /** 语音代次：停止后旧排期任务自动失效 */
    private var voiceToken = 0

    /** 尚未加载完成的音频数量与等待回调（预加载完成前延后播放，避免片段重叠） */
    private var pendingLoads = 0
    private val preloadCallbacks = ArrayList<() -> Unit>()
    private var preloadFlushScheduled = false

    /** 极少数"尚未加载完成"时的兜底播放器 */
    private var fallbackPlayer: MediaPlayer? = null

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loadedIds.add(sampleId)
            if (pendingLoads > 0) {
                pendingLoads--
                if (pendingLoads == 0) flushPreloadCallbacks()
            }
        }
    }

    /** 预加载一组音频（解析时长 + 载入 SoundPool） */
    fun preload(resIds: IntArray) {
        resIds.forEach { resId ->
            if (!soundIds.containsKey(resId)) {
                durations[resId] = readDurationMs(resId)
                pendingLoads++
                soundIds[resId] = pool.load(appContext, resId, 1)
            }
        }
        // 安全兜底：即使某个音频加载失败，也不能让等待方永远等下去
        if (pendingLoads > 0 && !preloadFlushScheduled) {
            preloadFlushScheduled = true
            mainHandler.postDelayed({ flushPreloadCallbacks() }, PRELOAD_TIMEOUT_MS)
        }
    }

    /** 预加载完成回调（供首题等待，避免重启后首题朗读被预加载拖慢） */
    fun whenPreloaded(action: () -> Unit) {
        if (pendingLoads <= 0) {
            action()
            return
        }
        preloadCallbacks.add(action)
    }

    /** 预加载完成（或超时）时执行等待中的动作 */
    private fun flushPreloadCallbacks() {
        pendingLoads = 0
        preloadFlushScheduled = false
        val callbacks = preloadCallbacks.toList()
        preloadCallbacks.clear()
        callbacks.forEach { it() }
    }

    /** 预加载全部语音与音效（应用启动时调用一次） */
    fun preloadAll() {
        preload(QuestionVoice.allResIds())
        preload(
            intArrayOf(
                R.raw.sfx_hammer, R.raw.sfx_warn, R.raw.sfx_beep,
                R.raw.sfx_star, R.raw.sfx_pop, R.raw.sfx_dizzy
            )
        )
    }

    /** 播放短音效 */
    fun playSfx(resId: Int) {
        val soundId = soundIds[resId]
        if (soundId != null && loadedIds.contains(soundId)) {
            pool.play(soundId, 1f, 1f, 1, 0, 1f)
            return
        }
        if (soundId == null) {
            durations[resId] = readDurationMs(resId)
            soundIds[resId] = pool.load(appContext, resId, 1)
        }
        // 尚未加载完成：稍后重试一次
        mainHandler.postDelayed({
            soundIds[resId]?.let { sid -> if (loadedIds.contains(sid)) pool.play(sid, 1f, 1f, 1, 0, 1f) }
        }, 80L)
    }

    /**
     * 顺序播放一组语音片段（如读题：数字 → 加/减 → 数字）。
     * 按各段实际时长精确排期，播完即回调 [onDone]，不依赖任何播放回调。
     */
    fun playVoice(ids: List<Int>, onDone: (() -> Unit)? = null) {
        stopVoice()
        if (!voiceEnabled || ids.isEmpty()) {
            Log.d(TAG, "语音关闭或片段为空（voiceEnabled=$voiceEnabled, ${ids.size} 段）")
            onDone?.let { cb -> mainHandler.post(cb) }
            return
        }
        // 首次预加载尚未完成时：等加载完成再排期，避免"半路播放"造成片段重叠
        if (pendingLoads > 0) {
            val token = voiceToken
            preloadCallbacks.add {
                if (token == voiceToken) playVoice(ids, onDone)
            }
            return
        }
        val token = ++voiceToken
        var offsetMs = 0L
        ids.forEach { resId ->
            val delay = offsetMs
            val task = Runnable {
                if (token != voiceToken) return@Runnable
                playClipNow(resId)
            }
            scheduledTasks.add(task)
            mainHandler.postDelayed(task, delay)
            offsetMs += (durations[resId] ?: DEFAULT_CLIP_MS) + INTER_CLIP_GAP_MS
        }
        // 末尾任务：整段语音播完
        val finish = Runnable {
            if (token == voiceToken) {
                Log.d(TAG, "语音播放完成（排期 ${offsetMs}ms）")
                onDone?.let { cb -> mainHandler.post(cb) }
            }
        }
        scheduledTasks.add(finish)
        mainHandler.postDelayed(finish, offsetMs)
    }

    /** 播放单个语音片段 */
    fun playVoice(resId: Int, onDone: (() -> Unit)? = null) = playVoice(listOf(resId), onDone)

    /** 立即播放某段音频（SoundPool 优先，未加载完成时兜底） */
    private fun playClipNow(resId: Int) {
        val soundId = soundIds[resId]
        if (soundId != null && loadedIds.contains(soundId)) {
            val stream = pool.play(soundId, 1f, 1f, 1, 0, 1f)
            if (stream != 0) activeStreams.add(stream)
            return
        }
        // 兜底：MediaPlayer 直接播（仅首次预加载尚未完成的极少数情况）
        Log.d(TAG, "片段尚未加载完成，走兜底播放: $resId")
        try {
            val mp = MediaPlayer.create(appContext, resId) ?: return
            fallbackPlayer?.let { old -> try { old.release() } catch (e: Exception) {} }
            fallbackPlayer = mp
            mp.setOnCompletionListener { player ->
                if (fallbackPlayer === player) fallbackPlayer = null
                try {
                    player.release()
                } catch (e: Exception) {
                    // 已释放则忽略
                }
            }
            mp.start()
        } catch (e: Exception) {
            Log.w(TAG, "兜底播放失败: $resId", e)
        }
    }

    /** 立即停止语音并丢弃队列（切题、切页、打断时调用） */
    fun stopVoice() {
        voiceToken++
        scheduledTasks.forEach { mainHandler.removeCallbacks(it) }
        scheduledTasks.clear()
        activeStreams.forEach { pool.stop(it) }
        activeStreams.clear()
        fallbackPlayer?.let { mp ->
            fallbackPlayer = null
            try {
                mp.stop()
            } catch (e: Exception) {
                // 播放已结束/已释放：无需处理
            }
            try {
                mp.release()
            } catch (e: Exception) {
                // 已释放则忽略
            }
        }
    }

    /** 释放全部资源（Activity 销毁时调用） */
    fun shutdown() {
        stopVoice()
        pool.release()
        soundIds.clear()
        durations.clear()
        loadedIds.clear()
    }

    /** 计算一组语音片段的总时长（含片段间隔），用于倒计时的读题宽限期 */
    fun sequenceDurationMs(ids: List<Int>): Long {
        if (ids.isEmpty()) return 0L
        return ids.sumOf { durations[it] ?: DEFAULT_CLIP_MS } + INTER_CLIP_GAP_MS * ids.size
    }

    /** 从 WAV 文件头解析时长（毫秒）：16bit 单声道 PCM */
    private fun readDurationMs(resId: Int): Long = try {
        resources.openRawResource(resId).use { input ->
            val header = ByteArray(44)
            var read = 0
            while (read < header.size) {
                val n = input.read(header, read, header.size - read)
                if (n <= 0) break
                read += n
            }
            val sampleRate = le32(header, 24)
            val dataSize = le32(header, 40)
            if (sampleRate <= 0 || dataSize <= 0) DEFAULT_CLIP_MS
            else (dataSize.toLong() * 1000L) / (sampleRate.toLong() * 2L)
        }
    } catch (e: Exception) {
        Log.w(TAG, "解析时长失败: $resId", e)
        DEFAULT_CLIP_MS
    }

    /** 小端 32 位整数 */
    private fun le32(b: ByteArray, offset: Int): Int =
        (b[offset].toInt() and 0xFF) or
            ((b[offset + 1].toInt() and 0xFF) shl 8) or
            ((b[offset + 2].toInt() and 0xFF) shl 16) or
            ((b[offset + 3].toInt() and 0xFF) shl 24)

    private companion object {
        /** 片段之间的间隔：留一点呼吸感，又不拖长总时长 */
        const val INTER_CLIP_GAP_MS = 40L

        /** 解析不到时长时的兜底值 */
        const val DEFAULT_CLIP_MS = 400L

        /** 预加载等待上限：超过则不再等待（保证流程不被预加载卡住） */
        const val PRELOAD_TIMEOUT_MS = 2500L

        const val TAG = "SoundManager"
    }
}
