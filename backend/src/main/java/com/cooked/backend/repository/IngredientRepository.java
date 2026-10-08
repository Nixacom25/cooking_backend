package com.cooked.backend.repository;

import com.cooked.backend.entity.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IngredientRepository extends JpaRepository<Ingredient, UUID> {
    Optional<Ingredient> findFirstByName(String name);
    
    default Optional<Ingredient> findByName(String name) {
        return findFirstByName(name);
    }
    
    java.util.List<com.cooked.backend.entity.Ingredient> findByNameContainingIgnoreCase(String query);
    java.util.List<Ingredient> findByPriceIsNull();

    /** Every ingredient name with the number of recipes using it, most used first: rows [name, recipes]. */
    @org.springframework.data.jpa.repository.Query("select i.name, count(ri.id) from Ingredient i "
            + "left join RecipeIngredient ri on ri.ingredient = i group by i.name order by count(ri.id) desc, i.name")
    java.util.List<Object[]> namesByUsage();
}
