package space.zeinab.demo.camel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class CustomerProfile {
    private String customerId;
    private String name;
    private String tier;
    private String email;
}
