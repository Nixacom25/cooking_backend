package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.RecipeFlagRequest;
import com.cooked.backend.dto.response.RecipeFlagResponse;
import com.cooked.backend.dto.response.RecipeMergeResponse;
import com.cooked.backend.entity.Recipe;
import com.cooked.backend.entity.RecipeFlag;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.RecipeFlagRepository;
import com.cooked.backend.repository.RecipeRepository;
import com.cooked.backend.service.RecipeModerationService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecipeModerationServiceImpl implements RecipeModerationService {

    private final RecipeFlagRepository flags;
    private final RecipeRepository recipes;
    private final EntityManager em;

    @Override
    @Transactional(readOnly = true)
    public List<RecipeFlagResponse> flags(UUID recipeId) {
        return flags.findByRecipeIdOrderByCreatedAtDesc(recipeId).stream().map(RecipeModerationServiceImpl::view).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public com.cooked.backend.dto.response.RecipeStatsResponse stats(UUID recipeId) {
        if (!recipes.existsById(recipeId)) throw new ResourceNotFoundException("Recipe not found");
        String id = recipeId.toString();
        java.util.function.Function<String, Long> count = (jpql) -> (Long) em.createQuery(jpql).setParameter("id", recipeId).getSingleResult();
        long views = (Long) em.createQuery("select count(e) from ProductEvent e where e.type = com.cooked.backend.entity.ProductEventType.RECIPE_VIEW and e.detail = :d")
                .setParameter("d", id).getSingleResult();
        long views30 = (Long) em.createQuery("select count(e) from ProductEvent e where e.type = com.cooked.backend.entity.ProductEventType.RECIPE_VIEW and e.detail = :d and e.createdAt >= :from")
                .setParameter("d", id).setParameter("from", LocalDateTime.now().minusDays(30)).getSingleResult();
        return new com.cooked.backend.dto.response.RecipeStatsResponse(views, views30,
                count.apply("select count(c) from Cookbook c join c.recipes r where r.id = :id"),
                count.apply("select count(m) from MealPlan m where m.recipe.id = :id"),
                count.apply("select count(g) from GroceryItem g where g.recipe.id = :id"),
                count.apply("select count(f) from RecipeFlag f where f.recipeId = :id and f.resolvedAt is null"),
                count.apply("select count(a) from RecipeAssignment a where a.recipe.id = :id"));
    }

    @Override
    @Transactional
    public RecipeFlagResponse flag(UUID recipeId, RecipeFlagRequest r, String adminEmail) {
        if (!recipes.existsById(recipeId)) throw new ResourceNotFoundException("Recipe not found");
        String note = r.note() == null || r.note().isBlank() ? null : r.note().trim();
        return view(flags.save(RecipeFlag.builder().recipeId(recipeId).reason(r.reason()).note(note).createdBy(adminEmail).build()));
    }

    @Override
    @Transactional
    public RecipeFlagResponse resolve(UUID flagId, String adminEmail) {
        RecipeFlag f = flags.findById(flagId).orElseThrow(() -> new ResourceNotFoundException("Report not found"));
        if (f.getResolvedAt() == null) {
            f.setResolvedAt(LocalDateTime.now());
            f.setResolvedBy(adminEmail);
        }
        return view(flags.save(f));
    }

    @Override
    @Transactional
    public RecipeMergeResponse merge(UUID keepId, UUID duplicateId, String adminEmail) {
        if (keepId.equals(duplicateId)) throw new BadRequestException("Pick another recipe: a recipe cannot be merged into itself.");
        Recipe keep = recipes.findById(keepId).orElseThrow(() -> new ResourceNotFoundException("Recipe to keep not found"));
        Recipe dup = recipes.findById(duplicateId).orElseThrow(() -> new ResourceNotFoundException("Duplicate recipe not found"));
        if (keep.isDeleted()) throw new BadRequestException("The recipe to keep is archived: restore it first.");
        if (dup.isDeleted()) throw new BadRequestException("The duplicate is already archived.");

        int cookbooks = em.createNativeQuery("insert into cookbook_recipes (cookbook_id, recipe_id) "
                        + "select c.cookbook_id, :keep from cookbook_recipes c where c.recipe_id = :dup "
                        + "and not exists (select 1 from cookbook_recipes k where k.cookbook_id = c.cookbook_id and k.recipe_id = :keep)")
                .setParameter("keep", keepId).setParameter("dup", duplicateId).executeUpdate();
        em.createNativeQuery("delete from cookbook_recipes where recipe_id = :dup").setParameter("dup", duplicateId).executeUpdate();
        int meals = em.createQuery("update MealPlan m set m.recipe = :keep where m.recipe = :dup")
                .setParameter("keep", keep).setParameter("dup", dup).executeUpdate();
        int grocery = em.createQuery("update GroceryItem g set g.recipe = :keep where g.recipe = :dup")
                .setParameter("keep", keep).setParameter("dup", dup).executeUpdate();

        dup.setDeleted(true);
        recipes.save(dup);
        flags.save(RecipeFlag.builder().recipeId(duplicateId).reason("DUPLICATE")
                .note("Merged into " + keep.getName() + " (" + keepId + ")").createdBy(adminEmail)
                .resolvedAt(LocalDateTime.now()).resolvedBy(adminEmail).build());
        return new RecipeMergeResponse(keepId, duplicateId, cookbooks, meals, grocery);
    }

    static RecipeFlagResponse view(RecipeFlag f) {
        return new RecipeFlagResponse(f.getId(), f.getRecipeId(), f.getReason(), f.getNote(), f.getCreatedBy(), f.getCreatedAt(), f.getResolvedAt(), f.getResolvedBy());
    }
}
