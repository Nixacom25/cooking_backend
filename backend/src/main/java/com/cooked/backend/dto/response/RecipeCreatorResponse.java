package com.cooked.backend.dto.response;

import java.util.UUID;

public class RecipeCreatorResponse {
    private UUID id;
    private String firstname;
    private String lastname;
    private String photo;

    public RecipeCreatorResponse() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getFirstname() { return firstname; }
    public void setFirstname(String firstname) { this.firstname = firstname; }
    public String getLastname() { return lastname; }
    public void setLastname(String lastname) { this.lastname = lastname; }
    public String getPhoto() { return photo; }
    public void setPhoto(String photo) { this.photo = photo; }

    public static RecipeCreatorResponseBuilder builder() {
        return new RecipeCreatorResponseBuilder();
    }

    public static class RecipeCreatorResponseBuilder {
        private final RecipeCreatorResponse response = new RecipeCreatorResponse();

        public RecipeCreatorResponseBuilder id(UUID id) { response.setId(id); return this; }
        public RecipeCreatorResponseBuilder firstname(String firstname) { response.setFirstname(firstname); return this; }
        public RecipeCreatorResponseBuilder lastname(String lastname) { response.setLastname(lastname); return this; }
        public RecipeCreatorResponseBuilder photo(String photo) { response.setPhoto(photo); return this; }

        public RecipeCreatorResponse build() {
            return response;
        }
    }
}
