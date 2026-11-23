import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * video_tab v5 无正文 GET 的请求构造。
 *
 * <p>这层最容易在合并中出错（端点、query 字段、三个 Armor 头），而它错了只会表现为
 * 「200 + 空响应」，看不出原因。这里把契约钉住。
 */
public class MobilePlayerV5TabRequestTest {

    @Test
    public void requestVideoTabTargetsVideoTabEndpointWithArmorHeaders() {
        com.hongguo.api.playback.MobilePlayerV5.TabRequest req =
                com.hongguo.api.playback.MobilePlayerV5.requestVideoTab(
                        "7600000000000000001", "7432109876543210987", "1905892595382586",
                        "synthetic-cdid", System.currentTimeMillis());

        assertTrue("端点应为 b 域名 video_tab",
                req.getUrl().startsWith("https://api5-normal-sinfonlineb.fqnovel.com"
                        + "/reading/bookapi/video_tab/multi_video_model/v1/?"));
        // 只带三个 Armor 头 + UA/Accept，不应有 Content-*（无正文 GET）
        assertNotNull(req.getHeaders().get("x-khronos"));
        assertNotNull(req.getHeaders().get("x-helios"));
        assertNotNull(req.getHeaders().get("x-medusa"));
        assertEquals("不应声明 Content-Type", null, req.getHeaders().get("Content-Type"));
        assertEquals("不应声明 Content-Encoding", null, req.getHeaders().get("Content-Encoding"));
        assertEquals("不应带 x-ss-stub", null, req.getHeaders().get("x-ss-stub"));
        // 官方形态带设备 Cookie 与 store-region 头（抓包实证），缺失时服务端回 200+空正文。
        assertNotNull("应带 Cookie", req.getHeaders().get("Cookie"));
        assertTrue("Cookie 含 install_id",
                req.getHeaders().get("Cookie").contains("install_id=1905892595382586"));
        assertEquals("cn-sh", req.getHeaders().get("x-tt-store-region"));
        assertEquals("did", req.getHeaders().get("x-tt-store-region-src"));

        String query = req.getUrl().substring(req.getUrl().indexOf('?') + 1);
        for (String field : new String[]{"aid=8662", "video_id=7600000000000000001",
                "video_id_type=0", "device_level=1", "need_all_video_definition=1",
                "video_platform=1024", "source=0", "from_video_id="}) {
            assertTrue("query 应含 " + field, query.contains(field));
        }
        assertTrue("票号只出现一次", query.indexOf("_rticket=") == query.lastIndexOf("_rticket="));
    }

    @Test
    public void requestVideoTabProducesDifferentTicketsOverTime() {
        AtomicLong now = new AtomicLong(1800000000000L);
        com.hongguo.api.playback.MobilePlayerV5.TabRequest first =
                com.hongguo.api.playback.MobilePlayerV5.requestVideoTab(
                        "7600000000000000001", "7432109876543210987", "1905892595382586", "cdid", now.get());
        com.hongguo.api.playback.MobilePlayerV5.TabRequest second =
                com.hongguo.api.playback.MobilePlayerV5.requestVideoTab(
                        "7600000000000000001", "7432109876543210987", "1905892595382586", "cdid",
                        now.addAndGet(1000));
        assertTrue("不同时间应得到不同签名 URL", !first.getUrl().equals(second.getUrl()));
    }
}
