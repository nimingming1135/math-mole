package com.alex.mathmole.ui

import android.animation.Animator
import android.animation.ObjectAnimator
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.alex.mathmole.MainActivity
import com.alex.mathmole.R
import com.alex.mathmole.data.SettingsStore
import com.alex.mathmole.databinding.FragmentAnswerBinding
import com.alex.mathmole.logic.CountdownController
import com.alex.mathmole.logic.GameCenter
import com.alex.mathmole.logic.QuestionGenerator
import com.alex.mathmole.logic.QuestionVoice
import com.alex.mathmole.logic.SoundManager
import com.alex.mathmole.model.Question
import kotlin.random.Random

/**
 * 答题页（打地鼠风格，主流程 / 错题巩固双模式复用）。
 *
 * 完整状态机（彻底解决旧版"卡死"问题）：
 * - [State.ASKING]：可答题，倒计时运行（读题期间暂停，读完继续）；
 * - [State.RESOLVING]：锤击/语音/动效进行中，屏蔽点击，倒计时暂停；
 * - [State.FINISHED]：本阶段完成。
 *
 * 所有音频播放均有看门狗兜底（见 SoundManager），任何回调丢失都会自动推进，
 * 不会出现"等回调把界面锁死"的情况。
 *
 * 交互：
 * - 题目出现 → 动物从土洞冒出 + 朗读题目；
 * - 点击 → 锤子砸下；答对 → 绿框亮起 + 动物被敲进土里 + 随机表扬语音 → 下一题；
 * - 答错 → 警告音 + 动物眩晕摇晃，题目保留，倒计时继续；
 * - 倒计时归零 → 播「小朋友下次答题要快一些哦」→ 记入错题集 → 下一题。
 */
class AnswerFragment : Fragment() {

    /** 答题状态机 */
    private enum class State { ASKING, RESOLVING, FINISHED }

    private var _binding: FragmentAnswerBinding? = null
    private val binding get() = _binding!!

    /** 六个动物图标（每道题随机洗牌位置，避免孩子背位置） */
    private val allAnimals = listOf(
        R.drawable.ic_cat, R.drawable.ic_pig, R.drawable.ic_dog,
        R.drawable.ic_monkey, R.drawable.ic_rabbit, R.drawable.ic_bear
    )

    /** 答对表扬音频（中英混合轮换，12 条降低重复感） */
    private val praiseIds = listOf(
        R.raw.praise_1, R.raw.praise_2, R.raw.praise_3,
        R.raw.praise_4, R.raw.praise_5, R.raw.praise_6,
        R.raw.praise_7, R.raw.praise_8, R.raw.praise_9,
        R.raw.praise_10, R.raw.praise_11, R.raw.praise_12
    )

    private lateinit var settings: SettingsStore
    private lateinit var countdown: CountdownController

    /** 当前题目的 6 个选项 */
    private var currentOptions: List<Int> = emptyList()

    /** 题目序号：用于丢弃过期回调（切题后旧回调一律不执行） */
    private var questionSerial = 0

    /** 上一次的表扬序号（避免连续重复） */
    private var lastPraiseIndex = -1

    private var state = State.ASKING

    /** 眩晕等 ObjectAnimator 句柄，切题时统一取消 */
    private val activeAnimators = mutableListOf<Animator>()

    /** 语音播放器由 MainActivity 统一持有与销毁 */
    private val sound: SoundManager get() = (requireActivity() as MainActivity).sound

    // ---- 控件列表化，便于按索引循环处理 ----
    private val holes: List<android.widget.FrameLayout> by lazy {
        listOf(
            binding.hole0, binding.hole1, binding.hole2,
            binding.hole3, binding.hole4, binding.hole5
        )
    }
    private val pops: List<android.widget.FrameLayout> by lazy {
        listOf(
            binding.pop0, binding.pop1, binding.pop2,
            binding.pop3, binding.pop4, binding.pop5
        )
    }
    /** 六个动物对应的专属巢穴背景（猫窝/鼠洞/狗窝/树洞/兔洞/熊洞） */
    private val nestsForAnimal = mapOf(
        R.drawable.ic_cat to R.drawable.bg_nest_cat,
        R.drawable.ic_pig to R.drawable.bg_nest_mouse,
        R.drawable.ic_dog to R.drawable.bg_nest_dog,
        R.drawable.ic_monkey to R.drawable.bg_nest_monkey,
        R.drawable.ic_rabbit to R.drawable.bg_nest_rabbit,
        R.drawable.ic_bear to R.drawable.bg_nest_bear,
    )

    private val nests: List<ImageView> by lazy {
        listOf(
            binding.nest0, binding.nest1, binding.nest2,
            binding.nest3, binding.nest4, binding.nest5
        )
    }

    private val animals: List<ImageView> by lazy {
        listOf(
            binding.animal0, binding.animal1, binding.animal2,
            binding.animal3, binding.animal4, binding.animal5
        )
    }
    private val numbers: List<TextView> by lazy {
        listOf(
            binding.number0, binding.number1, binding.number2,
            binding.number3, binding.number4, binding.number5
        )
    }
    private val greens: List<ImageView> by lazy {
        listOf(
            binding.green0, binding.green1, binding.green2,
            binding.green3, binding.green4, binding.green5
        )
    }
    private val hammers: List<ImageView> by lazy {
        listOf(
            binding.hammer0, binding.hammer1, binding.hammer2,
            binding.hammer3, binding.hammer4, binding.hammer5
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAnswerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        settings = SettingsStore(requireContext())
        sound.voiceEnabled = settings.voiceEnabled()
        updateMuteIcon()

        // 倒计时：每秒刷新数字，最后 3 秒嘟嘟，归零即超时判负
        countdown = CountdownController(
            onTick = { seconds ->
                binding.countdownText.text = seconds.toString()
                binding.countdownText.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        if (seconds <= 3) R.color.hint_wrong else R.color.text_dark
                    )
                )
            },            onBeep = { seconds ->
                Log.d(TAG, "倒计时 $seconds 秒嘟嘟")
                sound.playSfx(R.raw.sfx_beep)
            },
            onFinish = { onTimeout() },
        )

        // 六个土洞统一挂接点击
        holes.forEachIndexed { index, hole ->
            hole.contentDescription = getString(R.string.cd_hole)
            hole.setOnClickListener { onHoleClick(index) }
        }
        binding.btnMute.setOnClickListener { toggleVoice() }
        binding.btnSettings.setOnClickListener { showSettingsDialog() }

        // 屏幕旋转等重建：有进行中的题目则直接恢复显示，不再朗读，避免重复播报
        val cached = GameCenter.currentQuestion
        if (cached != null) {
            renderQuestion(cached, animatePop = false, readAloud = false)
        } else {
            loadQuestion()
        }
        updateTrophies()
    }

    // ================= 题目加载与渲染 =================

    /** 加载下一题：主流程推进题号；错题巩固轮取快照队列首题 */
    private fun loadQuestion() {
        questionSerial++
        val serial = questionSerial
        state = State.ASKING
        countdown.stop()
        cancelAnimations()
        resetHoles()

        val question = when (GameCenter.phase) {
            GameCenter.Phase.MAIN -> GameCenter.beginMainQuestion()
            GameCenter.Phase.REVIEW -> GameCenter.currentReviewQuestion()
        }
        GameCenter.currentQuestion = question
        renderQuestion(question, animatePop = true, readAloud = true, serial = serial)
    }

    /**
     * 渲染题目：题面 + 6 个数字牌 + 动物洗牌 + 冒头动画 + 朗读 + 倒计时预备。
     *
     * @param animatePop 是否播冒头动画（重建场景不播）
     * @param readAloud  是否朗读题目
     */
    private fun renderQuestion(
        question: Question,
        animatePop: Boolean,
        readAloud: Boolean,
        serial: Int = questionSerial,
    ) {
        currentOptions = QuestionGenerator.generateOptions(question)
        val animalOrder = allAnimals.shuffled()

        binding.tvQuestion.text = question.text
        binding.tvHint.visibility = View.GONE

        currentOptions.forEachIndexed { index, value ->
            val animalRes = animalOrder[index]
            animals[index].setImageResource(animalRes)
            // 巢穴与动物一一对应：猫→猫窝、鼠→土洞、狗→狗窝、猴→树洞、兔→草地洞、熊→岩洞
            nests[index].setImageResource(
                nestsForAnimal[animalRes] ?: R.drawable.bg_nest_mouse
            )
            numbers[index].text = value.toString()
        }

        if (animatePop) {
            pops.forEachIndexed { index, pop -> popIn(pop, index * 55L) }
            sound.playSfx(R.raw.sfx_pop)
        }

        updateProgress()
        updateTrophies()

        val readIds = QuestionVoice.idsFor(question)
        // 读题宽限期 = 读题音频总时长；倒计时从这一刻起独立走，点击打断读题也不影响
        val graceMs = if (readAloud) sound.sequenceDurationMs(readIds) else 0L
        countdown.start(settings.countdownSeconds(), graceMs)
        binding.countdownText.text = settings.countdownSeconds().toString()
        binding.countdownText.setTextColor(
            ContextCompat.getColor(requireContext(), R.color.text_dark)
        )

        if (readAloud) {
            // 首题可能还在预加载：等加载完成再朗读（倒计时已独立启动，无需等回调）
            sound.whenPreloaded {
                if (serial != questionSerial || state != State.ASKING) return@whenPreloaded
                Log.d(TAG, "朗读题目：${question.text}（${readIds.size} 段，宽限 ${graceMs}ms）")
                sound.playVoice(readIds)
            }
        }
    }

    // ================= 点击与判定 =================

    private fun onHoleClick(index: Int) {
        if (state != State.ASKING) {
            Log.d(TAG, "点击洞$index 被忽略（当前状态 $state）")
            return
        }
        val question = GameCenter.currentQuestion ?: return
        state = State.RESOLVING
        // 注意：这里**不暂停倒计时**——倒计时必须完全独立于点击动作
        sound.stopVoice() // 打断读题

        playHammer(index)
        if (currentOptions[index] == question.answer) {
            Log.d(TAG, "答对：洞$index 选项${currentOptions[index]} ＝ 答案${question.answer}（${question.text}）")
            onCorrect(index, question)
        } else {
            Log.d(TAG, "答错：洞$index 选项${currentOptions[index]} ≠ 答案${question.answer}（${question.text}）")
            onWrong(index, question)
        }
    }

    /** 答对：绿框 + 敲进土里 + 随机表扬 → 下一题 */
    private fun onCorrect(index: Int, question: Question) {
        val serial = questionSerial
        greens[index].visibility = View.VISIBLE
        greens[index].scaleX = 0.75f
        greens[index].scaleY = 0.75f
        greens[index].animate().scaleX(1f).scaleY(1f).setDuration(180L).start()

        // 被锤子敲进土里：下沉 + 缩小 + 淡出
        val pop = pops[index]
        pop.animate()
            .translationY(dp(72f))
            .alpha(0f)
            .setDuration(320L)
            .setInterpolator(AccelerateInterpolator())
            .start()
        animals[index].animate().scaleY(0.8f).setDuration(120L).start()

        GameCenter.registerCorrect()
        updateTrophies()
        // 连续答对 3 题：金色星星飘落
        if (GameCenter.streak % 3 == 0) {
            StarFallHelper.dropStars(binding.starLayer)
            sound.playSfx(R.raw.sfx_star)
        }
        showHint(getString(R.string.hint_right), R.color.hint_right)

        // 随机表扬（12 条中英混合轮换，且不与上一次重复）
        var praiseIndex = Random.nextInt(praiseIds.size)
        if (praiseIndex == lastPraiseIndex) {
            praiseIndex = (praiseIndex + 1 + Random.nextInt(praiseIds.size - 1)) % praiseIds.size
        }
        lastPraiseIndex = praiseIndex

        // 表扬播完再出下一题（看门狗兜底，不会卡住）
        sound.playVoice(praiseIds[praiseIndex]) {
            if (serial == questionSerial) onQuestionPassed(question)
        }
    }

    /** 答错：警告音 + 眩晕摇晃，题目保留；**倒计时不受影响，继续走** */
    private fun onWrong(index: Int, question: Question) {
        // 主流程中首次答错即收录进错题本（同题面只收录一次）
        GameCenter.recordWrong(question)
        sound.playSfx(R.raw.sfx_warn)
        sound.playSfx(R.raw.sfx_dizzy)
        dizzyAnimation(index)
        showHint(getString(R.string.hint_wrong), R.color.hint_wrong)

        // 立即恢复可答题状态；倒计时全程独立运行，这里不 pause 也不 resume
        state = State.ASKING
        sound.playVoice(R.raw.wrong_1)
    }

    /** 倒计时归零：超时判负，记入错题集，播提示音后进入下一题 */
    private fun onTimeout() {
        if (state != State.ASKING) return
        val question = GameCenter.currentQuestion ?: return
        state = State.RESOLVING
        sound.stopVoice()
        Log.d(TAG, "超时判负（${question.text}），记入错题集")
        // 超时同样算错题（错题巩固轮重复记录会自动去重）
        GameCenter.recordWrong(question)
        showHint(getString(R.string.hint_timeout), R.color.hint_wrong)
        sound.playSfx(R.raw.sfx_warn)

        val serial = questionSerial
        sound.playVoice(R.raw.timeout_1) {
            if (serial != questionSerial) return@playVoice
            onQuestionPassed(question)
        }
    }

    /**
     * 一题结束（答对或超时）：按阶段推进流程。
     *
     * - 主流程答满 30 题：有错题 → 进入错题巩固；零错题 → 直接完成；
     * - 错题巩固轮：移除当前题，队列空 → 完成，否则继续。
     */
    private fun onQuestionPassed(question: Question) {
        if (GameCenter.phase == GameCenter.Phase.REVIEW) {
            GameCenter.removeCurrentReviewQuestion()
        }
        Log.d(
            TAG,
            "推进流程：阶段=${GameCenter.phase} 题号=${GameCenter.questionIndex} 答对=${GameCenter.correctCount} 错题本=${GameCenter.wrongBook.size} 巩固剩余=${GameCenter.reviewQueue.size}"
        )
        when {
            GameCenter.phase == GameCenter.Phase.MAIN && GameCenter.isGoalReached -> {
                if (GameCenter.wrongBook.isNotEmpty()) {
                    // 进入错题巩固：先播提示语，再开始巩固第一题
                    GameCenter.startReview()
                    GameCenter.currentQuestion = null
                    showHint(getString(R.string.enter_review), R.color.hint_review)
                    sound.playVoice(R.raw.flow_review) { loadQuestion() }
                } else {
                    finishStage()
                }
            }
            GameCenter.phase == GameCenter.Phase.REVIEW && GameCenter.reviewQueue.isEmpty() -> {
                finishStage()
            }
            else -> loadQuestion()
        }
    }

    /** 阶段完成：解锁下一档并弹完成提示 */
    private fun finishStage() {
        state = State.FINISHED
        countdown.stop()
        GameCenter.currentQuestion = null

        val next = GameCenter.unlockNext()
        if (next != null) {
            (requireActivity() as MainActivity).saveUnlocked(GameCenter.unlockedIndex)
        }
        val message = when {
            next != null -> getString(R.string.finish_with_unlock, next.displayName)
            GameCenter.phase == GameCenter.Phase.REVIEW -> getString(R.string.review_finish_msg)
            else -> getString(R.string.finish_plain)
        }
        // 完成语音与弹窗同时出现（看门狗兜底，不影响流程）
        sound.playVoice(if (next != null) R.raw.flow_unlock else R.raw.flow_finish)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.finish_title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton(R.string.ok) { _, _ ->
                (requireActivity() as MainActivity).showLevelSelect()
            }
            .show()
    }

    // ================= 动效 =================

    /** 动物从土里冒出 */
    private fun popIn(pop: View, delayMs: Long) {
        pop.animate().cancel()
        pop.translationY = dp(64f)
        pop.alpha = 0f
        pop.animate()
            .translationY(0f)
            .alpha(1f)
            .setStartDelay(delayMs)
            .setDuration(340L)
            .setInterpolator(OvershootInterpolator(1.6f))
            .start()
    }

    /** 锤子砸击：由斜上方挥下 + 淡出 */
    private fun playHammer(index: Int) {
        val hammer = hammers[index]
        hammer.animate().cancel()
        hammer.visibility = View.VISIBLE
        hammer.alpha = 1f
        hammer.rotation = -42f
        hammer.animate()
            .rotation(14f)
            .setDuration(130L)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                hammer.animate().alpha(0f).setDuration(120L).start()
            }
            .start()
    }

    /** 答错眩晕：头部大幅摇摆 + 身体左右轻晃 */
    private fun dizzyAnimation(index: Int) {
        val animal = animals[index]
        val shake = ObjectAnimator.ofFloat(animal, View.ROTATION, 0f, -22f, 20f, -16f, 12f, 0f)
            .apply { duration = 760L }
        val sway = ObjectAnimator.ofFloat(animal, View.TRANSLATION_X, 0f, dp(4f), -dp(4f), 0f)
            .apply { duration = 760L }
        listOf(shake, sway).forEach { animator ->
            animator.addListener(onEndRemove)
            activeAnimators.add(animator)
            animator.start()
        }
    }

    private val onEndRemove = object : Animator.AnimatorListener {
        override fun onAnimationStart(animation: Animator) {}
        override fun onAnimationEnd(animation: Animator) {
            activeAnimators.remove(animation)
        }

        override fun onAnimationCancel(animation: Animator) {
            activeAnimators.remove(animation)
        }

        override fun onAnimationRepeat(animation: Animator) {}
    }

    /** 切题前取消残留动效并复位所有洞的状态 */
    /** 切题前取消残留动效（先取快照，避免 cancel 回调里 remove 造成并发修改崩溃） */
    private fun cancelAnimations() {
        val snapshot = activeAnimators.toList()
        activeAnimators.clear()
        snapshot.forEach { animator ->
            try {
                animator.cancel()
            } catch (e: Exception) {
                Log.w(TAG, "取消动画异常", e)
            }
        }
    }

    /** 复位六个土洞（绿框、锤子、动物姿态、数字牌） */
    private fun resetHoles() {
        holes.indices.forEach { index ->
            val pop = pops[index]
            pop.animate().cancel()
            pop.translationY = 0f
            pop.alpha = 1f

            val animal = animals[index]
            animal.animate().cancel()
            animal.rotation = 0f
            animal.translationX = 0f
            animal.scaleX = 1f
            animal.scaleY = 1f

            val hammer = hammers[index]
            hammer.animate().cancel()
            hammer.visibility = View.GONE
            hammer.alpha = 1f
            hammer.rotation = -42f

            val green = greens[index]
            green.animate().cancel()
            green.visibility = View.GONE
            green.scaleX = 1f
            green.scaleY = 1f
        }
    }

    // ================= 设置与语音 =================

    /** 状态栏 / 卡片上的一键静音 */
    private fun toggleVoice() {
        val enabled = !settings.voiceEnabled()
        applyVoiceEnabled(enabled)
    }

    /** 应用语音开关（按钮、设置弹窗共用） */
    private fun applyVoiceEnabled(enabled: Boolean) {
        settings.saveVoiceEnabled(enabled)
        sound.voiceEnabled = enabled
        updateMuteIcon()
        if (!enabled) {
            // 只停播报，绝不干预倒计时
            sound.stopVoice()
        }
    }

    /** 刷新静音按钮图标 */
    private fun updateMuteIcon() {
        binding.btnMute.setImageResource(
            if (settings.voiceEnabled()) R.drawable.ic_volume_on else R.drawable.ic_volume_off
        )
    }

    /** 设置弹窗：倒计时调节 + 语音开关 + 选择难度入口 */
    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)
        val tvValue = dialogView.findViewById<TextView>(R.id.tvCountdownValue)
        val btnMinus = dialogView.findViewById<ImageView>(R.id.btnMinus)
        val btnPlus = dialogView.findViewById<ImageView>(R.id.btnPlus)
        val switchVoice = dialogView.findViewById<SwitchCompat>(R.id.switchVoice)

        var seconds = settings.countdownSeconds()
        fun refresh() {
            tvValue.text = getString(R.string.countdown_seconds, seconds)
            btnMinus.isEnabled = seconds > SettingsStore.MIN_COUNTDOWN
            btnMinus.alpha = if (btnMinus.isEnabled) 1f else 0.35f
            btnPlus.isEnabled = seconds < SettingsStore.MAX_COUNTDOWN
            btnPlus.alpha = if (btnPlus.isEnabled) 1f else 0.35f
        }
        refresh()

        btnMinus.setOnClickListener {
            seconds = (seconds - SettingsStore.STEP_COUNTDOWN)
                .coerceAtLeast(SettingsStore.MIN_COUNTDOWN)
            settings.saveCountdownSeconds(seconds)
            refresh()
        }
        btnPlus.setOnClickListener {
            seconds = (seconds + SettingsStore.STEP_COUNTDOWN)
                .coerceAtMost(SettingsStore.MAX_COUNTDOWN)
            settings.saveCountdownSeconds(seconds)
            refresh()
        }

        switchVoice.isChecked = settings.voiceEnabled()
        switchVoice.setOnCheckedChangeListener { _, checked -> applyVoiceEnabled(checked) }

        // 打开设置时暂停倒计时，关闭后恢复（不占用孩子的答题时间）
        val wasRunning = countdown.isRunning
        countdown.pause()

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_title)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_done, null)
            .setNeutralButton(R.string.settings_choose_level) { _, _ ->
                sound.stopVoice()
                (requireActivity() as MainActivity).showLevelSelect()
            }
            .create()
        dialog.setOnDismissListener {
            if (state == State.ASKING && wasRunning) countdown.resume()
        }
        dialog.show()
    }

    // ================= 顶部信息 =================

    /** 更新顶部三枚奖杯 */
    private fun updateTrophies() {
        val context = requireContext()
        fun apply(imageView: ImageView, lit: Boolean, colorRes: Int) {
            imageView.setColorFilter(
                ContextCompat.getColor(context, if (lit) colorRes else R.color.trophy_gray)
            )
        }
        apply(binding.trophyBronze, GameCenter.bronzeLit(), R.color.trophy_bronze)
        apply(binding.trophySilver, GameCenter.silverLit(), R.color.trophy_silver)
        apply(binding.trophyGold, GameCenter.goldLit(), R.color.trophy_gold)
    }

    /** 更新顶部进度小字 */
    private fun updateProgress() {
        binding.progressText.text = when (GameCenter.phase) {
            GameCenter.Phase.MAIN ->
                getString(R.string.progress_main, GameCenter.questionIndex, GameCenter.TOTAL_GOAL)
            GameCenter.Phase.REVIEW ->
                getString(R.string.progress_review, GameCenter.reviewDone(), GameCenter.reviewTotal)
        }
    }

    /** 显示文字提示条 */
    private fun showHint(text: String, colorRes: Int) {
        binding.tvHint.text = text
        binding.tvHint.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
        binding.tvHint.visibility = View.VISIBLE
    }

    /** dp 转 px */
    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    override fun onPause() {
        super.onPause()
        // 退到后台：暂停倒计时并停止播报，避免时间白白流失
        if (::countdown.isInitialized) countdown.pause()
        if (::settings.isInitialized) sound.stopVoice()
    }

    override fun onResume() {
        super.onResume()
        // 仅从后台返回时恢复（设置弹窗自行管理）
        if (::countdown.isInitialized && state == State.ASKING) countdown.resume()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::countdown.isInitialized) countdown.shutdown()
        // 先取快照再取消，避免动画回调修改列表导致崩溃
        val snapshot = activeAnimators.toList()
        activeAnimators.clear()
        snapshot.forEach { animator ->
            try {
                animator.cancel()
            } catch (e: Exception) {
                // 视图已销毁，忽略
            }
        }
        _binding = null
    }

    private companion object {
        /** 诊断日志：状态流转可追溯（排查卡顿/无响应时看这里） */
        const val TAG = "AnswerFragment"
    }
}
