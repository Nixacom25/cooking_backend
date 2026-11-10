package com.cooked.backend.service.impl;

import com.cooked.backend.dto.response.ReviewChangesResponse;
import com.cooked.backend.entity.AssignmentStatus;
import com.cooked.backend.entity.Recipe;
import com.cooked.backend.entity.RecipeAssignment;
import com.cooked.backend.entity.RecipeCategory;
import com.cooked.backend.entity.RecipeIngredient;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.RecipeAssignmentRepository;
import com.cooked.backend.service.RecipeReviewService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeReviewServiceImpl implements RecipeReviewService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<AssignmentStatus> OPEN = EnumSet.of(AssignmentStatus.ASSIGNED, AssignmentStatus.NOT_STARTED,
            AssignmentStatus.IN_PROGRESS, AssignmentStatus.NEEDS_CORRECTION);

    private final RecipeAssignmentRepository assignments;

    @Override
    public Map<String, String> snapshot(Recipe r) {
        Map<String, String> s = new LinkedHashMap<>();
        s.put("name", text(r.getName()));
        s.put("image", text(r.getImage()));
        s.put("servings", num(r.getServings()));
        s.put("times", "Prep " + num(r.getPrepTime()) + " min · Cook " + num(r.getCookTime()) + " min");
        s.put("nutrition", num(r.getKcal()) + " kcal · P " + num(r.getProteinG()) + " g · C " + num(r.getCarbsG())
                + " g · F " + num(r.getFatG()) + " g · Fiber " + num(r.getFiberG()) + " g · Na " + num(r.getSodiumMg()) + " mg");
        s.put("cuisine", r.getCuisine() == null ? "" : text(r.getCuisine().getName()));
        s.put("categories", r.getCategories() == null ? "" : r.getCategories().stream().map(RecipeCategory::getName)
                .filter(Objects::nonNull).map(String::trim).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.joining(", ")));
        s.put("tips", text(r.getTips()));
        s.put("ingredients", r.getRecipeIngredients() == null ? "" : r.getRecipeIngredients().stream()
                .map(RecipeReviewServiceImpl::ingredientLine).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.joining("\n")));
        s.put("steps", r.getSteps() == null ? "" : r.getSteps().stream().map(RecipeReviewServiceImpl::text)
                .filter(x -> !x.isEmpty()).collect(Collectors.joining("\n")));
        s.put("equipment", r.getEquipment() == null ? "" : r.getEquipment().stream().map(RecipeReviewServiceImpl::text)
                .filter(x -> !x.isEmpty()).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.joining(", ")));
        s.put("price", num(r.getTotalPrice()));
        return s;
    }

    @Override
    public void captureBaseline(RecipeAssignment a) {
        if (a == null || a.getRecipe() == null || a.getBaselineSnapshot() != null) return;
        a.setBaselineSnapshot(write(snapshot(a.getRecipe())));
        a.setBaselineLate(false);
    }

    @Override
    @Transactional
    public void captureBaselineIfMissing(Recipe recipe) {
        if (recipe == null || recipe.getId() == null) return;
        for (RecipeAssignment a : assignments.findActiveAssignmentsByRecipeId(recipe.getId())) {
            if (a.getBaselineSnapshot() == null && OPEN.contains(a.getStatus())) {
                a.setBaselineSnapshot(write(snapshot(recipe)));
                a.setBaselineLate(true);
                assignments.save(a);
            }
        }
    }

    @Override
    public void recordSendBack(RecipeAssignment a, List<String> fieldsToFix, List<String> fieldsApproved) {
        a.setCorrectionSnapshot(write(snapshot(a.getRecipe())));
        a.setFieldsToFix(write(clean(fieldsToFix)));
        a.setFieldsApproved(write(clean(fieldsApproved)));
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewChangesResponse changes(UUID assignmentId) {
        RecipeAssignment a = assignments.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found"));
        Map<String, String> now = snapshot(a.getRecipe());
        Map<String, String> before = readMap(a.getBaselineSnapshot());
        Map<String, String> atSendBack = readMap(a.getCorrectionSnapshot());

        List<ReviewChangesResponse.FieldChange> fields = new ArrayList<>();
        for (String key : FIELDS) {
            String after = now.getOrDefault(key, "");
            String was = before == null ? null : before.getOrDefault(key, "");
            fields.add(ReviewChangesResponse.FieldChange.builder()
                    .key(key).before(was).after(after).changed(was != null && !was.equals(after)).build());
        }
        List<ReviewChangesResponse.Instruction> instructions = new ArrayList<>();
        for (String key : fieldsToFix(a)) {
            boolean respected = atSendBack != null && !Objects.equals(atSendBack.getOrDefault(key, ""), now.getOrDefault(key, ""));
            instructions.add(ReviewChangesResponse.Instruction.builder().key(key).respected(respected).build());
        }
        return ReviewChangesResponse.builder()
                .baselineAvailable(before != null)
                .baselineLate(Boolean.TRUE.equals(a.getBaselineLate()))
                .fields(fields)
                .instructions(instructions)
                .previouslyApproved(readList(a.getFieldsApproved()))
                .build();
    }

    @Override
    public List<String> fieldsToFix(RecipeAssignment a) {
        return a == null ? List.of() : readList(a.getFieldsToFix());
    }

    static String ingredientLine(RecipeIngredient ri) {
        String name = ri.getIngredient() == null ? "" : text(ri.getIngredient().getName());
        String qty = text(ri.getQuantity());
        return (qty.isEmpty() ? "" : qty + " ") + name;
    }

    private static List<String> clean(List<String> keys) {
        if (keys == null) return List.of();
        return keys.stream().filter(FIELDS::contains).distinct().toList();
    }

    private static String text(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }

    private static String num(Number n) {
        if (n == null) return "—";
        double d = n.doubleValue();
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(Math.round(d * 100) / 100.0);
    }

    private static String write(Object o) {
        try {
            return JSON.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, String> readMap(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return JSON.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {});
        } catch (Exception e) {
            log.warn("[RecipeReview] unreadable snapshot: {}", e.getMessage());
            return null;
        }
    }

    private static List<String> readList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return JSON.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
