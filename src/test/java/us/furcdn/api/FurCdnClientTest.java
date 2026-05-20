package us.furcdn.api;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FurCdnClientTest {

    private HttpServer server;
    private FurCdnClient client;

    @BeforeEach
    void start() throws IOException {
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
            byte[] data = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, data.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(data);
            }
        });
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
        respond("/api/v1/domains/42/purge", 200, "{\"ok\":true,\"total\":3,\"success\":3}");
        PurgeResult r = client.purgeCache(42);
        assertTrue(r.isOk());
        assertEquals(3, r.getTotal());
        assertEquals(3, r.getSuccess());
    }

    @Test
    void uploadSsl() {
        respond("/api/v1/domains/7/ssl", 200, "{\"ok\":true}");
        assertDoesNotThrow(() -> client.uploadSsl(7, "CERT", "KEY"));
    }

    @Test
    void apiError() {
        respond("/api/v1/domains", 401, "{\"error\":\"未授權\"}");
        FurCdnException ex = assertThrows(FurCdnException.class, () -> client.listDomains());
        assertEquals(401, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("未授權"));
    }
}
