package us.furcdn.api;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PurgeResult {
    private boolean ok;
    private int total;
    private int success;
}
