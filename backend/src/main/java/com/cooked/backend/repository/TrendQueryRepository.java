package com.cooked.backend.repository;

import com.cooked.backend.entity.ProductEventType;
import com.cooked.backend.entity.Recipe;
import com.cooked.backend.entity.RecipeOrigin;
import com.cooked.backend.entity.Role;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Read-only aggregates for Trend intelligence, kept apart from the CRUD
 * repositories. Windows are [from, to); only client-owned, non-deleted recipes.
 */
public interface TrendQueryRepository extends Repository<Recipe, UUID> {

    interface LabelCount {
        String getLabel();
        Long getTotal();
    }

    interface DayCount {
        LocalDate getDay();
        Long getTotal();
    }

    String CLIENT_RECIPES = "r.user.role = :role and (r.isDeleted is null or r.isDeleted = false) and r.createdAt >= :from and r.createdAt < :to";

    @Query("select lower(i.name) as label, count(ri) as total from RecipeIngredient ri join ri.recipe r join ri.ingredient i "
            + "where r.origin = :origin and " + CLIENT_RECIPES + " group by lower(i.name) order by count(ri) desc")
    List<LabelCount> ingredientCounts(@Param("origin") RecipeOrigin origin, @Param("role") Role role,
                                      @Param("from") LocalDateTime from, @Param("to") LocalDateTime to, Pageable page);

    @Query("select lower(i.name) as label, count(ri) as total from RecipeIngredient ri join ri.recipe r join ri.ingredient i "
            + "where r.origin = :origin and lower(i.name) in :labels and " + CLIENT_RECIPES + " group by lower(i.name)")
    List<LabelCount> ingredientCountsIn(@Param("origin") RecipeOrigin origin, @Param("labels") Collection<String> labels, @Param("role") Role role,
                                        @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select lower(r.name) as label, count(r) as total from Recipe r where " + CLIENT_RECIPES
            + " group by lower(r.name) order by count(r) desc")
    List<LabelCount> recipeNameCounts(@Param("role") Role role, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to, Pageable page);

    @Query("select lower(r.name) as label, count(r) as total from Recipe r where lower(r.name) in :labels and " + CLIENT_RECIPES
            + " group by lower(r.name)")
    List<LabelCount> recipeNameCountsIn(@Param("labels") Collection<String> labels, @Param("role") Role role,
                                        @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select c.name as label, count(r) as total from Recipe r join r.categories c where " + CLIENT_RECIPES + " group by c.name")
    List<LabelCount> categoryCounts(@Param("role") Role role, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select e.detail as label, count(e) as total from ProductEvent e where e.type = :type and e.detail is not null "
            + "and e.createdAt >= :from and e.createdAt < :to group by e.detail order by count(e) desc")
    List<LabelCount> eventDetailCounts(@Param("type") ProductEventType type, @Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to, Pageable page);

    @Query("select e.detail as label, count(e) as total from ProductEvent e where e.type = :type and e.detail in :labels "
            + "and e.createdAt >= :from and e.createdAt < :to group by e.detail")
    List<LabelCount> eventDetailCountsIn(@Param("type") ProductEventType type, @Param("labels") Collection<String> labels,
                                         @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select cast(e.createdAt as LocalDate) as day, count(e) as total from ProductEvent e where e.type = :type and e.detail = :detail "
            + "and e.createdAt >= :from and e.createdAt < :to group by cast(e.createdAt as LocalDate)")
    List<DayCount> eventDetailDaily(@Param("type") ProductEventType type, @Param("detail") String detail,
                                    @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
