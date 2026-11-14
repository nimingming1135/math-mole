package com.alex.mathmole.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import com.alex.mathmole.R
import kotlin.random.Random

/**
 * 金色星星飘落特效（连续答对 3 题时触发）。
 *
 * 做法：向特效容器内动态添加若干星星 ImageView，
 * 从容器顶部外随机横向位置入场，做"下落 + 旋转"动画，
 * 飘出容器底部后自动移除，不残留任何视图。
 */
object StarFallHelper {

    /**
     * 在 [container] 中撒下 [count] 个金色星星。
     * 容器尚未完成布局测量（宽高为 0）时静默跳过，不影响主流程。
     */
    fun dropStars(container: ViewGroup, count: Int = 9) {
        val width = container.width
        val height = container.height
        if (width <= 0 || height <= 0) return
        val density = container.resources.displayMetrics.density
        repeat(count) {
            // 20~35dp 的随机大小，视觉更有层次
            val sizePx = ((20 + Random.nextInt(16)) * density).toInt()
            val star = ImageView(container.context).apply {
                setImageResource(R.drawable.ic_star)
                layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)
                x = Random.nextInt((width - sizePx).coerceAtLeast(1)).toFloat()
                translationY = -sizePx.toFloat() // 从容器顶部外入场
                rotation = Random.nextInt(60).toFloat()
            }
            container.addView(star)
            star.animate()
                .translationY((height + sizePx).toFloat()) // 飘落到容器底部外
                .rotation((180 + Random.nextInt(360)).toFloat()) // 随机旋转圈数
                .setDuration((900 + Random.nextInt(500)).toLong())
                .setStartDelay(Random.nextInt(350).toLong()) // 错落下落更自然
                .withEndAction { container.removeView(star) }
                .start()
        }
    }
}
