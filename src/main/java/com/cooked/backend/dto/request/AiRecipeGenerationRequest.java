package com.cooked.backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRecipeGenerationRequest {
    private List<String> ingredients;
    // user_preferences can also be sent optionally based on API docs screenshot
    private UserPreferencesPayload user_preferences;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserPreferencesPayload {
        private List<String> allergies;
        private List<String> preferences;
        private List<String> dislikes;
        private List<String> cuisines_love;
        private List<String> kitchen_tools;
        private List<String> cooking_goals;
        private java.util.Map<String, Integer> DNA;
        private Object skill_level; // Can be String or Object with label/percent
        private Object time_minutes;
        private Object servings;
    }
}
