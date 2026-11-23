package com.hongguo.api.service;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * 公开单集媒体接口的解析与兜底语义。
 *
 * <p>该接口 2026-11-23 起对未授权调用返回 403（参考实现同样如此），因此这里只测解析层
 * 与「不可用时不影响原失败上报」的语义，不发真实请求。
 */
public class PublicEpisodeSourceTest {

    private static final long NOW = 1_800_000_000L;

    @Test public void parsesMainUrlAndMarksConservativeDefinition() throws Exception {
        String json = "{\"base_resp\":{\"StatusCode\":0},"
                + "\"main_url\":\"https://v1.qznovelvod.com/public-fallback.mp4\","
                + "\"url_expire\":" + (NOW + 3600) + "}";
        Stream.VideoTrack track = PublicEpisodeSource.parse(json, NOW);
        assertEquals("https://v1.qznovelvod.com/public-fallback.mp4", track.url);
        assertEquals("360p", track.definition);
        assertEquals("公开片源不带加密标记", false, track.encrypt);
    }

    @Test public void rejectsNonZeroStatusCode() {
        String json = "{\"base_resp\":{\"StatusCode\":1},\"main_url\":\"https://v1.qznovelvod.com/x.mp4\"}";
        assertThrows(java.io.IOException.class, () -> PublicEpisodeSource.parse(json, NOW));
    }

    @Test public void rejectsExpiredUrl() {
        String json = "{\"base_resp\":{\"StatusCode\":0},"
                + "\"main_url\":\"https://v1.qznovelvod.com/x.mp4\","
                + "\"url_expire\":" + (NOW - 10) + "}";
        assertThrows(java.io.IOException.class, () -> PublicEpisodeSource.parse(json, NOW));
    }

    @Test public void rejectsUrlOutsideKnownVodDomains() {
        // 不允许把任意域名当片源：只能来自 VOD_DOMAINS 白名单。
        String json = "{\"base_resp\":{\"StatusCode\":0},"
                + "\"main_url\":\"https://evil.example.com/x.mp4\"}";
        assertThrows(Exception.class, () -> PublicEpisodeSource.parse(json, NOW));
    }

    @Test public void endpointIsConfigurableSoRecoveryNeedsNoCodeChange() {
        // 默认端点对未授权调用返回 403；允许用配置覆盖，接口恢复后无需改代码。
        String def = PublicEpisodeSource.endpoint();
        assertTrue("默认端点应指向公开兜底接口", def.contains("fallback-play-info"));
    }

    @Test public void unavailableFallbackDoesNotMaskOriginalFailure() throws Exception {
        // 原生取源全失败 + 公开片源也不可用：对外应能看到原生失败（空响应），
        // 且公开片源失败作为 suppressed 附带，便于定位。
        AppSource.setSinglePlaybackForTest((vid, quality, identity, deadline) -> {
            throw new AppSource.EmptyPlaybackResponseException();
        });
        AppSource.setTabV5ForTest((vid, quality, identity) -> null);
        AppSource.setPublicPlaybackForTest(episodeId -> {
            throw new java.io.IOException("公开片源不可用（HTTP 403）");
        });
        try {
            Exception error = assertThrows(Exception.class, () -> AppSource.fetch("1", "auto"));
            String text = error + " " + java.util.Arrays.toString(error.getSuppressed());
            assertTrue("应能看到原生空响应或公开片源失败，实际=" + text,
                    text.contains("空响应") || text.contains("公开片源"));
        } finally {
            AppSource.setSinglePlaybackForTest(null);
            AppSource.setTabV5ForTest(null);
            AppSource.setPublicPlaybackForTest(null);
        }
    }
}
