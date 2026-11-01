package us.furcdn.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class FurCdnClientTest {

    private HttpServer server;
    private FurCdnClient client;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicInteger hits = new AtomicInteger();

    @BeforeEach
    void start() throws IOException {
        lastBody.set(null);
        hits.set(0);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        client = new FurCdnClient("http://127.0.0.1:" + server.getAddress().getPort(), "fck_test_secret");
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void respond(String path, int status, String body) {
        server.createContext(path, ex -> {
            String auth = ex.getRequestHeaders().getFirst("Authorization");
            if (!"Bearer fck_test_secret".equals(auth)) {
                ex.sendResponseHeaders(401, 0);
                ex.close();
                return;
            }
            hits.incrementAndGet();
            lastBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] data = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, data.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(data);
            }
        });
    }

    /** 上一次請求的 body，解析成 JSON（沒有 body 時回傳 null） */
    private JsonObject lastJson() {
        String body = lastBody.get();
        return body == null || body.isEmpty() ? null : new Gson().fromJson(body, JsonObject.class);
    }

    @Test
    void listDomains() {
        respond("/api/v1/domains", 200, "{\"domains\":[{\"id\":1,\"name\":\"example.com\",\"enabled\":true}]}");
        List<Domain> domains = client.listDomains();
        assertEquals(1, domains.size());
        assertEquals("example.com", domains.get(0).getName());
        assertTrue(domains.get(0).isEnabled());
    }

    @Test
    void purgeCache() {
        respond("/api/v1/domains/42/purge", 200, "{\"ok\":true,\"mode\":\"all\",\"total\":3,\"success\":3}");
        PurgeResult r = client.purgeCache(42);
        assertTrue(r.isOk());
        assertEquals("all", r.getMode());
        assertEquals(3, r.getTotal());
        assertEquals(3, r.getSuccess());
        assertEquals(0, r.getRemoved());
        // 舊的整站清除不帶 body，既有呼叫端行為不變
        assertEquals("", lastBody.get());
    }

    @Test
    void purgeCacheSelectiveUrls() {
        respond("/api/v1/domains/42/purge", 200,
                "{\"ok\":true,\"mode\":\"selective\",\"total\":5,\"success\":5,\"removed\":12}");

        PurgeResult r = client.purgeCache(42, PurgeOptions.urls("https://example.com/a.css?v=1", "/b.js"));

        assertTrue(r.isOk());
        assertEquals("selective", r.getMode());
        assertEquals(5, r.getTotal());
        assertEquals(12, r.getRemoved());

        JsonObject body = lastJson();
        assertNotNull(body);
        assertFalse(body.has("all"));
        assertEquals("https://example.com/a.css?v=1", body.getAsJsonArray("urls").get(0).getAsString());
        assertEquals("/b.js", body.getAsJsonArray("urls").get(1).getAsString());
        assertFalse(body.has("prefixes"));
        assertFalse(body.has("patterns"));
    }

    @Test
    void purgeCacheSelectivePrefixesAndPatterns() {
        respond("/api/v1/domains/42/purge", 200,
                "{\"ok\":true,\"mode\":\"selective\",\"total\":2,\"success\":2,\"removed\":7}");

        client.purgeCache(42, new PurgeOptions()
                .addPrefix("/static/")
                .addPrefix("https://example.com/img/")
                .addPattern("/assets/*.js"));

        JsonObject body = lastJson();
        assertNotNull(body);
        assertFalse(body.has("all"));
        assertFalse(body.has("urls"));
        assertEquals(2, body.getAsJsonArray("prefixes").size());
        assertEquals("/static/", body.getAsJsonArray("prefixes").get(0).getAsString());
        assertEquals("https://example.com/img/", body.getAsJsonArray("prefixes").get(1).getAsString());
        assertEquals(1, body.getAsJsonArray("patterns").size());
        assertEquals("/assets/*.js", body.getAsJsonArray("patterns").get(0).getAsString());
    }

    @Test
    void purgeCacheSelectivePatternsOnly() {
        respond("/api/v1/domains/42/purge", 200,
                "{\"ok\":true,\"mode\":\"selective\",\"total\":1,\"success\":1,\"removed\":3}");

        client.purgeCache(42, PurgeOptions.patterns("/img/*"));

        JsonObject body = lastJson();
        assertNotNull(body);
        assertEquals(1, body.getAsJsonArray("patterns").size());
        assertEquals("/img/*", body.getAsJsonArray("patterns").get(0).getAsString());
    }

    @Test
    void purgeCacheAllOption() {
        respond("/api/v1/domains/42/purge", 200, "{\"ok\":true,\"mode\":\"all\",\"total\":4,\"success\":4}");

        client.purgeCache(42, PurgeOptions.all());

        JsonObject body = lastJson();
        assertNotNull(body);
        assertTrue(body.get("all").getAsBoolean());
        assertFalse(body.has("urls"));
        assertFalse(body.has("prefixes"));
        assertFalse(body.has("patterns"));
    }

    @Test
    void purgeCacheValidationFailsBeforeHttpCall() {
        respond("/api/v1/domains/42/purge", 200, "{\"ok\":true,\"total\":0,\"success\":0}");

        List<String> tooMany = new ArrayList<>();
        for (int i = 0; i <= PurgeOptions.MAX_ENTRIES; i++) tooMany.add("/p" + i);
        String tooLong = "/" + "a".repeat(PurgeOptions.MAX_ENTRY_LENGTH);

        // null options
        assertThrows(IllegalArgumentException.class, () -> client.purgeCache(42, (PurgeOptions) null));
        // 沒有任何條件
        assertThrows(IllegalArgumentException.class, () -> client.purgeCache(42, new PurgeOptions()));
        // all 不可與其他條件混用
        assertThrows(IllegalArgumentException.class,
                () -> client.purgeCache(42, PurgeOptions.all().addUrl("/a.css")));
        PurgeOptions allPlusPrefix = PurgeOptions.prefixes("/static/");
        allPlusPrefix.setAll(true);
        assertThrows(IllegalArgumentException.class, () -> client.purgeCache(42, allPlusPrefix));
        // 空字串條目
        assertThrows(IllegalArgumentException.class, () -> client.purgeCache(42, PurgeOptions.urls("")));
        assertThrows(IllegalArgumentException.class, () -> client.purgeCache(42, PurgeOptions.prefixes("/ok", null)));
        // 超過條目數上限
        assertThrows(IllegalArgumentException.class, () -> client.purgeCache(42, PurgeOptions.urls(
                tooMany.toArray(new String[0]))));
        // 單一條目過長
        assertThrows(IllegalArgumentException.class, () -> client.purgeCache(42, PurgeOptions.urls(tooLong)));
        // pattern 必須以 / 開頭
        assertThrows(IllegalArgumentException.class, () -> client.purgeCache(42, PurgeOptions.patterns("assets/*.js")));

        assertEquals(0, hits.get(), "驗證失敗時不應發出任何 HTTP 請求");
    }

    @Test
    void purgeCacheAcceptsEntryLimitBoundary() {
        respond("/api/v1/domains/42/purge", 200,
                "{\"ok\":true,\"mode\":\"selective\",\"total\":1,\"success\":1,\"removed\":100}");

        PurgeOptions opts = new PurgeOptions();
        for (int i = 0; i < PurgeOptions.MAX_ENTRIES; i++) opts.addPrefix("/p" + i + "/");

        PurgeResult r = client.purgeCache(42, opts);

        assertEquals(1, hits.get());
        assertEquals(100, r.getRemoved());
        assertEquals(PurgeOptions.MAX_ENTRIES, lastJson().getAsJsonArray("prefixes").size());
    }

    @Test
    void uploadSsl() {
        respond("/api/v1/domains/7/ssl", 200, "{\"ok\":true}");
        assertDoesNotThrow(() -> client.uploadSsl(7, "CERT", "KEY"));
    }

    @Test
    void originIps() {
        respond("/api/public/origin-ips", 200, "{\"ips\":[\"1.2.3.4\",\"5.6.7.8\"],\"count\":2}");
        List<String> ips = client.originIps();
        assertEquals(2, ips.size());
        assertEquals("1.2.3.4", ips.get(0));
        assertEquals("5.6.7.8", ips.get(1));
    }

    @Test
    void apiError() {
        respond("/api/v1/domains", 401, "{\"error\":\"未授權\"}");
        FurCdnException ex = assertThrows(FurCdnException.class, () -> client.listDomains());
        assertEquals(401, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("未授權"));
    }
}
