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
    implementation("com.github.FurCDN:java-sdk:0.1.0")
}
```

Groovy DSL：

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.FurCDN:java-sdk:0.1.0'
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
    <version>0.1.0</version>
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
    }
}
```

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
