package com.cooked.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RecipeFlagRequest(
        @NotBlank @Pattern(regexp = "WRONG_INFO|BAD_IMAGE|DUPLICATE|INAPPROPRIATE|COPYRIGHT|OTHER") String reason,
        @Size(max = 2000) String note) {
}
