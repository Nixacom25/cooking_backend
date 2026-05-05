package com.cooked.backend.service.impl;

import com.cooked.backend.entity.RecipeData;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.repository.RecipeDataRepository;
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
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeDataServiceImpl implements RecipeDataService {

    private final RecipeDataRepository recipeDataRepository;
    private final CloudinaryService cloudinaryService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${openai.api.key}")
    private String openAiApiKey;

    private static final String DALLE_URL = "https://api.openai.com/v1/images/generations";

    @Override
    public RecipeData create(String name, MultipartFile image) {
        if (recipeDataRepository.existsByName(name)) {
            throw new BadRequestException("Recipe with name '" + name + "' already exists.");
        }
        try {
            String imageUrl;
            if (image != null && !image.isEmpty()) {
                // Upload provided image
                imageUrl = cloudinaryService.upload(image);
            } else {
                // Generate image with AI
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
    public List<RecipeData> bulkCreate(List<String> names, List<MultipartFile> images) {
        if (names == null) {
            throw new BadRequestException("Names list must not be null.");
        }

        List<RecipeData> savedItems = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            
            // Uniqueness check for bulk as well
            if (recipeDataRepository.existsByName(name)) {
                log.warn("Recipe already exists, skipping: {}", name);
                continue;
            }

            MultipartFile image = (images != null && i < images.size()) ? images.get(i) : null;
            try {
                savedItems.add(create(name, image));
            } catch (Exception e) {
                // Continue with next one in bulk for background compatibility
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

        Map<String, Object> requestBody = Map.of(
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
            // Upload the AI generated image to Cloudinary to make it permanent
            return cloudinaryService.uploadUrl(tempUrl);
        }

        throw new IOException("Failed to generate image with DALLE");
    }

    @Override
    public List<RecipeData> getAll() {
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
    public RecipeData update(Long id, String name, MultipartFile image) {
        RecipeData recipeData = getById(id);
        
        // Update name if changed
        if (name != null && !name.equals(recipeData.getName())) {
            // Check uniqueness if name changed
            if (recipeDataRepository.existsByName(name)) {
                throw new BadRequestException("Recipe with name '" + name + "' already exists.");
            }
            recipeData.setName(name);
        }

        // Update image if provided
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
    public void deleteMultiple(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        recipeDataRepository.deleteAllById(ids);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public java.util.Map<String, Object> bulkUpdateImages(List<MultipartFile> files) {
        int updatedCount = 0;
        List<String> failedFiles = new ArrayList<>();
        
        for (MultipartFile file : files) {
            String originalName = file.getOriginalFilename();
            if (originalName == null || originalName.isBlank()) continue;
            
            // Remove extension
            String cleanName = originalName.contains(".") 
                ? originalName.substring(0, originalName.lastIndexOf(".")) 
                : originalName;
            
            // Replace separators with spaces to improve matching
            String nameToSearch = cleanName.replace("_", " ").replace("-", " ").trim();
            
            List<RecipeData> recipes = recipeDataRepository.findByNameContainingIgnoreCase(nameToSearch);
            
            if (!recipes.isEmpty()) {
                try {
                    String imageUrl = cloudinaryService.upload(file);
                    for (RecipeData r : recipes) {
                        r.setImageUrl(imageUrl);
                        recipeDataRepository.save(r);
                        updatedCount++;
                    }
                } catch (Exception e) {
                    log.error("Failed to upload image for file: {}", originalName, e);
                    failedFiles.add(originalName + " (Upload failed)");
                }
            } else {
                log.warn("No recipe found for image: {}", originalName);
                failedFiles.add(originalName + " (Recipe not found: " + nameToSearch + ")");
            }
        }
        
        return Map.of(
            "updatedCount", updatedCount,
            "failedCount", failedFiles.size(),
            "failedFiles", failedFiles
        );
    }
}
