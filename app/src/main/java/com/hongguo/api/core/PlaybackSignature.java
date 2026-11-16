// SPDX-License-Identifier: GPL-3.0-only
package com.hongguo.api.core;

import com.hongguo.api.util.Crypto;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 播放请求签名（551 Argus + Ladon），1:1 移植自 sunnysky123/hongguo-web
 * {@code server/src/core/playback-sign.js}（commit {@code f662f0b}，2026-11-11，GPL-3.0-only）。
 *
 * <p>与前一代 {@link PlayerSign} 的差别（2026-11-13 实测相关）：Argus 明文里版本字段取 query 的
 * {@code version_name}、SDK 串为 {@code v04.04.05-ov-android}、字段 23 环境块换成
 * {@code NX551J/8196/0x80e0d800}，信封换成 {@code f2f7fcff…}/{@code eafb2cfe…} + {@code f281} 前缀、
 * AES 密钥为固定常量。上游对形态过时的签名同样回 200 + 空正文，因此取流统一走这里。
 *
 * <p>签名绑定「最终 query 原文 + 实际发送的 body（POST 为 gzip 后的字节）」。
 */
public final class PlaybackSignature {
    private PlaybackSignature() {}

    private static final long LICENSE_ID = 1611921764L;
    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    /** Argus Simon 轮密钥源（32 字节 = 4 个 u64 字）。 */
    private static final byte[] SIGN_KEY = Crypto.unhex(
            "fc78e0a9657a0c748ce51559903ccf03510e51d3cff232d71343e88a321c5304");
    private static final byte[] AES_KEY = Crypto.unhex("d31e3718288a1027baab59f146a09a9c");
    private static final byte[] AES_IV = Crypto.unhex("ea180a0336ed352fcd24e4d50018ae54");
    private static final byte[] MAGIC8 = Crypto.unhex("f2f7fcfff2f7fcff");
    private static final byte[] TAIL9 = Crypto.unhex("eafb2cfe8568519154");

    /**
     * 生成播放请求的签名头。
     *
     * @param query 最终 query 原文（不含 '?'，与发出去的字节一致）
     * @param body  实际发送的 body（POST 为 gzip 后的字节；GET 传 null）
     * @param ts    秒级时间戳
     */
    public static Map<String, String> headers(String query, byte[] body, long ts) {
        if (query == null) throw new IllegalArgumentException("播放器签名参数无效");
        String aidText = queryParam(query, "aid");
        long aid = aidText != null && aidText.matches("-?\\d{1,18}") ? Long.parseLong(aidText) : 8662L;
        String deviceId = queryParam(query, "device_id");
        if (deviceId == null) deviceId = "";
        String stub = body != null && body.length > 0 ? Crypto.hex(Crypto.md5(body)) : "";

        byte[] randFieldBytes = new byte[4];
        RANDOM.nextBytes(randFieldBytes);
        int randField = ByteBuffer.wrap(randFieldBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
        byte[] ladonRandom = new byte[4];
        RANDOM.nextBytes(ladonRandom);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("x-ss-req-ticket", String.valueOf(ts * 1000L));
        headers.put("x-khronos", String.valueOf(ts));
        headers.put("x-ladon", ladon(ts, aid, ladonRandom));
        headers.put("x-argus", argus(query, stub, ts, aid, deviceId, "", randField));
        if (!stub.isEmpty()) headers.put("x-ss-stub", stub);
        return headers;
    }

    /** 从 query 原文取参数值；解码失败按原样返回（签名按字节算）。 */
    static String queryParam(String query, String name) {
        if (query == null) return null;
        for (String part : query.split("&")) {
            int at = part.indexOf('=');
            String key = at < 0 ? part : part.substring(0, at);
            if (decode(key).equals(name)) return decode(at < 0 ? "" : part.substring(at + 1));
        }
        return null;
    }

    private static String decode(String value) {
        try {
            return java.net.URLDecoder.decode(value.replace("+", " "), StandardCharsets.UTF_8);
        } catch (Exception error) {
            return value;
        }
    }

    // ==================== protobuf / 摘要 ====================

    private static final class Proto {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        Proto varint(long value) {
            long n = value & 0xFFFFFFFFL;
            while ((n & ~127L) != 0) { out.write((int) (n & 127) | 128); n >>>= 7; }
            out.write((int) n);
            return this;
        }
        Proto tag(int field) { return varint(field * 8L); }
        Proto tagBytes(int field) { return varint(field * 8L + 2); }
        Proto putVarint(int field, long value) { tag(field); return varint(value); }
        Proto putBytes(int field, byte[] value) {
            tagBytes(field); varint(value.length); out.write(value, 0, value.length); return this;
        }
        Proto putUtf8(int field, String value) { return putBytes(field, value.getBytes(StandardCharsets.UTF_8)); }
        Proto putProto(int field, Proto nested) { return putBytes(field, nested.out.toByteArray()); }
        byte[] toBytes() { return out.toByteArray(); }
    }

    /** body 摘要取 sm3(stub 字节) 前 6 字节；无正文时是 16 个零字节的摘要。 */
    static byte[] bodyHash(String stubHex) {
        if (stubHex == null || stubHex.isEmpty()) return head6(Sm3.hash(new byte[16]));
        boolean hex = stubHex.matches("(?i)^(?:[0-9a-f]{2})+$");
        return head6(Sm3.hash(hex ? Crypto.unhex(stubHex) : new byte[0]));
    }

    /** query 摘要取 sm3(query 原文) 前 6 字节。 */
    static byte[] queryHash(String query) {
        return head6(Sm3.hash(query == null || query.isEmpty() ? new byte[16]
                : query.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] head6(byte[] digest) { return Arrays.copyOf(digest, 6); }

    private static byte[] pad16(byte[] data) {
        int n = 16 - data.length % 16;
        byte[] result = Arrays.copyOf(data, data.length + n);
        Arrays.fill(result, data.length, result.length, (byte) n);
        return result;
    }

    private static long u64(long value) { return value; }
    private static long rol(long value, int bits) { return Long.rotateLeft(value, bits); }
    private static long ror(long value, int bits) { return Long.rotateRight(value, bits); }

    private static ByteBuffer little(byte[] data) { return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN); }

    private static byte[] join(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) out.write(part, 0, part.length);
        return out.toByteArray();
    }

    // ==================== Simon（72 轮） ====================

    static long[] simonKeyExpansion(long[] key) {
        long[] expanded = new long[72];
        System.arraycopy(key, 0, expanded, 0, 4);
        for (int i = 4; i < 72; i++) {
            long tmp = ror(expanded[i - 1], 3) ^ expanded[i - 3];
            tmp ^= ror(tmp, 1);
            long bit = (0x3dc94c3a046d678bL >>> ((i - 4) % 62)) & 1L;
            expanded[i] = ~expanded[i - 4] ^ tmp ^ bit ^ 3L;
        }
        return expanded;
    }

    /** 单块加解密：返回 {x, y}。 */
    static long[] simonEncrypt(long left, long right, long[] roundKeys) {
        long x = u64(left), y = u64(right);
        for (long roundKey : roundKeys) {
            long previous = y;
            y = x ^ (rol(y, 1) & rol(y, 8)) ^ rol(y, 2) ^ roundKey;
            x = previous;
        }
        return new long[]{x, y};
    }

    // ==================== 551 Argus ====================

    static String argus(String query, String stubHex, long ts, long aid, String deviceId, String installId, int randField) {
        Proto counters = new Proto().putVarint(1, 1).putVarint(2, 1).putVarint(3, 1).putVarint(7, 0xc792eccaL);
        String versionName = queryParam(query, "version_name");
        Proto bean = new Proto()
                .putVarint(1, 1077940818L).putVarint(2, 2).putVarint(3, randField & 0xFFFFFFFFL)
                .putUtf8(4, String.valueOf(aid == 0 ? 1967L : aid))
                .putUtf8(5, deviceId)
                .putUtf8(6, String.valueOf(LICENSE_ID))
                .putUtf8(7, versionName == null ? "" : versionName)
                .putUtf8(8, "v04.04.05-ov-android")
                .putVarint(9, 134744640L)
                .putBytes(10, new byte[8])
                .putVarint(11, 0)
                .putVarint(12, ts << 1)
                .putBytes(13, bodyHash(stubHex))
                .putBytes(14, queryHash(query))
                .putProto(15, counters)
                .putUtf8(16, installId == null ? "" : installId)
                .putUtf8(20, "none")
                .putVarint(21, 738)
                .putProto(23, new Proto().putUtf8(1, "NX551J").putVarint(2, 8196).putVarint(4, 0x80e0d800L))
                .putVarint(25, 2);

        byte[] protobuf = pad16(bean.toBytes());
        ByteBuffer keyWords = little(SIGN_KEY);
        long[] key = new long[]{keyWords.getLong(0), keyWords.getLong(8), keyWords.getLong(16), keyWords.getLong(24)};
        long[] roundKeys = simonKeyExpansion(key);
        ByteArrayOutputStream blocks = new ByteArrayOutputStream();
        ByteBuffer blockView = little(protobuf);
        for (int at = 0; at < protobuf.length; at += 16) {
            long[] encrypted = simonEncrypt(blockView.getLong(at), blockView.getLong(at + 8), roundKeys);
            blocks.write(join(littleBytes(encrypted[0]), littleBytes(encrypted[1])), 0, 16);
        }
        byte[] framed = join(MAGIC8, blocks.toByteArray(), TAIL9);
        for (int i = 8; i < framed.length; i++) framed[i] ^= MAGIC8[i % 8];
        byte[] reversed = new byte[framed.length];
        for (int i = 0; i < framed.length; i++) reversed[i] = framed[framed.length - 1 - i];
        byte[] padded = pad16(join(reversed, new byte[]{'a', 'o'}));
        try {
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE,
                    new javax.crypto.spec.SecretKeySpec(AES_KEY, "AES"),
                    new javax.crypto.spec.IvParameterSpec(AES_IV));
            byte[] body = cipher.doFinal(padded);
            return java.util.Base64.getEncoder().encodeToString(join(Crypto.unhex("f281"), body));
        } catch (java.security.GeneralSecurityException error) {
            throw new IllegalStateException("无法生成播放器签名", error);
        }
    }

    private static byte[] littleBytes(long value) {
        return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array();
    }

    // ==================== Ladon ====================

    /** md5(随机 4 字节 + aid) 派生 34 轮字表；输出「随机 4 字节 + 密文」的 base64。 */
    static String ladon(long ts, long aid, byte[] randomBytes) {
        if (randomBytes == null || randomBytes.length != 4)
            throw new IllegalArgumentException("Ladon 需要 4 字节随机数");
        String aidText = String.valueOf(aid);
        byte[] digestHex = Crypto.hex(Crypto.md5(join(randomBytes, aidText.getBytes(StandardCharsets.US_ASCII))))
                .getBytes(StandardCharsets.US_ASCII);
        ByteBuffer words = little(digestHex);
        long[] word = new long[]{words.getLong(0), words.getLong(8), words.getLong(16), words.getLong(24)};

        long[] table = new long[35];
        ArrayDeque<Long> queue = new ArrayDeque<>();
        long b0 = word[0];
        long b8 = word[1];
        table[0] = word[0];
        queue.add(word[2]);
        queue.add(word[3]);
        for (int i = 0; i < 34; i++) {
            long x8 = (ror(b8, 8) + b0) ^ i;
            queue.add(x8);
            x8 ^= rol(b0, 3);
            table[i + 1] = x8;
            b0 = x8;
            b8 = queue.poll();
        }

        byte[] input = pad16((ts + "-" + LICENSE_ID + "-" + aidText).getBytes(StandardCharsets.US_ASCII));
        ByteBuffer view = little(input);
        ByteBuffer out = ByteBuffer.allocate(input.length).order(ByteOrder.LITTLE_ENDIAN);
        for (int at = 0; at < input.length; at += 16) {
            long a = view.getLong(at), b = view.getLong(at + 8);
            for (int i = 0; i < 34; i++) {
                b = table[i] ^ (a + ror(b, 8));
                a = b ^ rol(a, 3);
            }
            out.putLong(a); out.putLong(b);
        }
        return java.util.Base64.getEncoder().encodeToString(join(randomBytes, out.array()));
    }
}
