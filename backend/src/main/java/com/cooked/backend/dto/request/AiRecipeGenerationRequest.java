package com.cooked.backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import java.util.List;

@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRecipeGenerationRequest {
    private List<String> ingredients;
    private UserPreferencesPayload user_preferences;
    private String custom_instructions;

    public List<String> getIngredients() { return ingredients; }
    public void setIngredients(List<String> ingredients) { this.ingredients = ingredients; }
    public UserPreferencesPayload getUser_preferences() { return user_preferences; }
    public void setUser_preferences(UserPreferencesPayload user_preferences) { this.user_preferences = user_preferences; }
    public String getCustom_instructions() { return custom_instructions; }
    public void setCustom_instructions(String custom_instructions) { this.custom_instructions = custom_instructions; }

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
        private Object skill_level;
        private Object time_minutes;
        private Object servings;

        public List<String> getAllergies() { return allergies; }
        public void setAllergies(List<String> allergies) { this.allergies = allergies; }
        public List<String> getPreferences() { return preferences; }
        public void setPreferences(List<String> preferences) { this.preferences = preferences; }
        public List<String> getDislikes() { return dislikes; }
        public void setDislikes(List<String> dislikes) { this.dislikes = dislikes; }
        public List<String> getCuisines_love() { return cuisines_love; }
        public void setCuisines_love(List<String> cuisines_love) { this.cuisines_love = cuisines_love; }
        public List<String> getKitchen_tools() { return kitchen_tools; }
        public void setKitchen_tools(List<String> kitchen_tools) { this.kitchen_tools = kitchen_tools; }
        public List<String> getCooking_goals() { return cooking_goals; }
        public void setCooking_goals(List<String> cooking_goals) { this.cooking_goals = cooking_goals; }
        public java.util.Map<String, Integer> getDNA() { return DNA; }
        public void setDNA(java.util.Map<String, Integer> DNA) { this.DNA = DNA; }
        public Object getSkill_level() { return skill_level; }
        public void setSkill_level(Object skill_level) { this.skill_level = skill_level; }
        public Object getTime_minutes() { return time_minutes; }
        public void setTime_minutes(Object time_minutes) { this.time_minutes = time_minutes; }
        public Object getServings() { return servings; }
        public void setServings(Object servings) { this.servings = servings; }
    }
}
