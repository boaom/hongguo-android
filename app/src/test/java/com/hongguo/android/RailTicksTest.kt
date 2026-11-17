package com.hongguo.android

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 集刻度的抽样规则：远端设备只有一千多像素宽，1000 集不可能画 1000 根。
 *
 * <p>这套断言钉住三件事：没看是零根、差一集看完不能显示满格红、看过一集不能显示成没看过。
 * 这条规则属于设计的语义部分（灰=未看、红=已看），改它等于改这套主题说的话，所以要有测试守着。
 */
class RailTicksTest {

    @Test
    fun nothingWatchedLightsNoTick() {
        assertEquals(0, railWatchedTicks(total = 1000, watched = 0, shown = 60))
        assertEquals(0, railWatchedTicks(total = 80, watched = 0, shown = 80))
        // 负数是坏输入，也不该点亮任何一根
        assertEquals(0, railWatchedTicks(total = 80, watched = -5, shown = 80))
    }

    @Test
    fun everythingWatchedLightsEveryTick() {
        assertEquals(60, railWatchedTicks(total = 1000, watched = 1000, shown = 60))
        assertEquals(80, railWatchedTicks(total = 80, watched = 80, shown = 80))
        // 超过总数也按看完处理，不能越界
        assertEquals(60, railWatchedTicks(total = 1000, watched = 1200, shown = 60))
    }

    @Test
    fun oneEpisodeBeforeTheEndNeverLooksFinished() {
        // 999/1000 是 99.9%，抽稀成 60 格时若四舍五入会得到 60/60 —— 满格红会骗人。
        assertEquals(59, railWatchedTicks(total = 1000, watched = 999, shown = 60))
        assertEquals(79, railWatchedTicks(total = 80, watched = 79, shown = 80))
    }

    @Test
    fun aSingleEpisodeStillShowsProgress() {
        // 0.1% 不能因为向下取整变成 0 根（那读起来就是「一集都没看」）。
        assertEquals(1, railWatchedTicks(total = 1000, watched = 1, shown = 60))
        assertEquals(1, railWatchedTicks(total = 1000, watched = 2, shown = 60))
    }

    @Test
    fun unsampledRailsAreExact() {
        // 80 集画 80 根：一格一集，进度必须是精确的。
        assertEquals(3, railWatchedTicks(total = 80, watched = 3, shown = 80))
        assertEquals(41, railWatchedTicks(total = 81, watched = 41, shown = 81))
        assertEquals(40, railWatchedTicks(total = 81, watched = 40, shown = 81))
    }

    @Test
    fun sampledRailsStayWithinOneTickOfTheTruth() {
        // 抽样时每格代表 17 集，视觉比例与真实进度最多差一格。
        val shown = 60
        val total = 1000
        for (watched in listOf(17, 88, 500, 501, 999)) {
            val ticks = railWatchedTicks(total, watched, shown)
            val visual = ticks.toDouble() / shown
            val truth = watched.toDouble() / total
            assertTrueLike("watched=$watched 不该提前（视觉 $visual / 真实 $truth）", visual <= truth + 1e-9)
            assertTrueLike("watched=$watched 偏差超过一格", truth - visual <= 1.0 / shown + 1e-9)
        }
        // 极少数集的例外：只点亮 1 根会让比例略高于真实值，这是有意的（见函数注释）。
        assertEquals(1, railWatchedTicks(total = 1000, watched = 1, shown = 60))
    }

    @Test
    fun zeroTotalIsSafe() {
        assertEquals(0, railWatchedTicks(total = 0, watched = 0, shown = 0))
        assertEquals(0, railWatchedTicks(total = 0, watched = 5, shown = 10))
        assertEquals(0, railWatchedTicks(total = 0, watched = 5, shown = 0))
    }

    private fun assertTrueLike(message: String, condition: Boolean) {
        if (!condition) throw AssertionError(message)
    }
}