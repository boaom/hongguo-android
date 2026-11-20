// SPDX-License-Identifier: GPL-3.0-only
package com.hongguo.api.service;

import com.hongguo.api.util.Json;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * v5 播放响应（{@code multi_video_model/player/v1}）的直解。
 *
 * <p>上游 {@code MobilePlayback.ladder} 同时支持 {@code data[vid]}（flat）与
 * {@code data["1004"][vid]} 两种形态；只取精确分集号，绝不借用同批响应里其他分集的模型。
 * 这些用例不需要网络，也不注册身份 —— 只喂字符串。
 */
public class MobileV5ResponseTest {

    private static final String VID = "7600000000000000001";
    private static final String OTHER = "7600000000000000002";

    @Test public void acceptsFlatDataShape() {
        String text = "{\"code\":0,\"data\":{\"" + VID + "\":{\"video_model\":"
                + "{\"video_id\":\"v-flat\",\"video_list\":[]}}}}";
        Map<String, Object> out = AppSource.parseMobileV5Response(VID, text);
        Map<String, Object> episodes = Json.optObj(out.get("data"));
        Map<String, Object> model = Json.optObj(Json.optObj(episodes.get(VID)).get("video_model"));
        assertEquals("v-flat", model.get("video_id"));
    }

    @Test public void acceptsData1004ShapeAndPrefersIt() {
        String text = "{\"code\":0,\"data\":{\"" + VID + "\":{\"video_model\":{\"video_id\":\"flat\"}},"
                + "\"1004\":{\"" + VID + "\":{\"video_model\":{\"video_id\":\"nested\"}}}}}";
        Map<String, Object> out = AppSource.parseMobileV5Response(VID, text);
        Map<String, Object> episodes = Json.optObj(out.get("data"));
        Map<String, Object> model = Json.optObj(Json.optObj(episodes.get(VID)).get("video_model"));
        assertEquals("data.1004 优先", "nested", model.get("video_id"));
    }

    @Test public void acceptsVideoModelAsJsonString() {
        String text = "{\"code\":0,\"data\":{\"1004\":{\"" + VID
                + "\":{\"video_model\":\"{\\\"video_id\\\":\\\"as-string\\\"}\"}}}}";
        Map<String, Object> out = AppSource.parseMobileV5Response(VID, text);
        Map<String, Object> episodes = Json.optObj(out.get("data"));
        Map<String, Object> model = Json.optObj(Json.optObj(episodes.get(VID)).get("video_model"));
        assertEquals("as-string", model.get("video_id"));
    }

    @Test public void neverBorrowsAnotherEpisodeModel() {
        String text = "{\"code\":0,\"data\":{\"1004\":{\"" + OTHER
                + "\":{\"video_model\":{\"video_id\":\"wrong\"}}}}}";
        try {
            AppSource.parseMobileV5Response(VID, text);
            fail("不得借用其他分集的模型");
        } catch (IllegalStateException e) {
            assertEquals("App 接口未返回所选分集", e.getMessage());
        }
    }

    @Test public void reportsUpstreamRejectionCode() {
        try {
            AppSource.parseMobileV5Response(VID, "{\"code\":100103,\"data\":{}}");
            fail("非 0 code 应当作上游拒绝");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("100103"));
        }
    }

    @Test(expected = AppSource.EmptyPlaybackResponseException.class)
    public void emptyBodyIsEmptyResponse() {
        AppSource.parseMobileV5Response(VID, "   ");
    }

    @Test public void v5BodyCarriesDrSceneAndDeviceLevelOne() {
        String body = new String(AppSource.mobileV5Body(VID), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(body.contains("\"dr_scene\":\"player\""));
        assertTrue(body.contains("\"device_level\":1"));
        assertTrue(body.contains("\"mixed_video_id_map\":{\"1004\":[\"" + VID + "\"]}"));
        assertTrue(body.contains("\"video_platform\":1024"));
        assertTrue(body.contains("\"use_server_dns\":false"));
        assertTrue(body.contains("\"disable_video_relate_book\":false"));
        // dr_scene 必须在最后（上游 JSONObject 的插入序）
        assertTrue(body.trim().endsWith("\"dr_scene\":\"player\"}"));
    }

    @Test public void v5QueryHasExactlyThirteenFieldsInUpstreamOrder() {
        PlaybackDevice.Identity identity =
                new PlaybackDevice.Identity("7432109876543210987", "7432109876543210123", "cdid-x", "");
        Map<String, String> query = AppSource.mobileV5Query(identity, 1800000000000L);
        List<String> names = new java.util.ArrayList<>(query.keySet());
        assertEquals(13, names.size());
        assertEquals("aid", names.get(0));
        assertEquals("app_name", names.get(1));
        assertEquals("version_code", names.get(2));
        assertEquals("version_name", names.get(3));
        assertEquals("channel", names.get(4));
        assertEquals("device_platform", names.get(5));
        assertEquals("device_type", names.get(6));
        assertEquals("os_version", names.get(7));
        assertEquals("update_version_code", names.get(8));
        assertEquals("iid", names.get(9));
        assertEquals("cdid", names.get(10));
        assertEquals("device_id", names.get(11));
        assertEquals("_rticket", names.get(12));
        assertEquals("74332", query.get("version_code"));
        assertEquals("7.4.3.32", query.get("version_name"));
        assertEquals("vivo_8662_64", query.get("channel"));
        assertEquals("PGT-AN10", query.get("device_type"));
        assertEquals("12", query.get("os_version"));
        assertEquals("7432109876543210987", query.get("device_id"));
    }
}