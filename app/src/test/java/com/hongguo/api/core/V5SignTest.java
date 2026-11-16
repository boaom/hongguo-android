// SPDX-License-Identifier: GPL-3.0-only
package com.hongguo.api.core;

import com.hongguo.api.util.Crypto;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * v5 播放签名（{@code v04.10.03}）的离线回归。
 *
 * <p>向量来自 N3urda/hongguoTV {@code 18c37c2} 的 {@code kotlin-tv/core/src/test/resources}：
 * {@code signing-v5-f13.json}（32 个时间戳轮换类，由独立 SDK 计算）与 {@code signing-v5-carrier.json}
 * （4 个时间戳变体的 AES 载波）。二者都是合成输入，不含任何真实会话数据。
 *
 * <p>本测试把同样的向量过一遍仓库自己的 Java 实现（而不是 Python 镜像），因此 CI 能直接守住
 * {@code PureSign.hashF13(..., v5 = true)}、{@code PureSign.AesV3(..., v5 盒)}、
 * {@code V5Sign.medusaV5}、{@code PureSign.helios(..., license)} 与票号搜索的行为。
 */
public class V5SignTest {

    @Test public void hashF13V5MatchesAllTimestampRotationVectors() {
        int checked = 0;
        for (String row : F13_VECTORS) {
            String[] p = row.trim().split(" ");
            byte[] query = Crypto.unhex(p[0]);
            byte[] body = Crypto.unhex(p[1]);
            int ts = Integer.parseInt(p[2]);
            byte[] digest = PureSign.hashF13(query, body, PureSign.le32(ts), ts, true);
            assertEquals("20 字节输出", 20, digest.length);
            assertEquals("时间戳类 " + ts, p[3], Crypto.hex(java.util.Arrays.copyOfRange(digest, 0, 16)));
            // v5 的校验和必须置 bit 5（上游 SignerV5Test: 0x24 == result[16] & 0x24）。
            assertEquals("校验和置 bit 5", 0x24, digest[16] & 0x24);
            checked++;
        }
        assertEquals("32 个时间戳类", 32, checked);
    }

    @Test public void aesV5CarrierMatchesAllFourTimestampVariants() {
        int checked = 0;
        for (String row : CARRIER_VECTORS) {
            String[] p = row.trim().split(" ");
            byte[] source = Crypto.unhex(p[0]);
            byte[] key = Crypto.unhex(p[1]);
            byte[] iv = Crypto.unhex(p[2]);
            int ts = Integer.parseInt(p[3]);
            byte[] encrypted = new PureSign.AesV3(key, ts, V5Sign.V5_SBOX).encrypt(source, iv);
            assertEquals("时间戳变体 " + ts, p[4], Crypto.hex(encrypted));
            checked++;
        }
        assertEquals("4 个时间戳变体", 4, checked);
    }

    /**
     * v3 路径不得因为 v5 参数化而改变。
     *
     * <p>这里用 v5 向量里每个 (query, body, ts) 跑 {@code v5 = false}：
     * <ul>
     *   <li>{@code (iv & 15) == 12} 时 v3 的 {@code ivV0 = (12*171)>>9 = 4}、分支 {@code 12 - 12 = 0}
     *       —— 与 v5 走同一条主路径，但种子取 {@code ivV1List[4] = 0xf5a7adf2}、状态表取第 5 行、
     *       校验和是 {@code | 4}；</li>
     *   <li>其余 31 个时间戳类在 v3 下会落到别的分支，其中分支 1 直接走 20 字节兜底；
     *       无论哪条，都不得抛异常。</li>
     * </ul>
     * 因此断言：v3 对全部 32 个向量都能产出 20 字节，且与 v5 的输出不同（种子/状态表/校验和都不同）。
     */
    @Test public void v3HashPathIsUnchangedByParameterisation() {
        int compared = 0;
        for (String row : F13_VECTORS) {
            String[] p = row.trim().split(" ");
            byte[] query = Crypto.unhex(p[0]);
            byte[] body = Crypto.unhex(p[1]);
            int ts = Integer.parseInt(p[2]);
            byte[] v3 = PureSign.hashF13(query, body, PureSign.le32(ts), ts, false);
            byte[] v5 = PureSign.hashF13(query, body, PureSign.le32(ts), ts, true);
            assertEquals("v3 输出仍是 20 字节", 20, v3.length);
            assertEquals("v5 输出仍是 20 字节", 20, v5.length);
            assertNotEquals("v3 与 v5 的摘要不应相同", Crypto.hex(v3), Crypto.hex(v5));
            // v3 的校验和是 sumMd5 | 4：bit 2 恒置位；v5 是 sumMd5 | 0x20：bit 5 恒置位。
            assertEquals("v3 校验和置 bit 2", 4, v3[16] & 4);
            assertEquals("v5 校验和置 bit 5", 0x20, v5[16] & 0x20);
            compared++;
        }
        assertEquals(32, compared);
    }

    /** v5 对非 12 分支必须显式拒绝（上游 {@code require(...)} 的行为）。 */
    @Test public void v5RejectsNonBranch12Input() {
        byte[] query = C.queryOf("aid=8662&device_id=123&_rticket=1800000000000");
        byte[] body = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
        int ts = 1800000000;
        if (V5Sign.branchAccepts(query, PureSign.md5(body), ts)) {
            return;  // 这个合成输入恰好落在分支 0，跳过（不值得为它构造特例）
        }
        try {
            PureSign.hashF13(query, PureSign.md5(body), PureSign.le32(ts), ts, true);
            fail("v5 对非 12 分支应抛错");
        } catch (IllegalStateException expected) {
            assertEquals("Unsupported hash branch", expected.getMessage());
        }
    }

    @Test public void signPlaybackOnlyMovesTicketAndBindsBodyAndIdentity() {
        String base = "https://api5-normal-sinfonlinea.fqnovel.com/novel/player/multi_video_model/player/v1";
        byte[] body = new byte[]{31, (byte) 139, 8, 0, 1, 2, 3, 4};
        long ts = 1800000000L;
        Map<String, String> values = new LinkedHashMap<>();
        values.put("aid", "8662");
        values.put("app_name", "novelread");
        values.put("version_code", "74332");
        values.put("version_name", "7.4.3.32");
        values.put("channel", "vivo_8662_64");
        values.put("device_platform", "android");
        values.put("device_type", "PGT-AN10");
        values.put("os_version", "12");
        values.put("update_version_code", "74332");
        values.put("iid", "1905892595382586");
        values.put("cdid", "synthetic-cdid");
        values.put("device_id", "7432109876543210987");
        V5Sign.Identity identity = new V5Sign.Identity("7432109876543210987", "7.4.3.32",
                "vivo_8662_64", "12", "PGT-AN10", "c3ludGhldGlj", 1800000000000L);

        V5Sign.Signed first = V5Sign.signPlayback(base, values, body, identity, ts,
                1800000000000L, new FixedRandom());
        String query = first.url.substring(first.url.indexOf('?') + 1);
        assertTrue("query 以 13 个业务字段开头", query.startsWith("aid=8662&app_name=novelread&"));
        assertEquals("只出现一次 _rticket", 1, count(query, "_rticket="));
        assertFalse("v5 不带 ts 参数", query.contains("&ts="));
        assertTrue("x-ss-stub 之外只留三个 v5 Armor 头",
                first.headers.keySet().containsAll(java.util.Arrays.asList("x-khronos", "x-helios", "x-medusa")));
        assertEquals("v5 只保留三个签名头", 3, first.headers.size());
        assertEquals(String.valueOf(ts), first.headers.get("x-khronos"));

        // 分支判定：票号必须落在 (iv & 15) == 12
        byte[] querySm3 = PureSign.sm3(PureSign.queryOf(first.url).getBytes(StandardCharsets.UTF_8));
        assertTrue("票号已调到分支 0", V5Sign.branchAccepts(querySm3, PureSign.md5(body), (int) ts));

        // 只动 _rticket：其他字段逐字比对
        Map<String, String> other = new LinkedHashMap<>(values);
        for (Map.Entry<String, String> e : other.entrySet()) {
            assertTrue(e.getKey(), query.contains(V5Sign.encodePart(e.getKey())
                    + "=" + V5Sign.encodePart(e.getValue())));
        }

        // 身份绑定：换 device_id 必须换 medusa
        V5Sign.Identity otherId = new V5Sign.Identity("7432109876543210999", "7.4.3.32",
                "vivo_8662_64", "12", "PGT-AN10", "c3ludGhldGlj", 1800000000000L);
        V5Sign.Signed second = V5Sign.signPlayback(base, values, body, otherId, ts,
                1800000000000L, new FixedRandom());
        assertNotEquals("device_id 进 medusa 明文", first.headers.get("x-medusa"), second.headers.get("x-medusa"));
    }

    @Test public void medusaV5EnvelopeHasV5PrefixAndNonceTail() {
        // medusa 内部要跑 hashF13(v5)，所以 URL 的票号必须先落到 (iv & 15) == 12 ——
        // 和 signPlayback 一样在 1024 个候选里搜，不能写死一个票号。
        byte[] body = new byte[]{31, (byte) 139, 8, 0, 1, 2, 3, 4};
        int ts = 1800000000;
        byte[] bodyMd5 = PureSign.md5(body);
        String url = null;
        for (int offset = 0; offset < 1024 && url == null; offset++) {
            String candidate = "https://api5-normal-sinfonlinea.fqnovel.com"
                    + "/novel/player/multi_video_model/player/v1"
                    + "?aid=8662&device_id=7432109876543210987&_rticket=" + (1800000000000L + offset);
            byte[] q = PureSign.sm3(PureSign.queryOf(candidate).getBytes(StandardCharsets.UTF_8));
            if (V5Sign.branchAccepts(q, bodyMd5, ts)) url = candidate;
        }
        assertNotNull("1024 个票号内应能找到分支 0", url);
        V5Sign.Identity identity = new V5Sign.Identity("7432109876543210987", "7.4.3.32",
                "vivo_8662_64", "12", "PGT-AN10", "c3ludGhldGlj", 1800000000000L);
        byte[] raw = Base64.getDecoder().decode(V5Sign.medusaV5(url, body, ts, identity, new FixedRandom()));
        byte[] tsBytes = PureSign.le32(ts);
        // prefix = le32(5) || magic，再逐字节 XOR le32(ts)
        byte[] expected = PureSign.concat(PureSign.le32(5), V5Sign.MAGIC);
        for (int i = 0; i < expected.length; i++) expected[i] ^= tsBytes[i & 3];
        assertEquals(V5Sign.MAGIC.length + 4, expected.length);
        assertEquals(Crypto.hex(expected), Crypto.hex(java.util.Arrays.copyOfRange(raw, 0, expected.length)));
        // prefix 之后紧跟 4 字节 nonce 标记：nonce 低字节、高字节、0x00、0x01（上游 Signer.medusaV5）。
        int base = V5Sign.MAGIC.length + 4;
        assertEquals(0, raw[base + 2] & 0xFF);
        assertEquals(1, raw[base + 3] & 0xFF);
        // 标记之后是 AesV3 的输出：[key 末字节] + 与 packed 等长的密文（packed 必然 ≥ 248 字节）。
        assertTrue("信封加 AES 密文应超过 248 字节", raw.length > base + 4 + 249);
    }

    @Test public void heliosUsesV5License() {
        byte[] withV5 = Base64.getDecoder().decode(PureSign.helios(1800000000L, 0x11223344, V5Sign.LICENSE));
        byte[] withV3 = Base64.getDecoder().decode(PureSign.helios(1800000000L, 0x11223344, PureSign.HELIOS_LICENSE_V3));
        assertEquals("前置 random 4 字节", "44332211", Crypto.hex(java.util.Arrays.copyOfRange(withV5, 0, 4)));
        assertNotEquals("license 段必须不同", Crypto.hex(withV5), Crypto.hex(withV3));
        assertEquals("长度一致", withV3.length, withV5.length);
    }

    /** 固定随机源：让 medusa / helios 的输出可重复（只用于测试）。 */
    static final class FixedRandom implements V5Sign.RandomSource {
        private final List<Integer> values = new ArrayList<>();
        FixedRandom() {
            values.add(0x11223344);
            values.add(0xa1b2c3d4);
            values.add(0x55667788);
        }
        private int at = 0;
        @Override public int nextInt() {
            int value = values.get(at % values.size());
            at++;
            return value;
        }
    }

    private static int count(String text, String part) {
        int n = 0;
        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + 1)) n++;
        return n;
    }

    /** 供 v3 回归用例构造 query 的 sm3（复用 PureSign 的同一实现）。 */
    private static final class C {
        static byte[] queryOf(String query) { return PureSign.sm3(query.getBytes(StandardCharsets.UTF_8)); }
    }

    private static final String[] F13_VECTORS = {
            "97f67a81cea547c71c76702cc529330762c14971d85f3c335467579d1e9c15e3 0ff85a33ee8c8aa4f59a843d5199909f 1800000000 ca03ec7a0c2444b3dcccf33453d02579",
            "959b0479339d92d4ab2c428cff3daa0349a78072e56115d2fd8181ef00ef722d 49783eb0095375c17655cdc1ff329874 1800000001 a1376d7977abbc1dbc536aae0429ac2c",
            "37c02962735148636add7f1430394e36013faedcd015f1c5519773153b0efc81 c9ee9a33be57c4125ee742ee67f68cff 1800000002 acadd6dc830f1d3249c77c86d1a03ab7",
            "1ec50c23a9d19b673a4b7037cc51f20b594f04d37234da13b52321f5fd1c7a18 fcd833cf4fa23db062ea04a99de1f6f1 1800000003 7f18dac9add0b303bc6fdd4bfd181b39",
            "b11761b148eaadce909a31b3c0c4e9dc7a6db19d89e4d036bf9948d4f7d2cd47 302e1e011fcde596282923682a4f74ca 1800000004 ef2475d793d3228e1bb40c2d5258b32f",
            "925d3850c921a6cb9174d581243cc0c4150e51bdd144205b462acbd2cd46e5db 7dd8c5bccd64ae6e1c58ade2c929ac41 1800000005 dc1da7cf8c897dffc390d0997c58f916",
            "69d9f1d85c7a7421e14153fa37535bc0d04a0dc755ad1e13e0a65ab2a96fbbba 34b3025ffca5e08743b49f6ce3b6abf8 1800000006 d45e587fbbd4798ceab2623b5237f104",
            "c7c03633bfff0551b4975ea7b31929f5d1fa250ba492d472a5a511b3e5b4c45c 92df202c08c24d635f0154095848657a 1800000007 bdb494221bc010227df67b21ad814938",
            "c0f19b22831d1561f17b85e4b58dc90b2d274bcf1ec00fe24f270f4bcacf9602 7418334aa5d188fc874f67deea9a21d7 1800000008 fedb0bbc2c073944c7378d6e94eafce8",
            "e60f9c10bb6e74959462742e18470865a9d5a0e693504edc6e7c47155d54c898 e95c11c6ebf1b2019000b5d2b6efb2b9 1800000009 24bdcc955e3f0cab49aba64f91712c98",
            "9f44488778e4730fb6495e32faaea2bf97ee6be47e7a7ce73c5ce92f34b7f2d9 1f85408ee383baa76345e77f16a08293 1800000010 fccc11f6138080539b1548d0b9055bd3",
            "76bb13d24db94ecede1be7ab5047d442e279ebaedbec12a6002ff1736320eb70 6befdc0280d0eb9b00864f3b10fd6e9f 1800000011 8b85e9d9dda0b53ab7bed85d7a0546f0",
            "f08e4771e3ec68f66503a4138ba295646ff135891793aa2c48703fbbe83f959a 7fe849ed9d3bc1057fb8e7d05d42d50a 1800000012 abf54872e5b0589a490ad8f77fc80144",
            "22caa8612064c469bb6f3038b92d46c81660dbbf76e0bb1c1ea06d7dcf35df1c fc1361b74ef50e9a8f1637e1fca4d838 1800000013 7a22986242c909f50fae3460cacd6775",
            "7fea02e2070957c54a1352e67e905f56b95a9d9ff7bba694a8bad7f0f2c910ed 48477c26e58747dabfc0b2290185eef2 1800000014 c279b885497eeadf9ac22d4fb7a34b34",
            "ddf456d095ef231ae9a0332848c86372d34079e8e784bfa319f0c84703daf67d e93ce6e2ce1bcdaeb93bed8463fbb5b5 1800000015 3e5d88f088e9e56a668fb0020d9a1832",
            "0ab8312b0ab537ab8614b73c84d1e651b91a383ff96524b0850526dfea8e2edf e37a63574ca36affff4b9bab95d76fce 1800000016 e47ce104e1708f47b58c35e62d757e34",
            "3804c6a08ddf104c3775f2383e7a31ad1246464d3ab402ec1169ddbfc7aaf354 78eecf206f357a9caca88b91ef134743 1800000017 30d0ff8ad17ea5bc87fe24f6a03d2111",
            "f89abdaca254a6f22a01ccd23a9a3408c3c62f1ce67428e1e14fe2ecd7513df1 c105211c82a27ccd4ca00daccd7b8710 1800000018 f1eb59c415f6edc215d0a8ab50ec88d8",
            "a239a3356cc751cddcaee6e06a3d15e1caaae43f2f85a261f14945934ab54c4e 526fda00a40beaabeeb8c150f71cc210 1800000019 8645f854bdaf96d4bf385432594d5a1d",
            "50d51b627a3e67f00fd556534dc3d155ae8402198a8e6a734edc7bddabc6a4f1 4678211f3a092c6af8e75cca1afc31ca 1800000020 fb3e103857894dc97a4dde6864f084cc",
            "b78cc5d1b14cee008085e0e0f3a8b5be7d3dbca0cebc37f4b5ffd574a9df00c6 5fbf4ba47b8610fa654b96ca2818ba91 1800000021 5701b2a57c5d497f7431cc610624b5f0",
            "adf6b9936576b9708ae1a393182a1ef0fede156ffd41c69b9ecf9d3ce2263aa4 9dbfb4aa682f1cc4e0efe2896c603fe4 1800000022 ae03abe442d4d601bc2135ee4529b72d",
            "c00beda338b6c034ee147173ab78f678bc56eed8f2aa9c1cb7bac157b87e36f5 fd5139f800ac11ada82a542131551bac 1800000023 90a1698d9022aa5a91daf9db53f7ed63",
            "ccc3b48e070a81622e0af3b030faba24be90b07c8d6d92ee64a94c907dec0b90 8f32ebc7f408721e94a14ff6fb31299b 1800000024 8400797d526a9e55ad99dcbaa5d0be13",
            "fea6f6ff01087847c4eb2f2c6e4424c6a7b92aec5d624a450529f9321fa03850 cdcf940a7d471c6db8f2f033ce593da0 1800000025 a486f40927fc2c015fa56b441af15f42",
            "bada73694f51f051031c710f78a7ba3c6c677c634a63198757f8a1940ba6d22f a78390d73256cd71e4830aab685c8a71 1800000026 6c9e4b9fe7c7646e3ffcb6125f7aca76",
            "6d7e4dacc21b0b5d2cd54138f941bc98cff8ee59f27ac205527b3e3207d19691 094f2d484098b4a46ae820ddfb001bcc 1800000027 9e2433cc7076f16614d3a9f574403723",
            "6b25972f9f1b7e059d1cbfafa2b168a4872f818d2d69987b388da7a9a056dc7c 5918ae7dcca9d95fca221de508800bf8 1800000028 fb9cbb60efdf6e90192fe3bfe8912a03",
            "d0c1ec04b4affb0401da90d6f403eb7a2ea095d7fb2789caa3f24eb97b4e84aa 5ec477ba383495da0215c7abc93fe061 1800000029 28da0f1ae9f4bf3b47af5c8de041fa04",
            "9f1231092eccc944f23ace788382f6582c7539217899b49064e73fe7bb5f2c4b cc13517bcf9e05a656bf58ed06e53790 1800000030 3e820f120bec38f67ed05de1dd02b79e",
            "7452b4261e5ba66aa463bbc4892aa93c11f3f4005b3747c6adabb6359212d2e8 f5b46ee83c8b3c78876cd15e992c3c82 1800000031 26c2a2a9bd5a7f468b3f1f4525c0ba18",
    };

    private static final String[] CARRIER_VECTORS = {
            "13385d82a7ccf1163b6085aacff4193e6388add2f71c41668bb0d5fa1f44698eb3d8fd22476c91b6db00254a6f94b9de03284d7297bce1062b50759abfe4092e53789dc2e70c31567ba0c5ea0f34597ea3c8ed12375c81a6cbf0153a5f84a9cef3183d6287acd1f61b40658aafd4f91e43688db2d7fc21466b90b5daff24496e93b8dd02274c7196bbe0052a4f7499bee3082d52779cc1e60b30557a9fc4e90e33587da2c7ec11365b80a5caef14395e83a8cdf2173c6186abd0f51a3f6489aed3f81d42678cb1d6fb20456a8fb4d9fe23486d92b7dc01264b7095badf04294e7398bde2072c51769bc0e50a2f54799ec3e80d32577ca1c6eb10355a7fa4c9ee13385d82a7ccf1163b6085aacff4193e6388add2f71c41668bb0d5fa1f44698eb3d8fd22476c91b6db00254a 000102030405060708090a0b0c0d0e0f 101112131415161718191a1b1c1d1e1f 1800000000 0653385d92a3ccf3177b608dbacbf41b3f23a8a5c2f79c4367cb90ddea1b44698fb3f8fd3243ec91b79b002d4a6b14bbdf03084d7293bce1066b70759abb640b2f53589dd2e78c33567ba0c5ea0bb45b7fa3e8e51233dc81a78bf01d2a5f04abceb3383572872cd3f65b406d9aab54f91e436885a2d37c21462b90bddaffa4496ed398dd12234c7196bbe00d3a4bf499bfa3282542779cc1e74b105d7a9bc4eb0e337875b2c36c11365ba0adcaef94395fc3a8cde217bc6387abf0fd1a3b648baf93d81552670cb1d6fb004d6a8fb4dbfe63486d92b7dc03264b5095badf84294e33b8bdf203ac51769bc0ed1a2fd4799fc3c80d2253fca3c6eb10355a7fa4c9ee13385d82a7ccf1163b6085aacff4193e6388add2f71c41668bb0d5fa1f44698eb3d8fd22476c91b6db00254a",
            "13385d82a7ccf1163b6085aacff4193e6388add2f71c41668bb0d5fa1f44698eb3d8fd22476c91b6db00254a6f94b9de03284d7297bce1062b50759abfe4092e53789dc2e70c31567ba0c5ea0f34597ea3c8ed12375c81a6cbf0153a5f84a9cef3183d6287acd1f61b40658aafd4f91e43688db2d7fc21466b90b5daff24496e93b8dd02274c7196bbe0052a4f7499bee3082d52779cc1e60b30557a9fc4e90e33587da2c7ec11365b80a5caef14395e83a8cdf2173c6186abd0f51a3f6489aed3f81d42678cb1d6fb20456a8fb4d9fe23486d92b7dc01264b7095badf04294e7398bde2072c51769bc0e50a2f54799ec3e80d32577ca1c6eb10355a7fa4c9ee13385d82a7ccf1163b6085aacff4193e6388add2f71c41668bb0d5fa1f44698eb3d8fd22476c91b6db00254a 000102030405060708090a0b0c0d0e0f 101112131415161718191a1b1c1d1e1f 1800000001 7253185592a7ccf3177b408dbacb74193e63a8a5d2f39c4366cb90d5fa1f44698fb3d8fd32476c91b6db00254a6f94bbde0308457293bce3076b707d9abf64092f137895d2e30c33567b80c5fa0f34597fe3e8e51237dc83a68bd0152a5b84abcff3183562832cd3f71b606d8aaf54fb1f03688db2d37c21462bb0bdcaff24496fd3b8d502274c7197bbc0052a4f749bbea3282d52771cc1e60b105d7a9fc4eb0e737875a2c76c11375ba0a5caeb14395e8388c5e217bc6186ebd0fd1a3b648baf93f81d52638cb1d7fb00457a8f34d9ff23686d92b7dc01274b509dbadf84294e3398b5f207ac51779bc0e50a2fd4799fc3c80d3257fca1c7eb10355a7fa4c9ee13385d82a7ccf1163b6085aacff4193e6388add2f71c41668bb0d5fa1f44698eb3d8fd22476c91b6db00254a",
            "13385d82a7ccf1163b6085aacff4193e6388add2f71c41668bb0d5fa1f44698eb3d8fd22476c91b6db00254a6f94b9de03284d7297bce1062b50759abfe4092e53789dc2e70c31567ba0c5ea0f34597ea3c8ed12375c81a6cbf0153a5f84a9cef3183d6287acd1f61b40658aafd4f91e43688db2d7fc21466b90b5daff24496e93b8dd02274c7196bbe0052a4f7499bee3082d52779cc1e60b30557a9fc4e90e33587da2c7ec11365b80a5caef14395e83a8cdf2173c6186abd0f51a3f6489aed3f81d42678cb1d6fb20456a8fb4d9fe23486d92b7dc01264b7095badf04294e7398bde2072c51769bc0e50a2f54799ec3e80d32577ca1c6eb10355a7fa4c9ee13385d82a7ccf1163b6085aacff4193e6388add2f71c41668bb0d5fa1f44698eb3d8fd22476c91b6db00254a 000102030405060708090a0b0c0d0e0f 101112131415161718191a1b1c1d1e1f 1800000002 5553185592a3ccf1173b408daacff41b3f6388add2f71c4167cb90ddea1fc46b8ff3f8f52243ec91b6db20255a6b14b9de0328457297bce1072b707d8abfe40b2f13589dd2e38c31567b80c5fa0bb45b7fa3c8ed02335c83a68bf01d3a5f04abcef3383d6287acd1f75b60658aabd4f91e034885a2d7fc21466bb0bddaffa44b6ed3b8d502274c7196fbe00d3a4b749bbee3282d52731cc1e64b30556a9fc4e90f33587da2c36c13365ba0adcaeb94395e83a8cde217bc6386ebd0f50a3fe489ae93f81542638cb1d7bb004d7a8fb4d9fe63486592b3dc03274b7095badb84294f73b8b5e207ac5377dbe0ed0a2bd47b9f83c8052257fca3c7eb10355a7fa4c9ee13385d82a7ccf1163b6085aacff4193e6388add2f71c41668bb0d5fa1f44698eb3d8fd22476c91b6db00254a",
            "13385d82a7ccf1163b6085aacff4193e6388add2f71c41668bb0d5fa1f44698eb3d8fd22476c91b6db00254a6f94b9de03284d7297bce1062b50759abfe4092e53789dc2e70c31567ba0c5ea0f34597ea3c8ed12375c81a6cbf0153a5f84a9cef3183d6287acd1f61b40658aafd4f91e43688db2d7fc21466b90b5daff24496e93b8dd02274c7196bbe0052a4f7499bee3082d52779cc1e60b30557a9fc4e90e33587da2c7ec11365b80a5caef14395e83a8cdf2173c6186abd0f51a3f6489aed3f81d42678cb1d6fb20456a8fb4d9fe23486d92b7dc01264b7095badf04294e7398bde2072c51769bc0e50a2f54799ec3e80d32577ca1c6eb10355a7fa4c9ee13385d82a7ccf1163b6085aacff4193e6388add2f71c41668bb0d5fa1f44698eb3d8fd22476c91b6db00254a 000102030405060708090a0b0c0d0e0f 101112131415161718191a1b1c1d1e1f 1800000003 4b13185d82a34cf3177b608dbacbf41b3e63a8adc2f79c4367cbb0d5fa1fc46b8fb3d8f53243ec93b69b20254a6f14bbdf03084d6293bce3072b70758abf640b2e535895d2e30c31567ba0cdfa0fb45b7fe3e8ed0233dc81a7cbd01d2a5f84abcef338356287acd3f71b406d8aabd4fb1e03688da2d7fc23462bb0bdcaffa44b6e9398d50223cc7196bbc00d3a4f7499bfe3282d42739cc3e64b105d7a9f44e90f735875a2c7ec13375b80a5caef143b5fc3a8c5e217bc6186ebd0f51a3f6489ae93d81552630cb3d7fb20457a8b34d9fe23486d92b7dc03264b509dbadb842b4e73b8b5f207ac5377dbe0ed0a2bd4799e83c80d3253fca1c7eb10355a7fa4c9ee13385d82a7ccf1163b6085aacff4193e6388add2f71c41668bb0d5fa1f44698eb3d8fd22476c91b6db00254a",
    };
}