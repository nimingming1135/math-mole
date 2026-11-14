package com.alex.mathmole.data

import android.content.Context

/**
 * 解锁进度本地存储：仅保存"最高已解锁档位索引"这一项。
 * 使用系统自带 SharedPreferences（无需任何权限）。
 *
 * 注意：错题数据不在此持久化——错题仅存内存，退出 App 自动清空。
 */
class LevelProgressStore(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /** 读取已解锁的最高档位索引，默认 0（即 10 以内档） */
    fun load(): Int = sp.getInt(KEY_UNLOCKED, 0)

    /** 保存已解锁的最高档位索引 */
    fun save(unlockedIndex: Int) {
        sp.edit().putInt(KEY_UNLOCKED, unlockedIndex).apply()
    }

    private companion object {
        const val PREF_NAME = "level_progress"
        const val KEY_UNLOCKED = "unlocked_level_index"
    }
}
