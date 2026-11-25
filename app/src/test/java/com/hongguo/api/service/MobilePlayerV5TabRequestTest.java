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
        // 上游 0.8.0 改走设备可信度激活（app_alert_check + 完整设备画像 query），
        // 不再依赖 Cookie；这里断言请求头集合与上游实现一致（UA + Accept + 三个签名头）。
        assertNotNull("应带 User-Agent", req.getHeaders().get("User-Agent"));
        assertNotNull("应带 Accept", req.getHeaders().get("Accept"));

        String query = req.getUrl().substring(req.getUrl().indexOf('?') + 1);
        // 37 个字段按抓包成功请求逐项对齐；设备参数必须与 TAB_UA 自洽，
        // 否则服务端判定伪造客户端，回 200 + Content-Length:0。
        for (String field : new String[]{
                "aid=8662", "app_name=novelread", "channel=update_64",
                "version_code=73932", "version_name=7.3.9.32",
                "device_type=25053RT47C", "device_brand=Redmi",
                "os=android", "os_api=36", "os_version=16",
                "manifest_version_code=73932", "update_version_code=73932",
                "resolution=1280*2772", "dpi=520", "pv_player=73932",
                "ssmix=a", "ac=wifi", "language=zh", "host_abi=arm64-v8a",
                "dragon_device_type=phone", "compliance_status=0",
                "need_personal_recommend=1", "player_so_load=1",
                "is_android_pad_screen=0",
                "video_id=7600000000000000001", "video_id_type=0", "device_level=1",
                "need_all_video_definition=1", "video_platform=1024", "source=0"}) {
            assertTrue("query 应含 " + field, query.contains(field));
        }
        // 上游 0.8.0 仍带空 from_video_id（与官方实现一致），不强制删除
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
