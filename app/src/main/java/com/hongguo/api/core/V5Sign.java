// SPDX-License-Identifier: GPL-3.0-only
package com.hongguo.api.core;

import com.hongguo.api.util.Crypto;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * v5 播放签名（{@code v04.10.03} 形态），移植自 N3urda/hongguoTV commit {@code 18c37c2}
 * （2026-11-16，"fix: restore TV playback with pure Kotlin Android v5 signing"）与 {@code 14771e0}
 * 的 {@code kotlin-tv/core/.../Signer.kt}（{@code mobileV5 = true} 分支）、{@code V5Constants.kt}、
 * {@code AesV3.kt}。协议说明见该仓 {@code kotlin-tv/SIGNING_V5.md}。
 *
 * <p><b>为什么需要它</b>：2026-11-16 起上游播放接口对旧的 v3 Medusa 请求一律回 HTTP 200 + 空正文，
 * 换身份无效 —— 服务端要求 {@code v04.10.03} 的 medusa/envelope 与 {@code 0x9d4a97c2} 种子、
 * 96 轮、置 bit 5 校验和的 v5 hashF13，且请求票号必须先被调到 {@code (iv & 15) == 12}（分支 0）。
 *
 * <p><b>与既有代码的关系（本次移植不复制粘贴）</b>：hashF13、AesV3、xmxor、protobuf 编码、helios
 * 全部复用 {@link PureSign} 的实现，v5 的差异通过参数注入：
 * <ul>
 *   <li>{@code PureSign.hashF13(..., v5 = true)}：固定 {@code ivV1 = 0x9d4a97c2}、96 轮状态选择
 *       {@code (1,3,4,0,6,5,2,0,7,7)}、校验和 {@code | 0x20}；</li>
 *   <li>{@code PureSign.AesV3(key, ts, v5 = true)}：换 S 盒段/行置换/列序/初始字与位序表；</li>
 *   <li>{@code PureSign.helios(ts, random, license)}：v5 的 license 为 {@link #LICENSE}。</li>
 * </ul>
 * 本类只补 v5 独有的东西：常量、medusaV5 的消息编排、票号搜索与请求头集合。
 *
 * <p>签名绑定「最终 query 原文 + 实际发送的 body（gzip 后的字节）+ 同一个 khronos」，
 * 三者任何一处变动都必须重签，否则分支判定与摘要会对不上。
 */
public final class V5Sign {

    private V5Sign() {}

    /** {@code V5Constants.magic}（16 字节信封魔数）。 */
    static final byte[] MAGIC = Crypto.unhex("2d4b4fca49750d433fb5ae2c226dcc56");

    /** {@code V5Constants.license}：写进 medusa 字段 6 与 helios 明文段。 */
    public static final String LICENSE = "646637794";

    /** {@code V5Constants.signKey}：medusa 的 sm3 混淆键，同时派生 AES 的 key/iv（md5 前后半段）。 */
    static final byte[] SIGN_KEY = Crypto.unhex(
            "b23c2a470a20d105dc3f8c4438f8272ed46d6c89ffdcdedd5108698d26e9489d");

    /** {@code V5Constants.sbox}：4096 字节（4 段 × 1024），按 khronos 低 2 位选 256 字节一段。 */
    static final byte[] V5_SBOX = Crypto.unhex(
            "53290fe9e51f316f90f74a7e034d367784ab49237cd0135cdc632c322fd3be0e2624a417ecd9151a7ff64f60730a87f9"
            + "445ec6ada2f865d22b696c4188f24c0df37b75510b939f6bc2fe6883e68bc8c722181ba0bdb08c9871b50c9a16b46eea"
            + "8eed20ba4b8a14e0fcf1468dbbef05a93e5a9d7dcf06898635c1fd99f5de287a80c594daaab19b6279fbd75070cb52eb"
            + "2555c456e39e7839853874e2f0013ac0b64742d8ca10e4b79c955de8b843ce4ed464a6a72ed508091c92e7b3eedd1e1d"
            + "d12afac321618fa3d65f9167c982f43d66a53c6a1997585b346dbf9672ff37cdbc073b2dae11453304df00815476b2a1"
            + "3fccdb30b957a8acafe127400248591238922563e36406d37a24100b7902a028044e218453c2a399199c9d13dc40c55b"
            + "a67e502273850ebdc1a2e2188b8f2d9e3cb909b8c46a2a9f4a6dd93007eafa65b7b3a881aa908cc74f0ad76bbbb58617"
            + "c37012ca05a469fd44e62eecf1e4272ce860c0ae5eadf99b20d8f0234174006f3f110d7657d01645d4eff8f71471cbfe"
            + "1cb2f52648727bf3c9de55153e031b4d61757db63dbf7782eeafe9018a8891362fb14759ac32bab03b349aeb5a377c51"
            + "56be4c8dfbe13a684bda39cf836697c894545fe0f2491acea9fc6252f4ff1e0fe72bd2df6e67a7f635e5bcc6d1a5dd95"
            + "1d89960887a18eb40c785cdb1f80ed42299333ab98cdd67f316cd54346585dccd759fb722789b08807cc0fda293f7e5e"
            + "1819bb0353517020b31d3d00374952bea2c421e862040a9ec892a05b266b7a0d0cc316f483b81af39715657ffe32ad8d"
            + "e0c13077646035236ee5363e5f900e9a7cf850a81cd9960b6df73317226f99eaa6fff15cec34acb98246639b918ab285"
            + "1466a7cdfc4ad1b52c2ae95a58d3734e47ddd5b7560193f644ef9c408b7dee39b4d6aabf4de761e3252d4c7ba3843187"
            + "745d55e6a5db05d42b45caed43c0c5d0b64bfa9d94a4a9d8f2f0e1a108dcfd288fd2bc1fc7bd675412138169cb092498"
            + "75ba3b68cfb11180afae104fc9789571c6dfce02413886796ae21e3c488cc21b9f423a066c2eabf98e2fdeeb5776e4f5"
            + "d09a515ba58cbbab3e37b09fd4f6d9244df80512e84cd7956be301cf64f1429d58577cc241f3a2c655144f81aaf95a4e"
            + "108da894b38970561ae9002619fbbac91fa922ed202bfc62cb08e0de2a7f3631435f8b47c8a3c563b4db4071ce74c46d"
            + "a6fa696a9e282d1e825d663b93bfd56c48dce1884bb10fccea063929f746443fd830a4232e53da21a1f2d1bdfebc3a02"
            + "c10a1b8ad31d04fd616590d272ac737e98eb33af60528716bee78ec0ee8699031177277d9cff32542c67caf5f4459168"
            + "1335ae0e178f3409a7803db83892dde5b9b7f0c750e24aef077badd678a09b850b84ec1597e4963cb5e675df1c0d6fb6"
            + "18496ecd76c35c798325590c2fb27a5e");

    /** {@code V5Constants.sbox}：4 段 × 256 字节 = 1024 字节（上面按 96 字符/行拼接）。 */
    private static final int SBOX_BYTES = 1024;

    /** v5 的 medusa 生产者串（字段 8）与版本码（字段 9，= 67503104）。 */
    private static final String MEDUSA_PRODUCER_V5 = "v04.10.03-ml-android";
    private static final int MEDUSA_VERSION_CODE_V5 = 0x040a0300;

    /** 一次票号搜索的上界（SIGNING_V5.md：search is bounded at 1024 tickets）。 */
    private static final int TICKET_SEARCH_LIMIT = 1024;

    static {
        if (V5_SBOX.length != SBOX_BYTES) {
            throw new IllegalStateException("v5 sbox 长度错误：" + V5_SBOX.length);
        }
    }

    // ==================== 签名头组装 ====================

    /**
     * 已签名的 v5 请求（URL 与签名头绑定，发送时不能再改动）。
     *
     * <p>与上游 {@code Signer.Request} 一致：URL 是最终形态（含被调整过的 {@code _rticket}）。
     */
    public static final class Signed {
        public final String url;
        public final Map<String, String> headers;
        public final byte[] body;

        Signed(String url, Map<String, String> headers, byte[] body) {
            this.url = url;
            this.headers = headers;
            this.body = body;
        }
    }

    /**
     * 生成 v5 签名的播放请求。
     *
     * <p>票号从 {@code ticketStart} 起逐个试，直到 {@code (iv & 15) == 12}（分支 0）；搜索上限
     * {@value #TICKET_SEARCH_LIMIT} 个，只改动 {@code _rticket}，不动分集号与注册身份。
     * 头集合按上游 {@code signPlayback} 的 v5 分支：只保留 {@code x-khronos / x-helios / x-medusa}
     * 三个 Armor 头（其余由调用方补齐 UA/Accept/Content-Encoding/x-ss-stub）。
     *
     * @param base     端点（不含 query）
     * @param values   业务 query 参数（按插入序编码；不含 {@code _rticket}）
     * @param body     实际发送的 body（gzip 字节）；GET 传 null
     * @param identity 注册身份（device_id/iid/cdid + 版本、机型等）
     * @param ts       秒级 khronos
     * @param ticketStart 票号起点（通常是毫秒时间戳）
     * @param random   注入的随机字节（测试用）；null 用 {@link java.security.SecureRandom}
     */
    /**
     * video_tab 的无正文 v5 GET 签名（2026-11-20 起唯一可用的取源形态）。
     *
     * <p>与 {@link #signPlayback} 的区别：body 是空数组，其 digest 必须是 <b>MD5(空)</b>
     * 而不是 16 个零字节（后者是 body 为 null 时的旧口径，2026-11-20 起会被判空响应）。
     * 只产出 x-khronos / x-helios / x-medusa 三个头，调用方不得再加
     * Content-Type / Content-Encoding / x-ss-stub。
     */
    public static Signed signPlaybackGet(String base, Map<String, String> values,
            Identity identity, long ts, long ticketStart, RandomSource random) {
        if (base == null || values == null || identity == null) {
            throw new IllegalArgumentException("v5 播放签名参数无效");
        }
        RandomSource rnd = random == null ? new SecureRandomSource() : random;
        byte[] empty = new byte[0];
        byte[] bodyMd5 = PureSign.md5(empty);
        String query = null;
        for (int offset = 0; offset < TICKET_SEARCH_LIMIT; offset++) {
            long ticket = ticketStart + offset;
            String candidate = encode(values, ticket);
            byte[] querySm3 = PureSign.sm3(candidate.getBytes(StandardCharsets.UTF_8));
            if (!branchAccepts(querySm3, bodyMd5, (int) ts)) continue;
            query = candidate;
            break;
        }
        if (query == null) throw new IllegalStateException("无法生成播放签名");
        String url = base + "?" + query;
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("x-khronos", String.valueOf(ts));
        headers.put("x-helios", PureSign.helios(ts, rnd.nextInt(), LICENSE));
        headers.put("x-medusa", medusaV5(url, empty, (int) ts, identity, rnd));
        return new Signed(url, headers, empty);
    }

    /** video_tab 的 query：注册身份 + 分集号 + 取源档位参数（21 字段，照抄 v5 video_tab）。 */
    public static Map<String, String> videoTabQuery(String vid, String deviceId, String iid, String cdid) {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("aid", "8662");
        q.put("app_name", "novelread");
        q.put("version_code", "74332");
        q.put("version_name", "7.4.3.32");
        q.put("channel", "vivo_8662_64");
        q.put("device_platform", "android");
        q.put("device_type", "PGT-AN10");
        q.put("os_version", "12");
        q.put("update_version_code", "74332");
        q.put("iid", iid);
        q.put("cdid", cdid);
        q.put("device_id", deviceId);
        q.put("video_id", vid);
        q.put("video_id_type", "0");
        q.put("device_level", "1");
        q.put("need_all_video_definition", "1");
        q.put("need_mp4_align", "0");
        q.put("use_os_player", "0");
        q.put("video_platform", "1024");
        q.put("source", "0");
        q.put("from_video_id", "");
        return q;
    }

    public static Signed signPlayback(String base, Map<String, String> values, byte[] body,
            Identity identity, long ts, long ticketStart, RandomSource random) {
        if (base == null || values == null || identity == null) {
            throw new IllegalArgumentException("v5 播放签名参数无效");
        }
        RandomSource rnd = random == null ? new SecureRandomSource() : random;
        String query = null;
        // body 的 md5 与票号无关，只算一次；query 的 sm3 必须每轮重算（票号在 query 里）。
        byte[] bodyMd5 = body != null && body.length > 0 ? PureSign.md5(body) : new byte[16];
        for (int offset = 0; offset < TICKET_SEARCH_LIMIT; offset++) {
            long ticket = ticketStart + offset;
            // encode() 返回的就是 query 原文（不含 '?'），不能过 PureSign.queryOf ——
            // 那个方法按 '?' 切分，纯 query 会被切成空串，导致 1024 轮全部判为「无匹配」。
            String candidate = encode(values, ticket);
            byte[] querySm3 = PureSign.sm3(candidate.getBytes(StandardCharsets.UTF_8));
            if (!branchAccepts(querySm3, bodyMd5, (int) ts)) continue;
            query = candidate;
            break;
        }
        if (query == null) throw new IllegalStateException("无法生成播放签名");

        String url = base + "?" + query;
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("x-khronos", String.valueOf(ts));
        headers.put("x-helios", PureSign.helios(ts, rnd.nextInt(), LICENSE));
        headers.put("x-medusa", medusaV5(url, body, (int) ts, identity, rnd));
        return new Signed(url, headers, body);
    }

    /** 上游 {@code Signer.branch} 的 v5 判定：只有 {@code (iv & 15) == 12} 才是分支 0。 */
    public static boolean branchAccepts(byte[] querySm3, byte[] bodyMd5, int ts) {
        return (ivOf(querySm3, bodyMd5, ts) & 15) == 12;
    }

    /** {@code getIv(getIv(getIv(0x20230928, query), body), le32(ts))}。 */
    static int ivOf(byte[] querySm3, byte[] bodyMd5, int ts) {
        int iv = PureSign.getIv(0x20230928, querySm3);
        iv = PureSign.getIv(iv, bodyMd5);
        return PureSign.getIv(iv, PureSign.le32(ts));
    }

    // ==================== medusa v5 ====================

    /**
     * 构造 v5 的 {@code x-medusa}。
     *
     * <p>明文消息（字段序与上游一致）：magic / 版本 / nonce / aid / device_id / license /
     * version_name / 生产者串 / 版本码 / flags(8B) / ts / f13(20B) / query 前 6 字节 /
     * 计数块 / ts / "none" / 377 / env / 设备能力 JSON。
     * 之后 sm3(signKey||le32(nonce)||signKey) 派生 hash 与 4 字节种子，xmxor -> 逆序 -> 逐字节异或，
     * 装进 {@code 0xca || le32(rand) || le32(check) || payload || nonce 高位} 的信封，
     * 用 {@code AesV3(md5(key[0:16]), ts, v5 = true)} 与 {@code md5(key[16:32])} 做 IV 加密。
     */
    static String medusaV5(String url, byte[] body, int ts, Identity identity, RandomSource random) {
        byte[] query = PureSign.sm3(PureSign.queryOf(url).getBytes(StandardCharsets.UTF_8));
        byte[] bodyMd5 = body != null && body.length > 0 ? PureSign.md5(body) : new byte[16];
        byte[] f13 = PureSign.hashF13(query, bodyMd5, PureSign.le32(ts), ts, true);

        byte[] flags = new byte[8];
        String version = identity.versionName;
        byte[] deviceInfo = PureSign.proto(new Object[][]{
                {1, 1, "sint"}, {2, 2, "sint"}, {3, "8662"}, {4, identity.deviceId},
                {5, identity.processNonce}, {6, "!noperm!"}, {12, "Asia/Shanghai,8"},
                {13, "zh_CN"}, {22, identity.osVersion}, {25, identity.processStarted, "sint"},
                {30, identity.deviceType}, {31, "HONOR"},
                {32, identity.deviceType}, {34, "HONOR"}});
        byte[] env = PureSign.proto(new Object[][]{
                {6, "v04.10.03.04-bugfix"}, {8, identity.channel},
                {12, deviceInfo, "message"}, {14, version}});
        byte[] message = PureSign.proto(new Object[][]{
                {1, MAGIC, "bytes"}, {2, 5, "sint"}, {3, unsigned(random.nextInt()), "sint"},
                {4, "8662"}, {5, identity.deviceId}, {6, LICENSE},
                {7, version}, {8, MEDUSA_PRODUCER_V5}, {9, MEDUSA_VERSION_CODE_V5, "sint"},
                {10, flags, "bytes"}, {12, ts, "sint"}, {13, f13, "bytes"},
                {14, PureSign.slice(query, 0, 6), "bytes"},
                {15, PureSign.proto(new Object[][]{
                        {1, 1, "sint"}, {2, 1, "sint"}, {3, 1, "sint"}, {5, 1, "sint"}}), "message"},
                {17, ts, "sint"}, {20, "none"}, {21, 377, "sint"}, {23, env, "message"},
                {24, "{\"cmr\":16777216,\"cmr2\":16777216,\"un_h\":11,\"vpn\":0,\"kd\":0,"
                        + "\"dyn\":\"\",\"do\":1,\"tk\":true}"}});

        int nonce = random.nextInt();
        byte[] key = SIGN_KEY;
        byte[] hash = PureSign.sm3(PureSign.concat(key, PureSign.le32(nonce), key));
        // 上游 Signer.medusaV5：high = (nonce >>> 16) & 255，
        // seed = le32(~(((high << 11) | (nonce >>> 24)) ^ (high >>> 5) ^ high))。
        int high = (nonce >>> 16) & 255;
        int seedValue = ((high << 11) | (nonce >>> 24)) ^ (high >>> 5) ^ high;
        byte[] seed = PureSign.le32(~seedValue);

        byte[] transformed = PureSign.concat(flags, PureSign.xmxor(message, hash));
        reverse(transformed);
        for (int i = 0; i < transformed.length; i++) {
            transformed[i] ^= seed[(~i) & 3];
        }
        int check = 0x18000001
                | ((query[0] & 63) << 14)
                | ((f13[0] & 63) << 8);
        byte[] packed = PureSign.concat(
                new byte[]{(byte) 0xca},
                PureSign.le32(random.nextInt()),
                PureSign.le32(check),
                transformed,
                new byte[]{(byte) ((nonce >> 16) & 255), (byte) ((nonce >> 24) & 255)});
        byte[] encrypted = new PureSign.AesV3(
                PureSign.md5(PureSign.slice(key, 0, 16)), ts, V5_SBOX)
                .encrypt(packed, PureSign.md5(PureSign.slice(key, 16, 32)));
        byte[] prefix = PureSign.concat(PureSign.le32(5), MAGIC);
        byte[] tsBytes = PureSign.le32(ts);
        for (int i = 0; i < prefix.length; i++) {
            prefix[i] ^= tsBytes[i & 3];
        }
        return PureSign.b64(PureSign.concat(prefix,
                new byte[]{(byte) nonce, (byte) (nonce >> 8), 0, 1}, encrypted));
    }

    private static void reverse(byte[] value) {
        for (int i = 0, j = value.length - 1; i < j; i++, j--) {
            byte t = value[i];
            value[i] = value[j];
            value[j] = t;
        }
    }

    private static long unsigned(int value) {
        return value & 0xFFFFFFFFL;
    }

    // ==================== 编码 ====================

    /**
     * query 编码：与上游 {@code URLEncoder} 后补转 {@code +}/{@code %7E} 的规则一致，末尾补 {@code _rticket}。
     *
     * <p>与上游 {@code values + ("_rticket" to ticket)} 对齐：Map 相加是「覆盖同名键」，
     * 所以入参里已有的 {@code _rticket} 会被丢弃，只用本次搜索到的票号 —— 否则 query 会出现
     * 两个同名参数，签名与发出去的 URL 就不同了。
     */
    static String encode(Map<String, String> values, long ticket) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> e : values.entrySet()) {
            if (e.getKey() == null || "_rticket".equals(e.getKey())) continue;
            if (out.length() > 0) out.append('&');
            out.append(encodePart(e.getKey())).append('=').append(encodePart(e.getValue()));
        }
        if (out.length() > 0) out.append('&');
        return out.append("_rticket=").append(ticket).toString();
    }

    /**
     * 单个键值编码。上游 {@code URLEncoder.encode(v,"UTF-8")} 会把空格编成 {@code +}、
     * 把 {@code ~} 编成 {@code %7E}；{@code Signer.encode} 再补回 {@code %20} 与 {@code ~}，
     * 并保留 {@code *}（queryEncode 的作用）。此处一次性对齐最终字节。
     */
    static String encodePart(String raw) {
        StringBuilder out = new StringBuilder();
        for (byte b : raw.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            boolean keep = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '*' || c == '~';
            if (keep) out.append((char) c);
            else out.append('%').append(String.format("%02X", c));
        }
        return out.toString();
    }

    // ==================== 可注入的随机源 ====================

    /** 随机源（测试注入固定序列，生产用 {@link SecureRandomSource}）。 */
    public interface RandomSource {
        int nextInt();
    }

    /** 默认实现：与 {@code PureSign} 同源的 SecureRandom。 */
    public static final class SecureRandomSource implements RandomSource {
        @Override public int nextInt() {
            return PureSign.RND.nextInt();
        }
    }

    // ==================== 身份 ====================

    /** 进入 v5 medusa 明文的注册身份与设备画像（服务端会核对 device_id 与 query 是否一致）。 */
    public static final class Identity {
        public final String deviceId;
        public final String versionName;
        public final String channel;
        public final String osVersion;
        public final String deviceType;
        public final String processNonce;
        public final long processStarted;

        public Identity(String deviceId, String versionName, String channel, String osVersion,
                String deviceType, String processNonce, long processStarted) {
            this.deviceId = deviceId == null ? "" : deviceId;
            this.versionName = versionName == null ? "" : versionName;
            this.channel = channel == null ? "" : channel;
            this.osVersion = osVersion == null ? "" : osVersion;
            this.deviceType = deviceType == null ? "" : deviceType;
            this.processNonce = processNonce == null ? "" : processNonce;
            this.processStarted = processStarted;
        }

        @Override public String toString() {
            return "V5Sign.Identity([redacted])";
        }
    }

    /**
     * 进程级 nonce（18 随机字节的 base64url 无填充）。
     *
     * <p>上游 {@code Signer} 的 {@code processNonce} 是构造参数默认值里的一次性随机值 ——
     * 也就是「每次冷启动生成一个，进程内所有请求复用」。这里同样做成类加载时仅生成一次，
     * 而不是每次调用都换（换 nonce 会让同一进程的请求看起来来自不同客户端）。
     */
    private static final String PROCESS_NONCE = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(PureSign.randomBytes(18));

    /** 进程级 nonce 的只读入口。 */
    public static String processNonce() {
        return PROCESS_NONCE;
    }

    /** 进程启动时间（毫秒），medusa 明文里四处引用。 */
    public static long processStarted() {
        return PureSign.PROCESS_STARTED;
    }
}