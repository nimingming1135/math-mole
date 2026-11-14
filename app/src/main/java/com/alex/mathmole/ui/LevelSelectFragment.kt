package com.alex.mathmole.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.alex.mathmole.MainActivity
import com.alex.mathmole.R
import com.alex.mathmole.databinding.FragmentLevelSelectBinding
import com.alex.mathmole.logic.GameCenter
import com.alex.mathmole.model.Level

/**
 * 难度关卡选择页：六档难度（10/20/30/50/80/100 以内）。
 *
 * 规则：
 * - 初始仅解锁 10 以内；完成前一档 30 题目标且错题巩固通关后自动解锁下一档；
 * - 已解锁档位：马卡龙色高亮卡片，点击进入（切换档位 = 开启全新会话）；
 * - 未解锁档位：灰色 + 锁图标，点击仅给出轻提示。
 */
class LevelSelectFragment : Fragment() {

    private var _binding: FragmentLevelSelectBinding? = null
    private val binding get() = _binding!!

    /** 每档难度配一个动物点缀 */
    private val levelAnimals = listOf(
        R.drawable.ic_cat, R.drawable.ic_pig, R.drawable.ic_dog,
        R.drawable.ic_monkey, R.drawable.ic_rabbit, R.drawable.ic_bear
    )

    /** 已解锁卡片的马卡龙底色（循环使用） */
    private val unlockedColors = listOf(R.color.mint, R.color.pink, R.color.lavender, R.color.sky)

    // 卡片控件列表化，便于按档位索引循环处理
    private val cards: List<LinearLayout> by lazy {
        listOf(
            binding.cardLevel0, binding.cardLevel1, binding.cardLevel2,
            binding.cardLevel3, binding.cardLevel4, binding.cardLevel5
        )
    }
    private val levelIcons by lazy {
        listOf(
            binding.iconLevel0, binding.iconLevel1, binding.iconLevel2,
            binding.iconLevel3, binding.iconLevel4, binding.iconLevel5
        )
    }
    private val levelTexts by lazy {
        listOf(
            binding.textLevel0, binding.textLevel1, binding.textLevel2,
            binding.textLevel3, binding.textLevel4, binding.textLevel5
        )
    }
    private val levelLocks by lazy {
        listOf(
            binding.lockLevel0, binding.lockLevel1, binding.lockLevel2,
            binding.lockLevel3, binding.lockLevel4, binding.lockLevel5
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentLevelSelectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 左上角返回按钮：回到答题页（会话进度保留，切回后恢复显示当前题目）
        binding.btnBack.setOnClickListener {
            (requireActivity() as MainActivity).sound.stopVoice()
            (requireActivity() as MainActivity).showAnswer()
        }
        Level.entries.forEachIndexed { index, level ->
            val unlocked = index <= GameCenter.unlockedIndex
            levelIcons[index].setImageResource(levelAnimals[index])
            levelTexts[index].text = level.displayName
            val card = cards[index]
            if (unlocked) {
                levelLocks[index].visibility = View.GONE
                card.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), unlockedColors[index % unlockedColors.size])
                )
                card.setOnClickListener {
                    // 手动选难度：切换档位即开启全新会话（题目生成、奖杯计数、错题收录按新档位适配）
                    GameCenter.switchLevel(level)
                    (requireActivity() as MainActivity).showAnswer()
                }
            } else {
                levelLocks[index].visibility = View.VISIBLE
                levelIcons[index].alpha = 0.35f
                card.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.level_locked_bg)
                )
                card.setOnClickListener {
                    Toast.makeText(
                        requireContext(), R.string.level_locked_toast, Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
