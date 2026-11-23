package com.hongguo.api.service;

import com.hongguo.api.util.Http;
import com.hongguo.api.util.Json;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

/**
 * 公开单集媒体接口：原生取源（video_tab / player / 兼容路由）全部失败时的最后兜底。
 *
 * <p>来源：LiuWQ0809/hongguo-android 的 {@code PublicEpisodeSource}（local.16 引入）。
 * 该接口按分集 id 直接返回可直接播放的 {@code main_url}，不经过 v5 签名，因此不受
 * 匿名设备风控影响。
 *
 * <p><b>现状（2026-11-23 实测）</b>：该接口对所有调用返回
 * {@code HTTP 403 {"error":"public_fallback_unavailable"}}，与参考实现发布说明中
 * 「六个随机分集的公开取源接口均返回 HTTP 403」一致。因此本类只作为架构保留：
 * 端点可由配置覆盖，接口恢复后无需改代码即可生效，平时不会拖慢正常取源
 * （仅在原生全失败后才调用，且失败只记入原异常的 suppressed）。
 */
final class PublicEpisodeSource {
    /** 默认端点；可用 {@code playback.public_fallback_endpoint} 覆盖。 */
    private static final String DEFAULT_ENDPOINT =
            "https://hongguoduanju.com/api/video/protected/fallback-play-info";
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;
    private static final int TIMEOUT_MS = 6000;

    private PublicEpisodeSource() {}

    static String endpoint() {
        String configured = Config.str("playback.public_fallback_endpoint", null);
        return configured == null || configured.trim().isEmpty() ? DEFAULT_ENDPOINT : configured.trim();
    }

    /** 取公开媒体地址；不可用时抛异常，由调用方记入 suppressed 并继续报告原生失败。 */
    static Stream.VideoTrack fetch(String episodeId) throws Exception {
        if (episodeId == null || !episodeId.matches("[0-9]{1,30}")) {
            throw new IllegalArgumentException("分集编号无效");
        }
        byte[] body = Json.stringify(Collections.singletonMap("item_id", episodeId))
                .getBytes(StandardCharsets.UTF_8);
        Http.Req request = new Http.Req(endpoint()).method("POST").timeout(TIMEOUT_MS).body(body)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json");
        request.followRedirect = false;
        Http.Resp response = Http.sendPlaybackMetadata(request, Long.MAX_VALUE);
        if (response.status == 403) throw new IOException("本集公开片源不可用（HTTP 403）");
        if (response.status != 200 || response.body.length == 0
                || response.body.length > MAX_RESPONSE_BYTES) {
            throw new IOException("公开片源接口未返回有效数据（HTTP " + response.status + "）");
        }
        return parse(response.text(), System.currentTimeMillis() / 1000);
    }

    /** 解析 {@code {base_resp:{StatusCode}, main_url, url_expire}}。 */
    static Stream.VideoTrack parse(String text, long nowSeconds) throws Exception {
        Map<String, Object> reply = Json.parseObject(text);
        Map<String, Object> base = Json.optObj(reply.get("base_resp"));
        Object code = base == null ? reply.get("code") : base.get("StatusCode");
        if (Json.optLong(code, -1) != 0) throw new IOException("公开片源不可用");
        Object expires = reply.get("url_expire");
        if (expires != null && Json.optLong(expires, 0) <= nowSeconds + 5) {
            throw new IOException("公开片源已过期");
        }
        String raw = Json.optStr(reply.get("main_url"), "");
        String url = AppSource.checkedUrl(raw, AppSource.VOD_DOMAINS);
        if (URI.create(url).getFragment() != null) throw new IOException("公开片源地址无效");
        Stream.VideoTrack track = new Stream.VideoTrack();
        track.url = url;
        // 公开接口只给单一清晰度；按 360p 保守标注，避免播放器按高码率期待缓冲。
        track.definition = "360p";
        return track;
    }
}
