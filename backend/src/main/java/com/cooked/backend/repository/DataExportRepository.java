package com.cooked.backend.repository;

import com.cooked.backend.entity.DataExport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Data exports and the paged reads that build them (projections only: no
 * entity graphs, no secrets such as password hashes or tokens).
 */
@Repository
public interface DataExportRepository extends JpaRepository<DataExport, UUID> {

    Page<DataExport> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<DataExport> findAllByStatusAndExpiresAtBefore(DataExport.Status status, LocalDateTime at);

    // --- row counts for the period ------------------------------------------------
    @Query("select count(u) from User u where u.createdAt >= :from and u.createdAt < :to")
    long countUsers(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select count(r) from Recipe r where r.createdAt >= :from and r.createdAt < :to")
    long countRecipes(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select count(p) from SubscriptionPayment p where p.createdAt >= :from and p.createdAt < :to")
    long countPayments(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select count(d) from ProviderDailyCost d where d.costDay >= :from and d.costDay < :to")
    long countProviderCosts(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select count(e) from CostEntry e where e.startDate < :to and (e.endDate is null or e.endDate >= :from)")
    long countManualCosts(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select count(a) from ActivityLog a where a.createdAt >= :from and a.createdAt < :to")
    long countAudit(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // --- pages of rows ----------------------------------------------------------------
    @Query("select u.id, u.email, u.firstname, u.lastname, u.role, u.status, u.subscriptionStatus, u.subscriptionType, "
            + "u.subscriptionExpiresAt, u.country, u.language, u.discoverySource, u.createdAt, u.lastActive "
            + "from User u where u.createdAt >= :from and u.createdAt < :to order by u.createdAt, u.id")
    List<Object[]> users(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, Pageable page);

    @Query("select r.id, r.name, r.origin, c.name, r.prepTime, r.cookTime, r.servings, r.kcal, r.status, r.isPublic, "
            + "r.isDeleted, r.sourceUrl, r.createdAt from Recipe r left join r.cuisine c "
            + "where r.createdAt >= :from and r.createdAt < :to order by r.createdAt, r.id")
    List<Object[]> recipes(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, Pageable page);

    @Query("select ri.recipe.id, ri.quantity, i.name from RecipeIngredient ri join ri.ingredient i where ri.recipe.id in :ids")
    List<Object[]> recipeIngredients(@Param("ids") Collection<UUID> ids);

    @Query("select r.id, s from Recipe r join r.steps s where r.id in :ids")
    List<Object[]> recipeSteps(@Param("ids") Collection<UUID> ids);

    @Query("select p.id, p.createdAt, u.email, p.store, p.planType, p.amount, p.status, p.stripePaymentId "
            + "from SubscriptionPayment p join p.user u where p.createdAt >= :from and p.createdAt < :to order by p.createdAt, p.id")
    List<Object[]> payments(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, Pageable page);

    @Query("select d.costDay, d.provider, d.lineItem, d.amountUsd from ProviderDailyCost d "
            + "where d.costDay >= :from and d.costDay < :to order by d.costDay, d.provider, d.id")
    List<Object[]> providerCosts(@Param("from") LocalDate from, @Param("to") LocalDate to, Pageable page);

    @Query("select e.startDate, e.endDate, e.provider, e.category, e.frequency, e.amount, e.currency, e.amountUsd, e.notes "
            + "from CostEntry e where e.startDate < :to and (e.endDate is null or e.endDate >= :from) order by e.startDate, e.id")
    List<Object[]> manualCosts(@Param("from") LocalDate from, @Param("to") LocalDate to, Pageable page);

    @Query("select a.createdAt, u.email, a.title, a.message, a.entityType, a.entityId from ActivityLog a left join a.user u "
            + "where a.createdAt >= :from and a.createdAt < :to order by a.createdAt, a.id")
    List<Object[]> audit(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, Pageable page);
}
