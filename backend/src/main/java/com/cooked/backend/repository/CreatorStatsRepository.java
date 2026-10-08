package com.cooked.backend.repository;

import com.cooked.backend.entity.Recipe;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** A creator's public recipes and how much they are used (grocery-list adds), read-only. */
public interface CreatorStatsRepository extends Repository<Recipe, UUID> {

    interface RecipeUses {
        UUID getId();
        String getName();
        String getImage();
        Long getUses();
        LocalDateTime getCreatedAt();
    }

    interface DayCount {
        LocalDate getDay();
        Long getTotal();
    }

    String PUBLIC = "r.user.id = :id and r.isPublic = true and (r.isDeleted is null or r.isDeleted = false)";

    @Query("select count(r) from Recipe r where " + PUBLIC)
    long countPublic(@Param("id") UUID userId);

    @Query("select count(r) from Recipe r where " + PUBLIC + " and r.createdAt >= :from and r.createdAt < :to")
    long countPublicBetween(@Param("id") UUID userId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Recipe-screen views (app events) of every creator's public recipes since [from]. */
    @Query("select count(e) from ProductEvent e where e.type = com.cooked.backend.entity.ProductEventType.RECIPE_VIEW and e.createdAt >= :from "
            + "and e.detail in (select cast(r.id as string) from Recipe r where r.user.role = com.cooked.backend.entity.Role.CREATOR and r.isPublic = true)")
    long countAllCreatorViewsSince(@Param("from") LocalDateTime from);

    /** Other users who keep this creator's public recipes in a cookbook ("followers on Cooked"). */
    @Query("select count(distinct c.user.id) from Cookbook c join c.recipes r where r.user.id = :id and r.isPublic = true and c.user.id <> :id")
    long countSavers(@Param("id") UUID userId);

    /** Recipe screens opened (app RECIPE_VIEW events) on this creator's public recipes since [from]. */
    @Query("select count(e) from ProductEvent e where e.type = com.cooked.backend.entity.ProductEventType.RECIPE_VIEW and e.createdAt >= :from "
            + "and e.detail in (select cast(r.id as string) from Recipe r where " + PUBLIC + ")")
    long countViewsSince(@Param("id") UUID userId, @Param("from") LocalDateTime from);

    @Query("select count(gi) from GroceryItem gi where gi.recipe.user.id = :id and gi.recipe.isPublic = true")
    long totalUses(@Param("id") UUID userId);

    @Query("select r.id as id, r.name as name, r.image as image, (select count(gi) from GroceryItem gi where gi.recipe = r) as uses, r.createdAt as createdAt "
            + "from Recipe r where " + PUBLIC + " order by (select count(gi) from GroceryItem gi where gi.recipe = r) desc, r.createdAt desc")
    List<RecipeUses> topRecipes(@Param("id") UUID userId, Pageable page);

    @Query("select cast(r.createdAt as LocalDate) as day, count(r) as total from Recipe r where " + PUBLIC
            + " and r.createdAt >= :from group by cast(r.createdAt as LocalDate)")
    List<DayCount> publishedDaily(@Param("id") UUID userId, @Param("from") LocalDateTime from);
}
