package com.hongguo.api.service;

import com.hongguo.api.util.Config;
import com.hongguo.api.util.HttpForTest;
import java.io.File;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * 2026-11-17 播放链修复（Review 三问题之二、之三）的 JVM 证据：
 *
 * <ol>
 *   <li>新两跳（v5 与 11-13 签名两跳）受总预算与线程取消约束：预算耗尽或已取消时
 *       不再注册身份、不再发起请求；</li>
 *   <li>新两跳成功后写入 PLAYBACK_IDENTITY：连续两次成功只注册一次身份。</li>
 * </ol>
 *
 * <p>所有请求都通过 {@code setPlaybackHttpForTest} 桩掉，不打真网络；身份注册通过
 * {@code PlaybackDevice.resetForTest} 桩掉并计数。
 */
public class PlaybackNewHopsTest {
    private final AtomicInteger registrations = new AtomicInteger();
    private final AtomicInteger requests = new AtomicInteger();
    private final List<String> requestedUrls = new ArrayList<>();

    @Before public void setup() {
        Config.initialize("{}", new File(System.getProperty("java.io.tmpdir"), "new-hops-test").getPath(), "1234567890123456");
        PlaybackDevice.resetForTest((url, headers, body) -> {
            int n = registrations.incrementAndGet();
            return new PlaybackDevice.Reply(200,
                    "{\"code\":0,\"device_id\":\"73000000000000000" + n + "\",\"install_id\":\"74000000000000000" + n + "\"}");
        });
        AppSource.setPlaybackHttpForTest(req -> {
            requests.incrementAndGet();
            requestedUrls.add(req.url);
            return HttpForTest.empty(200);
        });
        // 旧路径全部按「不可用」桩掉：本测试只关心新两跳的行为。
        AppSource.setSinglePlaybackForTest((vid, quality, identity) -> {
            throw new AssertionError("Unexpected single request");
        });
        AppSource.setPlaybackTabForTest((vid, identity) -> { throw new AssertionError("Unexpected legacy request"); });
        AppSource.setPlaybackPostForTest((body, identity, host) -> { throw new AssertionError("Unexpected legacy request"); });
        // 默认让 v5 与 11-13 两跳走生产实现（Http 已被桩掉），需要覆盖语义的用例自行注入。
    }

    @After public void cleanup() {
        AppSource.setPlaybackHttpForTest(null);
        AppSource.setMobileV5PlaybackForTest(null);
        AppSource.setPlayerSignaturePlaybackForTest(null);
        AppSource.setSinglePlaybackForTest(null);
        AppSource.setPlaybackTabForTest(null);
        AppSource.setPlaybackPostForTest(null);
        PlaybackDevice.resetForTest(null);
    }

    /** 预算为 0：fetch 入口即抛超时，不注册、不发任何请求。 */
    @Test public void expiredBudgetStartsNoRegistrationOrRequest() throws Exception {
        try {
            AppSource.fetch("1", "auto", 0);
            fail("预算耗尽应立即失败");
        } catch (java.io.IOException expected) { }
        assertEquals(0, registrations.get());
        assertEquals(0, requests.get());
    }

    /** 预算耗尽后不再发起下一跳：第一跳（v5）空响应后预算已尽，11-13 两跳不得再注册或请求。 */
    @Test public void budgetExhaustedBetweenHopsStopsTheNextHop() throws Exception {
        AppSource.setMobileV5PlaybackForTest((vid, quality, identity, started, budgetMs) -> {
            requests.incrementAndGet();
            // 确定性地耗尽 1ms 预算：v5 一跳内实际等待超过预算，下一跳入口的预算检查必然失败。
            try {
                Thread.sleep(30L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            throw new AppSource.EmptyPlaybackResponseException();
        });
        try {
            AppSource.fetch("1", "auto", 1);
            fail("预算耗尽应抛超时");
        } catch (java.io.IOException expected) { }
        // v5 一跳（被桩为空响应）之后预算已尽：11-13 两跳不应注册身份，也不应发请求。
        assertEquals(1, registrations.get()); // 只有 fetch 入口的那一次
        assertEquals(1, requests.get());      // 只有 v5 桩的那一次
    }

    /** 线程取消后不再注册、不再发请求（v5 空响应后取消，11-13 两跳立即停止）。 */
    @Test public void cancellationBetweenHopsStopsRegistrationAndRequests() throws Exception {
        AppSource.setMobileV5PlaybackForTest((vid, quality, identity, started, budgetMs) -> {
            requests.incrementAndGet();
            // 取消发生在 v5 一跳之内（入口已注册身份、已发请求），下一跳不得再注册或发请求。
            Thread.currentThread().interrupt();
            throw new AppSource.EmptyPlaybackResponseException();
        });
        try {
            AppSource.fetch("1", "auto");
            fail("已取消应抛 InterruptedIOException");
        } catch (InterruptedIOException expected) {
        } finally {
            Thread.interrupted(); // 清除中断标记，避免影响后续用例
        }
        assertEquals(1, registrations.get());
        assertEquals(1, requests.get());
    }

    /** v5 成功后写入 PLAYBACK_IDENTITY：连续两次 fetch 只注册一次身份。
     *
     * <p>这里用注入缝返回片源（不经过 HTTP 与模型解析）：被测的生产行是 fetch() 里
     * v5 成功之后的 {@code PLAYBACK_IDENTITY.compareAndSet(null, identity)}。
     */
    @Test public void v5SuccessCachesIdentitySoTwoFetchesRegisterOnce() throws Exception {
        com.hongguo.api.service.Stream.VideoTrack track = new com.hongguo.api.service.Stream.VideoTrack();
        track.url = "https://v1.qznovelvod.com/new-hops-v5.mp4";
        AppSource.setMobileV5PlaybackForTest((vid, quality, identity, started, budgetMs) -> track);
        AppSource.setPlayerSignaturePlaybackForTest((vid, quality, started, budgetMs) -> {
            throw new AssertionError("v5 成功时不应落到 11-13 两跳");
        });
        AppSource.setSinglePlaybackForTest((vid, quality, identity) -> {
            throw new AssertionError("v5 成功时不应落到单集路径");
        });
        assertSame(track, AppSource.fetch("1", "auto"));
        assertSame(track, AppSource.fetch("2", "auto"));
        assertEquals(1, registrations.get()); // 第二次直接复用缓存身份，不再注册
        assertEquals(0, requests.get());      // 全程无 HTTP
    }

    /** 诊断：v5 夹具必须能独立解析成片源（失败时把真实原因带进断言消息，避免下一轮再猜）。 */
    @Test public void v5FixtureParsesToTrack() throws Exception {
        try {
            com.hongguo.api.service.Stream.VideoTrack t =
                    AppSource.parseModel("1", AppSource.parseMobileV5Response("1", v5Body("1")), "auto");
            assertNotNull("v5 夹具解析为 null", t);
            assertEquals("https://v1.qznovelvod.com/new-hops.mp4", t.url);
        } catch (Exception e) {
            fail("v5 夹具解析失败: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** 取消发生在两跳内部（注册之后、请求之前）时不再发请求。 */
    @Test public void cancellationBeforeRequestSendsNothing() throws Exception {
        AppSource.setMobileV5PlaybackForTest((vid, quality, identity, started, budgetMs) -> {
            throw new AppSource.EmptyPlaybackResponseException();
        });
        AppSource.setPlaybackHttpForTest(req -> {
            requests.incrementAndGet();
            Thread.currentThread().interrupt(); // 第一跳（tab GET）返回后取消线程
            return HttpForTest.empty(200);
        });
        try {
            AppSource.fetch("1", "auto");
            fail("已取消应抛 InterruptedIOException");
        } catch (InterruptedIOException expected) {
        } finally {
            Thread.interrupted();
        }
        assertEquals(1, requests.get()); // tab GET 发出后取消，POST 不得再发
    }

    /** 合成 v5 响应正文（flat 形态，parseMobileV5Response 认识的形状）。 */
    private static String v5Body(String vid) {
        return "{\"code\":0,\"data\":{\"" + vid + "\":{\"video_model\":" + modelJson() + "}}}";
    }

    /** 合成 video_tab 响应正文（wrapTabModel 认识的形状）。 */
    private static String tabBody(String vid) {
        return "{\"code\":0,\"data\":{\"video_model_data\":{\"" + vid + "\":{\"video_model\":" + modelJson() + "}}}}";
    }

    /** 最小可用片源模型：一个 h264 档、带合法 spade（PlaybackSourceTest 的自检真值）。 */
    private static String modelJson() {
        String spade = java.util.Base64.getEncoder().encodeToString(com.hongguo.api.util.Crypto.unhex(
                "93bc1df253ba1bf7618b19c448b806f64d810afc45b715fe5eb925f26cbf12f541ba098282"));
        return "{\"video_id\":\"v-test\",\"video_list\":[{\"codec_type\":\"h264\","
                + "\"definition\":\"720p\",\"vwidth\":1280,\"vheight\":720,"
                + "\"main_url\":\"https://v1.qznovelvod.com/new-hops.mp4\","
                + "\"encrypt_info\":{\"spade_a\":\"" + spade + "\",\"encrypt\":true}}]}";
    }
}
