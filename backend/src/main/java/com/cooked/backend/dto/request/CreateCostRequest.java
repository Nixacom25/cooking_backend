package com.cooked.backend.dto.request;

import com.cooked.backend.entity.CostCategory;
import com.cooked.backend.entity.CostFrequency;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CreateCostRequest {
    @NotBlank @Size(max = 80)
    private String provider;
    @NotNull
    private CostCategory category;
    @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2)
    private BigDecimal amount;
    @NotBlank @Pattern(regexp = "[A-Za-z]{3}")
    private String currency;
    @NotNull
    private CostFrequency frequency;
    @NotNull
    private LocalDate startDate;
    private LocalDate endDate;
    @Size(max = 500)
    private String notes;
}
