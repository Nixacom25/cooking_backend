package com.cooked.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AskCookedRequest {
    @NotBlank
    @Size(max = 500)
    private String question;
}
