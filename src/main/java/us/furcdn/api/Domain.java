package us.furcdn.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Domain {
    private long id;
    private String name;
    private boolean enabled;
}
