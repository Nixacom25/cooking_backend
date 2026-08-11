package com.cooked.backend.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchAssignmentRequest {

    @NotNull(message = "L'identifiant du stagiaire est obligatoire")
    private UUID userId;

    @Min(value = 1, message = "Le nombre de recettes doit être supérieur ou égal à 1")
    private int count;
}
