package com.cooked.backend.dto.request;

import com.cooked.backend.entity.Status;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateUserStatusRequest {
    @NotNull
    private Status status;
}
