# java-sdk

[FurCDN](https://www.furcdn.us) 開放 API 的 Java SDK，使用 Lombok + Gson。

完整 API 文檔：<https://docs.furcdn.us/api>

## 安裝（JitPack）

`build.gradle.kts`：

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.FurCDN:java-sdk:0.3.0")
}
```

Groovy DSL：

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.FurCDN:java-sdk:0.3.0'
}
```

Maven：

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.FurCDN</groupId>
    <artifactId>java-sdk</artifactId>
    <version>0.3.0</version>
</dependency>
```

## 使用

```java
import us.furcdn.api.FurCdnClient;
import us.furcdn.api.Domain;
import us.furcdn.api.PurgeResult;

public class Demo {
    public static void main(String[] args) {
        FurCdnClient client = new FurCdnClient("fck_xxxxxxxx_xxxxxxxxxxxxxxxxxxxxxxxx");

        for (Domain d : client.listDomains()) {
            System.out.printf("%d  %s  enabled=%b%n", d.getId(), d.getName(), d.isEnabled());
        }

        PurgeResult r = client.purgeCache(123);
        System.out.printf("purged %d/%d nodes%n", r.getSuccess(), r.getTotal());

        client.uploadSsl(123,
            "-----BEGIN CERTIFICATE-----\n...",
            "-----BEGIN PRIVATE KEY-----\n...");

        // 取得全部節點回源 IP（公開端點，不需 API key）。清單非固定，建議定期重取後同步源站防火牆白名單。
        System.out.println(client.originIps());
    }
}
```

## 選擇性清除快取

`purgeCache(domainId)` 會清除整個域名的快取（行為與舊版相同，不帶 request body）。
只要清掉部分內容時，改用 `purgeCache(domainId, PurgeOptions)`：

```java
import us.furcdn.api.PurgeOptions;

// 精確 URL：可帶完整 URL（host 必須等於域名）或以 / 開頭的路徑
PurgeResult r = client.purgeCache(123,
    PurgeOptions.urls("https://example.com/a.css?v=1", "/b.js"));

// 路徑前綴
client.purgeCache(123, PurgeOptions.prefixes("/static/", "https://example.com/img/"));

// 路徑 glob（* 可匹配任意字元，含 /）
client.purgeCache(123, PurgeOptions.patterns("/assets/*.js"));

// 混用（任一命中即清除）
client.purgeCache(123, new PurgeOptions()
    .addPrefix("/static/")
    .addPattern("/img/*")
    .addUrl("/index.html"));

// 清除整個域名，等同 purgeCache(123)
client.purgeCache(123, PurgeOptions.all());

System.out.printf("%s：%d/%d 節點完成，共刪除 %d 筆快取%n",
    r.getMode(), r.getSuccess(), r.getTotal(), r.getRemoved());
```

`PurgeOptions` 的三種條件：

| 欄位 | 比對方式 |
| --- | --- |
| `urls` | 精確 URL。查詢字串算在比對內（`/a.css` 不會清掉 `/a.css?v=1`），除非該域名開啟了 ignoreQueryString；會清掉該 URL 的所有變體（Vary 副本、GET 與 HEAD）。 |
| `prefixes` | URL 路徑前綴，查詢字串忽略。`/static/` 會命中 `/static/a.js` 與 `/static/x/y.css`。帶完整 URL 時只取路徑。 |
| `patterns` | URL 路徑的 glob，`*` 可匹配任意字元（含 `/`），必須以 `/` 開頭。 |

規則：

- 沒有 body、空 body、`{"all":true}` 都是清除整個域名，舊的呼叫端不需修改。
- `urls`、`prefixes`、`patterns` 可同時使用（聯集），但不可與 `all:true` 混用。
- 條目總數上限 100（三種合計）、單一條目最長 2048 字元、不可為空字串。
- 上述限制與格式錯誤會在送出請求前由 SDK 檢查，直接丟出 `IllegalArgumentException`，不會打到 API。
- 速率限制：整站清除 10 次 / 5 分鐘、選擇性清除 60 次 / 5 分鐘，超過回 `429`（`FurCdnException`）。

回應欄位：`mode` 為 `all` 或 `selective`；`total` 是正在服務該域名的線上節點數，`success` 是已確認的節點數；
`removed`（僅選擇性清除）是各節點合計刪除的快取條目數——同一個 URL 在 3 台節點有快取就算 3 筆。

> 快取規則（TTL / 不快取規則）只在節點回源時套用，修改規則不會動到已經快取的內容；
> 要讓規則變更立即生效，必須清除對應的快取。

## 錯誤處理

非 2xx 回應會丟出 `FurCdnException`：

```java
try {
    client.listDomains();
} catch (FurCdnException e) {
    System.err.printf("HTTP %d: %s%n", e.getStatusCode(), e.getMessage());
}
```

## 開發

需要 JDK 17+ 與 Gradle：

```bash
gradle build
gradle test
```

## License

MIT
