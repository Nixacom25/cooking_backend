package com.cooked.backend.dto.request;

import com.cooked.backend.entity.AmbassadorStatus;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AmbassadorRequest {
    @NotBlank @Size(max = 120)
    private String name;
    @Email @Size(max = 255)
    private String email;
    /** Optional: generated from the name when empty. */
    @Pattern(regexp = "^$|[A-Za-z0-9]{3,20}", message = "Code: 3-20 letters or digits")
    private String code;
    @Size(max = 40)
    private String platform;
    @Size(max = 80)
    private String handle;
    @Size(max = 40)
    private String audience;
    /** Null = workspace default. */
    @DecimalMin("0") @DecimalMax("100")
    private BigDecimal commissionPercent;
    private AmbassadorStatus status;
    @Size(max = 500)
    private String notes;
}
