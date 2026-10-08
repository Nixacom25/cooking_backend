package com.cooked.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Another name that resolves to a visual; aliasKey is the normalized form and is unique catalog-wide. */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class IngredientAlias {

    @Column(name = "alias", nullable = false, length = 120)
    private String alias;

    @Column(name = "alias_key", nullable = false, length = 120)
    private String aliasKey;
}
