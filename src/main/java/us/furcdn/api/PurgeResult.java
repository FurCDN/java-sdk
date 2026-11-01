package us.furcdn.api;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PurgeResult {
    private boolean ok;

    /** {@code "all"}（整站清除）或 {@code "selective"}（選擇性清除） */
    private String mode;

    /** 正在服務此域名的線上節點數 */
    private int total;

    /** 已確認完成的節點數 */
    private int success;

    /** 選擇性清除時，各節點合計刪除的快取條目數；整站清除時為 0 */
    private int removed;
}
