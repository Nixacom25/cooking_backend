package com.cooked.backend.service.impl;

import com.cooked.backend.entity.RecipeData;
import com.cooked.backend.entity.Recipe;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.repository.RecipeDataRepository;
import com.cooked.backend.repository.RecipeRepository;
import com.cooked.backend.service.CloudinaryService;
import com.cooked.backend.service.RecipeDataService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeDataServiceImpl implements RecipeDataService {

    private final RecipeDataRepository recipeDataRepository;
    private final RecipeRepository recipeRepository;
    private final CloudinaryService cloudinaryService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${openai.api.key}")
    private String openAiApiKey;

    private static final String DALLE_URL = "https://api.openai.com/v1/images/generations";

    @Override
    public RecipeData create(String name, org.springframework.web.multipart.MultipartFile image) {
        if (recipeDataRepository.existsByName(name)) {
            throw new BadRequestException("Recipe with name '" + name + "' already exists.");
        }
        try {
            String imageUrl;
            if (image != null && !image.isEmpty()) {
                imageUrl = cloudinaryService.upload(image);
            } else {
                imageUrl = generateAiImage(name);
            }

            RecipeData recipeData = RecipeData.builder()
                    .name(name)
                    .imageUrl(imageUrl)
                    .build();
            return recipeDataRepository.save(recipeData);
        } catch (Exception e) {
            log.error("Failed to process recipe: {}", name, e);
            throw new BadRequestException("Failed to process recipe: " + name + ". " + e.getMessage());
        }
    }

    @Override
    public java.util.List<RecipeData> bulkCreate(java.util.List<String> names, java.util.List<org.springframework.web.multipart.MultipartFile> images) {
        if (names == null) {
            throw new BadRequestException("Names list must not be null.");
        }

        java.util.List<RecipeData> savedItems = new java.util.ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            
            if (recipeDataRepository.existsByName(name)) {
                log.warn("Recipe already exists, skipping: {}", name);
                continue;
            }

            org.springframework.web.multipart.MultipartFile image = (images != null && i < images.size()) ? images.get(i) : null;
            try {
                savedItems.add(create(name, image));
            } catch (Exception e) {
                log.warn("Bulk item failed, skipping: {}", name);
            }
        }

        return savedItems;
    }

    private String generateAiImage(String recipeName) throws IOException {
        log.info("Generating AI image for: {}", recipeName);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openAiApiKey);

        java.util.Map<String, Object> requestBody = java.util.Map.of(
                "model", "dall-e-3",
                "prompt", "A minimalist food photography shot. bird's-eye view, centered. A single circular matte ceramic plate of ochre color. On the plate: " + recipeName + ". Background: 100% solid seamless white. NO other objects, NO utensils, NO cutlery, NO napkins. High-end clean culinary style. See the image link to understand the plate's dimensions, background, and image size: https://res.cloudinary.com/davj7mdjj/image/upload/v1776201830/ai-recipe-app/ingredients/kr5zshv0smgr3bqefnmt.jpg",
                "n", 1,
                "size", "1024x1024",
                "style", "natural"
        );

        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);
        ResponseEntity<String> response = restTemplate.postForEntity(DALLE_URL, request, String.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            JsonNode root = objectMapper.readTree(response.getBody());
            String tempUrl = root.path("data").get(0).path("url").asText();
            return cloudinaryService.uploadUrl(tempUrl);
        }

        throw new IOException("Failed to generate image with DALLE");
    }

    @Override
    public java.util.List<RecipeData> getAll() {
        return recipeDataRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    public RecipeData getById(Long id) {
        return recipeDataRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Recipe data not found with id: " + id));
    }

    @Override
    public void delete(Long id) {
        if (!recipeDataRepository.existsById(id)) {
            throw new BadRequestException("Recipe data not found with id: " + id);
        }
        recipeDataRepository.deleteById(id);
    }

    @Override
    public RecipeData update(Long id, String name, org.springframework.web.multipart.MultipartFile image) {
        RecipeData recipeData = getById(id);
        
        if (name != null && !name.equals(recipeData.getName())) {
            if (recipeDataRepository.existsByName(name)) {
                throw new BadRequestException("Recipe with name '" + name + "' already exists.");
            }
            recipeData.setName(name);
        }

        if (image != null && !image.isEmpty()) {
            try {
                String imageUrl = cloudinaryService.upload(image);
                recipeData.setImageUrl(imageUrl);
            } catch (IOException e) {
                log.error("Failed to upload new image for recipe: {}", id, e);
                throw new BadRequestException("Failed to upload image: " + e.getMessage());
            }
        }

        return recipeDataRepository.save(recipeData);
    }

    @Override
    public void deleteMultiple(java.util.List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        recipeDataRepository.deleteAllById(ids);
    }

    @Override
    @Transactional
    public java.util.Map<String, Object> bulkUpdateImages(java.util.List<org.springframework.web.multipart.MultipartFile> files) {
        int updatedCount = 0;
        java.util.List<String> failedFiles = new java.util.ArrayList<>();
        
        for (org.springframework.web.multipart.MultipartFile file : files) {
            String originalName = file.getOriginalFilename();
            if (originalName == null || originalName.isBlank()) continue;
            
            String cleanName = originalName.contains(".") 
                ? originalName.substring(0, originalName.lastIndexOf(".")) 
                : originalName;
            
            String nameToSearch = cleanName.trim();
            
            try {
                java.util.List<Recipe> matchingRecipes = recipeRepository.findAllByNameIgnoreCase(nameToSearch);
                
                if (!matchingRecipes.isEmpty()) {
                    String imageUrl = cloudinaryService.upload(file);
                    for (Recipe r : matchingRecipes) {
                        r.setImage(imageUrl);
                        recipeRepository.save(r);
                        updatedCount++;
                    }
                } else {
                    log.warn("No exact recipe found for image in recipes table: {}", nameToSearch);
                    failedFiles.add(originalName + " (Recipe not found: " + nameToSearch + ")");
                }
            } catch (Exception e) {
                log.error("Failed to process image file: {}", originalName, e);
                failedFiles.add(originalName + " (Error: " + e.getMessage() + ")");
            }
        }
        
        return java.util.Map.of(
            "updatedCount", updatedCount,
            "failedCount", failedFiles.size(),
            "failedFiles", failedFiles
        );
    }

    @Override
    public java.util.List<java.util.Map<String, Object>> getRecipesMissingImages() {
        return recipeRepository.findRecipesMissingImages().stream()
                .map(r -> java.util.Map.of(
                        "id", (Object) r.getId(),
                        "name", (Object) r.getName()
                ))
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public java.util.List<java.util.Map<String, Object>> getRecipesExistingInData() {
        return recipeDataRepository.findRecipesPresentInRecipes().stream()
                .map(rd -> java.util.Map.of(
                        "id", (Object) rd.getId(),
                        "name", (Object) rd.getName()
                ))
                .collect(java.util.stream.Collectors.toList());
    }
}
