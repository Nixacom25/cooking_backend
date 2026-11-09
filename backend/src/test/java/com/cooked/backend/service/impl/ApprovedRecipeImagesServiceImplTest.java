package com.cooked.backend.service.impl;

import com.cooked.backend.repository.RecipeAssignmentRepository;
import com.cooked.backend.repository.RecipeAssignmentRepository.ApprovedRecipeImage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApprovedRecipeImagesServiceImplTest {

    private static ApprovedRecipeImage row(String id, String name, String image) {
        return new ApprovedRecipeImage() {
            public UUID getId() { return UUID.fromString(id); }
            public String getName() { return name; }
            public String getImage() { return image; }
        };
    }

    @Test
    void zipsEveryPhotoAndListsFailuresInTheManifest() throws Exception {
        RecipeAssignmentRepository repo = mock(RecipeAssignmentRepository.class);
        when(repo.findApprovedRecipeImages()).thenReturn(List.of(
                row("11111111-aaaa-4aaa-8aaa-000000000001", "Poulet Yassa épicé", "https://img.test/a.png"),
                row("22222222-aaaa-4aaa-8aaa-000000000002", "Broken, \"photo\"", "https://img.test/broken.jpg"),
                row("33333333-aaaa-4aaa-8aaa-000000000003", "Thieb", "https://img.test/c?w=800")));
        var service = new ApprovedRecipeImagesServiceImpl(repo, url -> {
            if (url.contains("broken")) throw new java.io.IOException("HTTP 404");
            return new ApprovedRecipeImagesServiceImpl.Fetched(url.getBytes(StandardCharsets.UTF_8), url.endsWith(".png") ? "image/png" : "image/jpeg");
        });

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.writeZip(out);

        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()), StandardCharsets.UTF_8)) {
            for (ZipEntry e; (e = zip.getNextEntry()) != null; ) entries.put(e.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertEquals(List.of("images/poulet-yassa-epice-11111111.png", "images/thieb-33333333.jpg", "manifest.csv"), List.copyOf(entries.keySet()));
        assertEquals("https://img.test/a.png", entries.get("images/poulet-yassa-epice-11111111.png"));
        String manifest = entries.get("manifest.csv");
        assertTrue(manifest.contains("\"Broken, \"\"photo\"\"\",https://img.test/broken.jpg,MISSING (HTTP 404)"), manifest);
        assertTrue(manifest.contains(",thieb-33333333.jpg"));
    }
}
