package com.cooked.backend.repository;

import com.cooked.backend.entity.GroceryItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Grocery list aggregates for the admin Grocery analytics screen. */
public interface GroceryStatsRepository extends Repository<GroceryItem, UUID> {

    @Query("select count(g) from GroceryItem g where g.createdAt >= :from and g.createdAt < :to")
    long countAdded(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select count(distinct g.user.id) from GroceryItem g where g.createdAt >= :from and g.createdAt < :to")
    long countUsers(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select count(g) from GroceryItem g where g.createdAt >= :from and g.createdAt < :to and g.isBought = true")
    long countBought(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select count(g) from GroceryItem g where g.createdAt >= :from and g.createdAt < :to and g.recipe is not null")
    long countFromRecipes(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select count(distinct g.user.id) from GroceryItem g")
    long countUsersEver();

    interface DayCount {
        LocalDate getDay();
        Long getTotal();
    }

    @Query("select cast(g.createdAt as LocalDate) as day, count(g) as total from GroceryItem g where g.createdAt >= :from group by cast(g.createdAt as LocalDate)")
    List<DayCount> dailySince(@Param("from") LocalDateTime from);

    interface LabelCount {
        String getLabel();
        Long getTotal();
    }

    @Query("select g.ingredient.name as label, count(g) as total from GroceryItem g where g.createdAt >= :from group by g.ingredient.name order by count(g) desc")
    List<LabelCount> topIngredients(@Param("from") LocalDateTime from, Pageable page);

    interface RecipeCount {
        UUID getId();
        String getName();
        Long getTotal();
    }

    @Query("select g.recipe.id as id, g.recipe.name as name, count(g) as total from GroceryItem g where g.createdAt >= :from and g.recipe is not null "
            + "group by g.recipe.id, g.recipe.name order by count(g) desc")
    List<RecipeCount> topRecipes(@Param("from") LocalDateTime from, Pageable page);
}
