package com.alex.mathmole

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.alex.mathmole.data.LevelProgressStore
import com.alex.mathmole.data.SettingsStore
import com.alex.mathmole.logic.GameCenter
import com.alex.mathmole.logic.SoundManager
import com.alex.mathmole.model.Level
import com.alex.mathmole.ui.AnswerFragment
import com.alex.mathmole.ui.LevelSelectFragment

/**
 * 主容器：承载答题页与难度选择页的 Fragment 切换。
 *
 * 启动流程：
 * 1. 从本地恢复已解锁的最高档位（错题数据不持久化，仅存内存）；
 * 2. 默认进入当前最高已解锁档位的答题页；
 * 3. 初始化预制音频播放器（SoundPool + MediaPlayer），预加载全部音效。
 */
class MainActivity : AppCompatActivity() {

    /** 预制音频播放器（各页面共享，随 Activity 销毁而释放） */
    lateinit var sound: SoundManager
        private set

    private lateinit var progressStore: LevelProgressStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupImmersiveMode()

        // 恢复持久化的解锁进度（仅此一项状态跨启动保留）
        progressStore = LevelProgressStore(this)
        GameCenter.unlockedIndex = progressStore.load()
        // 默认进入当前最高已解锁的档位
        GameCenter.startAt(Level.entries[GameCenter.unlockedIndex])

        // 预制音频播放器：语音开关取自设置，全部音频预先加载（播放零延迟）
        sound = SoundManager(this).apply {
            voiceEnabled = SettingsStore(this@MainActivity).voiceEnabled()
            preloadAll()
        }

        // 首次进入显示答题页；屏幕旋转等重建时保留现有 Fragment
        if (supportFragmentManager.findFragmentById(R.id.fragment_container) == null) {
            showAnswer()
        }
    }

    /** 全屏沉浸：隐藏状态栏与导航栏，从屏幕边缘滑动可临时呼出 */
    private fun setupImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val root = findViewById<View>(R.id.fragment_container)
        val controller = WindowInsetsControllerCompat(window, root)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    /** 对话框或其他窗口抢走焦点后，重新隐藏系统栏保持全屏 */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setupImmersiveMode()
    }

    /** 持久化保存解锁进度 */
    fun saveUnlocked(unlockedIndex: Int) {
        progressStore.save(unlockedIndex)
    }

    /** 切换到答题页 */
    fun showAnswer() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, AnswerFragment())
            .commit()
    }

    /** 切换到难度关卡选择页 */
    fun showLevelSelect() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, LevelSelectFragment())
            .commit()
    }

    override fun onDestroy() {
        if (this::sound.isInitialized) sound.shutdown()
        super.onDestroy()
    }
}
