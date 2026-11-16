package com.hongguo.api.util;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** 测试专用：合成 {@link Http.Resp}（构造器包内可见，测试同包直调）。 */
public final class HttpForTest {
    private HttpForTest() {}

    public static Http.Resp resp(int status, String text) {
        return new Http.Resp(status, text.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                Collections.<String, List<String>>emptyMap());
    }

    public static Http.Resp resp(int status, byte[] body) {
        return new Http.Resp(status, body, Collections.<String, List<String>>emptyMap());
    }

    public static Http.Resp empty(int status) {
        return new Http.Resp(status, new byte[0], Collections.<String, List<String>>emptyMap());
    }

    public static Map<String, List<String>> noHeaders() {
        return Collections.emptyMap();
    }
}
