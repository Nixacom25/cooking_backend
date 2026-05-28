package com.cooked.backend.dto.request;

import jakarta.validation.constraints.NotBlank;

public class IngredientPayload {
    @NotBlank(message = "Ingredient name is required")
    private String name;

    private String icon;
    private String quantity;
    private Double price;

    public IngredientPayload() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public String getQuantity() { return quantity; }
    public void setQuantity(String quantity) { this.quantity = quantity; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
}
