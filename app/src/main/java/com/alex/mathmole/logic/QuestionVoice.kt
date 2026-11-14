package com.alex.mathmole.logic

import com.alex.mathmole.R
import com.alex.mathmole.model.Question

/**
 * 题面朗读音频的拼接规则（纯函数，便于单元测试）。
 *
 * 例如 `7 + 2 = ?` → 依次播放「7」「加」「2」三段预制音频（不读"等于几"，更简洁）。
 */
object QuestionVoice {

    /** 数字 0~100 的朗读音频（下标即数字本身） */
    private val numberIds = intArrayOf(
        R.raw.num_0, R.raw.num_1, R.raw.num_2, R.raw.num_3, R.raw.num_4,
        R.raw.num_5, R.raw.num_6, R.raw.num_7, R.raw.num_8, R.raw.num_9,
        R.raw.num_10, R.raw.num_11, R.raw.num_12, R.raw.num_13, R.raw.num_14,
        R.raw.num_15, R.raw.num_16, R.raw.num_17, R.raw.num_18, R.raw.num_19,
        R.raw.num_20, R.raw.num_21, R.raw.num_22, R.raw.num_23, R.raw.num_24,
        R.raw.num_25, R.raw.num_26, R.raw.num_27, R.raw.num_28, R.raw.num_29,
        R.raw.num_30, R.raw.num_31, R.raw.num_32, R.raw.num_33, R.raw.num_34,
        R.raw.num_35, R.raw.num_36, R.raw.num_37, R.raw.num_38, R.raw.num_39,
        R.raw.num_40, R.raw.num_41, R.raw.num_42, R.raw.num_43, R.raw.num_44,
        R.raw.num_45, R.raw.num_46, R.raw.num_47, R.raw.num_48, R.raw.num_49,
        R.raw.num_50, R.raw.num_51, R.raw.num_52, R.raw.num_53, R.raw.num_54,
        R.raw.num_55, R.raw.num_56, R.raw.num_57, R.raw.num_58, R.raw.num_59,
        R.raw.num_60, R.raw.num_61, R.raw.num_62, R.raw.num_63, R.raw.num_64,
        R.raw.num_65, R.raw.num_66, R.raw.num_67, R.raw.num_68, R.raw.num_69,
        R.raw.num_70, R.raw.num_71, R.raw.num_72, R.raw.num_73, R.raw.num_74,
        R.raw.num_75, R.raw.num_76, R.raw.num_77, R.raw.num_78, R.raw.num_79,
        R.raw.num_80, R.raw.num_81, R.raw.num_82, R.raw.num_83, R.raw.num_84,
        R.raw.num_85, R.raw.num_86, R.raw.num_87, R.raw.num_88, R.raw.num_89,
        R.raw.num_90, R.raw.num_91, R.raw.num_92, R.raw.num_93, R.raw.num_94,
        R.raw.num_95, R.raw.num_96, R.raw.num_97, R.raw.num_98, R.raw.num_99,
        R.raw.num_100,
    )

    /** 组装一道题的朗读音频序列（数字 → 加/减 → 数字） */
    fun idsFor(question: Question): List<Int> = buildList {
        add(numberIds[question.a.coerceIn(0, 100)])
        add(if (question.isAddition) R.raw.op_add else R.raw.op_sub)
        add(numberIds[question.b.coerceIn(0, 100)])
    }

    /**
     * 全部朗读音频（供启动时一次性预加载）。
     * 顺序很重要：SoundPool 按调用顺序解码，所以把**最常用的小数字与运算符放在最前**，
     * 保证首题朗读几乎不用等预加载。
     */
    fun allResIds(): IntArray {
        val priority = intArrayOf(R.raw.op_add, R.raw.op_sub) + numberIds.copyOfRange(0, 21)
        val rest = numberIds.copyOfRange(21, numberIds.size)
        val phrases = intArrayOf(
            R.raw.praise_1, R.raw.praise_2, R.raw.praise_3,
            R.raw.praise_4, R.raw.praise_5, R.raw.praise_6,
            R.raw.praise_7, R.raw.praise_8, R.raw.praise_9,
            R.raw.praise_10, R.raw.praise_11, R.raw.praise_12,
            R.raw.wrong_1, R.raw.timeout_1,
            R.raw.flow_review, R.raw.flow_finish, R.raw.flow_unlock,
        )
        return priority + rest + phrases
    }
}
