package com.alex.mathmole.data

import android.content.Context

/**
 * 设置项本地存储（SharedPreferences，无需任何权限）。
 *
 * 目前包含：
 * - 答题倒计时秒数（默认 15 秒，可选 5~60 秒，步进 5 秒）；
 * - 语音播报开关（默认开启）。
 */
class SettingsStore(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /** 读取答题倒计时秒数（越界会自动收敛到合法范围） */
    fun countdownSeconds(): Int =
        sp.getInt(KEY_COUNTDOWN, DEFAULT_COUNTDOWN).coerceIn(MIN_COUNTDOWN, MAX_COUNTDOWN)

    /** 保存答题倒计时秒数 */
    fun saveCountdownSeconds(seconds: Int) {
        sp.edit().putInt(KEY_COUNTDOWN, seconds.coerceIn(MIN_COUNTDOWN, MAX_COUNTDOWN)).apply()
    }

    /** 语音播报是否开启 */
    fun voiceEnabled(): Boolean = sp.getBoolean(KEY_VOICE, true)

    /** 保存语音播报开关 */
    fun saveVoiceEnabled(enabled: Boolean) {
        sp.edit().putBoolean(KEY_VOICE, enabled).apply()
    }

    companion object {
        const val DEFAULT_COUNTDOWN = 15
        const val MIN_COUNTDOWN = 5
        const val MAX_COUNTDOWN = 60
        const val STEP_COUNTDOWN = 5

        private const val PREF_NAME = "game_settings"
        private const val KEY_COUNTDOWN = "countdown_seconds"
        private const val KEY_VOICE = "voice_enabled"
    }
}
