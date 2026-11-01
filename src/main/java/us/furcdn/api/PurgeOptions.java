package us.furcdn.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 選擇性清除快取的請求內容，搭配 {@link FurCdnClient#purgeCache(long, PurgeOptions)} 使用。
 * <p>
 * 三種選擇條件可混用（任一命中即清除）：
 * <ul>
 *   <li>{@code urls}：精確 URL。可帶完整 URL（host 必須等於域名）或以 {@code /} 開頭的路徑；
 *       查詢字串屬於比對的一部分（域名開啟 ignoreQueryString 時則忽略），
 *       會清掉該 URL 的所有快取變體（Vary 副本、GET 與 HEAD）。</li>
 *   <li>{@code prefixes}：URL 路徑前綴比對，查詢字串忽略。{@code /static/} 會命中
 *       {@code /static/a.js}、{@code /static/x/y.css}。也可帶完整 URL，只取其路徑。</li>
 *   <li>{@code patterns}：URL 路徑的 glob，{@code *} 可匹配任意字元（含 {@code /}），
 *       必須以 {@code /} 開頭，查詢字串忽略。</li>
 * </ul>
 * {@code all=true}（清除整個域名）不可與上述條件混用。
 *
 * <pre>{@code
 * client.purgeCache(123, PurgeOptions.urls("https://example.com/a.css?v=1", "/b.js"));
 * client.purgeCache(123, new PurgeOptions().addPrefix("/static/").addPattern("/img/*"));
 * client.purgeCache(123, PurgeOptions.all());
 * }</pre>
 */
@Data
public class PurgeOptions {

    /** urls + prefixes + patterns 的條目總數上限 */
    public static final int MAX_ENTRIES = 100;

    /** 單一條目的長度上限 */
    public static final int MAX_ENTRY_LENGTH = 2048;

    /** 是否清除整個域名的快取 */
    private boolean all;

    /** 精確 URL 清單 */
    private List<String> urls = new ArrayList<>();

    /** 路徑前綴清單 */
    private List<String> prefixes = new ArrayList<>();

    /** 路徑 glob 清單 */
    private List<String> patterns = new ArrayList<>();

    /** 清除整個域名的快取 */
    public static PurgeOptions all() {
        PurgeOptions options = new PurgeOptions();
        options.setAll(true);
        return options;
    }

    /** 以精確 URL 建立選項 */
    public static PurgeOptions urls(String... urls) {
        return new PurgeOptions().addUrls(urls);
    }

    /** 以路徑前綴建立選項 */
    public static PurgeOptions prefixes(String... prefixes) {
        return new PurgeOptions().addPrefixes(prefixes);
    }

    /** 以路徑 glob 建立選項 */
    public static PurgeOptions patterns(String... patterns) {
        return new PurgeOptions().addPatterns(patterns);
    }

    public PurgeOptions addUrl(String url) {
        this.urls.add(url);
        return this;
    }

    public PurgeOptions addUrls(String... more) {
        for (String url : more) this.urls.add(url);
        return this;
    }

    public PurgeOptions addPrefix(String prefix) {
        this.prefixes.add(prefix);
        return this;
    }

    public PurgeOptions addPrefixes(String... more) {
        for (String prefix : more) this.prefixes.add(prefix);
        return this;
    }

    public PurgeOptions addPattern(String pattern) {
        this.patterns.add(pattern);
        return this;
    }

    public PurgeOptions addPatterns(String... more) {
        for (String pattern : more) this.patterns.add(pattern);
        return this;
    }

    /** 在送出請求前檢查內容，不合法時丟出 {@link IllegalArgumentException} */
    public void validate() {
        boolean hasSelectors = !isEmpty(urls) || !isEmpty(prefixes) || !isEmpty(patterns);
        if (all && hasSelectors) {
            throw new IllegalArgumentException("all=true 時不可同時指定 urls / prefixes / patterns");
        }
        if (!all && !hasSelectors) {
            throw new IllegalArgumentException("必須指定 all=true 或至少一個 urls / prefixes / patterns 條目");
        }
        int total = size(urls) + size(prefixes) + size(patterns);
        if (total > MAX_ENTRIES) {
            throw new IllegalArgumentException(
                    "urls + prefixes + patterns 條目總數不可超過 " + MAX_ENTRIES + "（目前 " + total + "）");
        }
        checkEntries("urls", urls);
        checkEntries("prefixes", prefixes);
        checkEntries("patterns", patterns);
        int index = 1;
        for (String pattern : emptyIfNull(patterns)) {
            if (!pattern.startsWith("/")) {
                throw new IllegalArgumentException("patterns 第 " + index + " 個條目必須以 / 開頭：" + pattern);
            }
            index++;
        }
    }

    /** 轉成請求 body；空清單與 false 的 all 不會出現在 JSON 中 */
    JsonObject toJson() {
        validate();
        JsonObject obj = new JsonObject();
        if (all) obj.addProperty("all", true);
        addArray(obj, "urls", urls);
        addArray(obj, "prefixes", prefixes);
        addArray(obj, "patterns", patterns);
        return obj;
    }

    private static void addArray(JsonObject obj, String name, List<String> entries) {
        if (isEmpty(entries)) return;
        JsonArray arr = new JsonArray();
        for (String entry : entries) arr.add(entry);
        obj.add(name, arr);
    }

    private static void checkEntries(String field, List<String> entries) {
        int index = 1;
        for (String entry : emptyIfNull(entries)) {
            if (entry == null || entry.isEmpty()) {
                throw new IllegalArgumentException(field + " 第 " + index + " 個條目不可為空字串");
            }
            if (entry.length() > MAX_ENTRY_LENGTH) {
                throw new IllegalArgumentException(
                        field + " 第 " + index + " 個條目長度不可超過 " + MAX_ENTRY_LENGTH + " 字元（目前 "
                                + entry.length() + "）");
            }
            index++;
        }
    }

    private static boolean isEmpty(List<String> entries) {
        return entries == null || entries.isEmpty();
    }

    private static int size(List<String> entries) {
        return entries == null ? 0 : entries.size();
    }

    private static List<String> emptyIfNull(List<String> entries) {
        return entries == null ? List.of() : entries;
    }
}
