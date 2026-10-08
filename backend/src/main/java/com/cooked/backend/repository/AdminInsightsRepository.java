package com.cooked.backend.repository;

import com.cooked.backend.entity.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Read-only aggregates for the admin tables (batch per page of ids). Rows are [key, count]. */
public interface AdminInsightsRepository extends Repository<User, UUID> {

    /** [discoverySource, subscriptionStatus, user id] of clients who signed up since [from]. */
    @Query("select u.discoverySource, u.subscriptionStatus, u.id from User u where u.role = com.cooked.backend.entity.Role.CLIENT and u.createdAt >= :from")
    List<Object[]> signupsSince(@Param("from") LocalDateTime from);

    /** [user id, successful payments total]. */
    @Query("select p.user.id, sum(p.amount) from SubscriptionPayment p where p.user.id in :ids and upper(p.status) = 'SUCCESS' group by p.user.id")
    List<Object[]> revenueOf(@Param("ids") Collection<UUID> ids);

    /** [creator id, distinct other users keeping their public recipes in a cookbook]. */
    @Query("select r.user.id, count(distinct c.user.id) from Cookbook c join c.recipes r where r.user.id in :ids and r.isPublic = true and c.user.id <> r.user.id group by r.user.id")
    List<Object[]> saversOf(@Param("ids") Collection<UUID> ids);

    /** [creator id, recipe-screen views of their public recipes since from]. */
    @Query("select r.user.id, count(e) from ProductEvent e, Recipe r where e.type = com.cooked.backend.entity.ProductEventType.RECIPE_VIEW "
            + "and e.createdAt >= :from and e.detail = cast(r.id as string) and r.user.id in :ids and r.isPublic = true group by r.user.id")
    List<Object[]> creatorViewsSince(@Param("ids") Collection<UUID> ids, @Param("from") LocalDateTime from);

    /** [recipe id as text, views since from]. */
    @Query("select e.detail, count(e) from ProductEvent e where e.type = com.cooked.backend.entity.ProductEventType.RECIPE_VIEW "
            + "and e.detail in :ids and e.createdAt >= :from group by e.detail")
    List<Object[]> recipeViewsSince(@Param("ids") Collection<String> ids, @Param("from") LocalDateTime from);

    @Query("select r.id, count(c) from Cookbook c join c.recipes r where r.id in :ids group by r.id")
    List<Object[]> cookbooksOf(@Param("ids") Collection<UUID> ids);

    @Query("select m.recipe.id, count(m) from MealPlan m where m.recipe.id in :ids group by m.recipe.id")
    List<Object[]> mealPlansOf(@Param("ids") Collection<UUID> ids);

    /** [blog path, distinct visitors since from]. */
    @Query("select v.path, count(distinct v.visitor) from SiteVisit v where v.day >= :from and v.path like '/blog/%' group by v.path")
    List<Object[]> blogVisitorsSince(@Param("from") LocalDate from);

    /** [event type, distinct paying users who used it since from]. */
    @Query("select e.type, count(distinct e.userId) from ProductEvent e, User u where u.id = e.userId and e.createdAt >= :from "
            + "and u.subscriptionStatus in (com.cooked.backend.entity.SubscriptionStatus.ACTIVE, com.cooked.backend.entity.SubscriptionStatus.PREMIUM) group by e.type")
    List<Object[]> payingUsersByTypeSince(@Param("from") LocalDateTime from);

    @Query("select count(g) from GroceryItem g where g.user.id = :id")
    long groceryAddsOf(@Param("id") UUID userId);

    @Query("select count(e) from ProductEvent e where e.userId = :id and e.type = com.cooked.backend.entity.ProductEventType.WEB_SEARCH")
    long searchesOf(@Param("id") UUID userId);
}
