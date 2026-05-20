package us.furcdn.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class FurCdnClient {

    public static final String DEFAULT_BASE_URL = "https://www.furcdn.us";

    private final String baseUrl;
    private final String apiKey;
    private final HttpClient http;
    private final Gson gson = new Gson();

    public FurCdnClient(String apiKey) {
        this(DEFAULT_BASE_URL, apiKey);
    }

    public FurCdnClient(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** 列出當前 API key 擁有者的所有域名 */
    public List<Domain> listDomains() {
        JsonObject obj = request("GET", "/api/v1/domains", null);
        Domain[] arr = gson.fromJson(obj.getAsJsonArray("domains"), Domain[].class);
        return List.of(arr);
    }

    /** 刷新指定域名所有節點的 L1+L2 快取 */
    public PurgeResult purgeCache(long domainId) {
        JsonObject obj = request("POST", "/api/v1/domains/" + domainId + "/purge", null);
        return gson.fromJson(obj, PurgeResult.class);
    }

    /** 為指定域名上傳 PEM 格式憑證和私鑰，會關閉自動續約 */
    public void uploadSsl(long domainId, String cert, String key) {
        String body = gson.toJson(Map.of("cert", cert, "key", key));
        request("POST", "/api/v1/domains/" + domainId + "/ssl", body);
    }

    private JsonObject request(String method, String path, String body) {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + apiKey);

        HttpRequest.BodyPublisher pub = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);

        if (body != null) b.header("Content-Type", "application/json");
        b.method(method, pub);

        try {
            HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            String text = res.body();
            if (res.statusCode() >= 400) {
                String msg = text;
                try {
                    JsonObject err = gson.fromJson(text, JsonObject.class);
                    if (err != null && err.has("error")) msg = err.get("error").getAsString();
                } catch (JsonSyntaxException ignore) {}
                throw new FurCdnException(res.statusCode(), msg);
            }
            if (text == null || text.isEmpty()) return new JsonObject();
            return gson.fromJson(text, JsonObject.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
