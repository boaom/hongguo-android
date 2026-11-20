package com.hongguo.android

import com.hongguo.api.service.AppSource
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import org.junit.Assert.*
import org.junit.Test

/**
 * 2026-11-17 播放链修复（Review 三问题之一）的 JVM 证据：
 * 「是否值得自动重试」的纯函数分类正确，明确拒绝不再被外层自动重试抵消。
 *
 * <ul>
 *   <li>PlaybackRejectedException → false（明确业务/HTTP 拒绝，重试只会加重风控）；</li>
 *   <li>PlaybackHttpException 403/429 → false（同上，含包在 cause 链里的情况）；</li>
 *   <li>空响应 / SocketTimeout / 注册失败等偶发抖动 → true（保持「网络抖动，自动重试」）。</li>
 * </ul>
 */
class RetryablePlaybackFailureTest {

    @Test fun explicitRejectionIsNotRetryable() {
        assertFalse(retryablePlaybackFailure(AppSource.PlaybackRejectedException("播放服务拒绝请求（HTTP 403）")))
    }

    @Test fun backoffIsNotRetryable() {
        // 上游带 Retry-After 的繁忙信号：换身份重试只会空转预算，等 N 秒再试才有意义。
        assertFalse(retryablePlaybackFailure(AppSource.PlaybackBackoffException("30")))
        assertFalse(retryablePlaybackFailure(java.io.IOException("包装", AppSource.PlaybackBackoffException("30"))))
    }

    @Test fun backoffMessageShowsWaitSeconds() {
        val msg = readableFailure(AppSource.PlaybackBackoffException("30"), "准备播放")
        assertTrue("应提示秒数，实际=$msg", msg.contains("30 秒"))
        assertTrue(readableFailure(AppSource.PlaybackBackoffException("soon"), "准备播放").contains("稍后"))
    }

    @Test fun http403And429AreNotRetryable() {
        assertFalse(retryablePlaybackFailure(AppSource.PlaybackHttpException(403)))
        assertFalse(retryablePlaybackFailure(AppSource.PlaybackHttpException(429)))
    }

    @Test fun otherHttpStatusRemainsRetryable() {
        assertTrue(retryablePlaybackFailure(AppSource.PlaybackHttpException(500)))
        assertTrue(retryablePlaybackFailure(AppSource.PlaybackHttpException(502)))
    }

    @Test fun rejectionWrappedInCauseChainIsStillNotRetryable() {
        val wrapped = IOException("准备播放失败", AppSource.PlaybackHttpException(429))
        assertFalse(retryablePlaybackFailure(wrapped))
        val doubleWrapped = IOException("外层", IllegalStateException("中层", AppSource.PlaybackRejectedException("拒绝")))
        assertFalse(retryablePlaybackFailure(doubleWrapped))
    }

    @Test fun emptyResponseTimeoutAndRegistrationFailureRemainRetryable() {
        assertTrue(retryablePlaybackFailure(AppSource.EmptyPlaybackResponseException()))
        assertTrue(retryablePlaybackFailure(SocketTimeoutException("网络请求超时")))
        assertTrue(retryablePlaybackFailure(IOException("匿名播放设备注册失败，请检查网络后重试")))
        assertTrue(retryablePlaybackFailure(InterruptedIOException("网络请求超时")))
    }

    @Test fun nullCauseChainTerminatesSafely() {
        // 自引用防护：cause 链为 null 或极短时正常返回 true
        assertTrue(retryablePlaybackFailure(IOException("简单失败")))
    }
}
