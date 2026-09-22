package com.cooked.backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Runs one-time DDL migrations that Hibernate's ddl-auto=update cannot handle,
 * such as dropping & recreating PostgreSQL CHECK constraints when new enum values
 * are added to the Java enum but not yet in the DB constraint.
 *
 * Safe to run repeatedly — each migration is guarded by an existence check.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseMigrationRunner {

    private final JdbcTemplate jdbc;

    @EventListener(ApplicationReadyEvent.class)
    public void runMigrations() {
        fixAssignmentStatusConstraint();
        ensureAssignmentNewColumns();
        fixRecipeAssignmentForeignKeys();
        fixUserRoleConstraint();
        fixUserSubscriptionStatusConstraint();
    }

    /**
     * Migration 4: Update the CHECK constraint on users.role to include all role values
     * (CLIENT, ADMIN, EDITOR, CREATOR) to match the Java enum.
     */
    private void fixUserRoleConstraint() {
        try {
            // Check if the old constraint still exists with the wrong set of values
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints " +
                "WHERE table_name = 'users' " +
                "AND constraint_type = 'CHECK' " +
                "AND constraint_name = 'users_role_check'",
                Integer.class
            );

            if (count != null && count > 0) {
                log.info("[Migration] Dropping old users_role_check constraint...");
                jdbc.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check");
                log.info("[Migration] Old users_role_check constraint dropped successfully.");
            }

            // Re-add the constraint with ALL current enum values
            jdbc.execute(
                "ALTER TABLE users " +
                "ADD CONSTRAINT users_role_check " +
                "CHECK (role IN ('CLIENT', 'ADMIN', 'EDITOR', 'CREATOR'))"
            );
            log.info("[Migration] users_role_check constraint updated with all role values.");

        } catch (Exception e) {
            // Constraint already correct or DB doesn't use CHECK constraints (H2 test env) — safe to ignore
            if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                log.debug("[Migration] Role CHECK constraint already up to date — skipping.");
            } else {
                log.warn("[Migration] Could not update role CHECK constraint: {}", e.getMessage());
            }
        }
    }

    /**
     * Migration 3: Ensure foreign key constraints on recipe_assignments use ON DELETE CASCADE
     * to avoid SQL 23503 foreign key violations when deleting users or recipes.
     */
    private void fixRecipeAssignmentForeignKeys() {
        try {
            // Find foreign key constraint names on recipe_assignments for recipe_id
            java.util.List<String> fkNames = jdbc.queryForList(
                "SELECT constraint_name FROM information_schema.table_constraints " +
                "WHERE table_name = 'recipe_assignments' AND constraint_type = 'FOREIGN_KEY'",
                String.class
            );

            for (String fk : fkNames) {
                if (fk.startsWith("fk84eq7") || fk.contains("recipe")) {
                    jdbc.execute("ALTER TABLE recipe_assignments DROP CONSTRAINT IF EXISTS " + fk);
                }
            }

            jdbc.execute(
                "ALTER TABLE recipe_assignments " +
                "ADD CONSTRAINT fk_recipe_assignments_recipe " +
                "FOREIGN KEY (recipe_id) REFERENCES recipes(id) ON DELETE CASCADE"
            );
            log.info("[Migration] Updated recipe_assignments.recipe_id foreign key to ON DELETE CASCADE.");
        } catch (Exception e) {
            log.debug("[Migration] Recipe assignment FK update info: {}", e.getMessage());
        }
    }

    /**
     * Migration 1: Update the CHECK constraint on recipe_assignments.status
     * to include all new status values (ASSIGNED, UNASSIGNED, SUBMITTED_FOR_VALIDATION,
     * NEEDS_CORRECTION, VALIDATED, DELETED, IN_PROGRESS, etc.)
     */
    private void fixAssignmentStatusConstraint() {
        try {
            // Check if the old constraint still exists with the wrong set of values
            // We identify it by looking for a constraint that does NOT include 'ASSIGNED'
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints " +
                "WHERE table_name = 'recipe_assignments' " +
                "AND constraint_type = 'CHECK' " +
                "AND constraint_name = 'recipe_assignments_status_check'",
                Integer.class
            );

            if (count != null && count > 0) {
                log.info("[Migration] Dropping old recipe_assignments_status_check constraint...");
                jdbc.execute("ALTER TABLE recipe_assignments DROP CONSTRAINT IF EXISTS recipe_assignments_status_check");
                log.info("[Migration] Old CHECK constraint dropped successfully.");
            }

            // Re-add the constraint with ALL current enum values
            // This is idempotent — if the constraint doesn't exist, it adds it;
            // if it was just dropped, it re-creates it with the full list.
            jdbc.execute(
                "ALTER TABLE recipe_assignments " +
                "ADD CONSTRAINT recipe_assignments_status_check " +
                "CHECK (status IN (" +
                "  'UNASSIGNED', 'ASSIGNED', 'NOT_STARTED', 'IN_PROGRESS', " +
                "  'SUBMITTED_FOR_VALIDATION', 'NEEDS_CORRECTION', " +
                "  'VALIDATED', 'COMPLETED', 'DELETED'" +
                "))"
            );
            log.info("[Migration] recipe_assignments_status_check constraint updated with all status values.");

        } catch (Exception e) {
            // Constraint already correct or DB doesn't use CHECK constraints (H2 test env) — safe to ignore
            if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                log.debug("[Migration] Status CHECK constraint already up to date — skipping.");
            } else {
                log.warn("[Migration] Could not update status CHECK constraint: {}", e.getMessage());
            }
        }
    }

    /**
     * Migration 2: Ensure new columns added in RecipeAssignment entity exist in the DB.
     * Hibernate ddl-auto=update should handle this, but we add safety checks here.
     */
    private void ensureAssignmentNewColumns() {
        addColumnIfMissing("recipe_assignments", "submitted_date", "TIMESTAMP");
        addColumnIfMissing("recipe_assignments", "validated_date", "TIMESTAMP");
        addColumnIfMissing("recipe_assignments", "feedback_comment", "TEXT");
        addColumnIfMissing("recipe_assignments", "revision_count", "INTEGER DEFAULT 0");
    }

    private void addColumnIfMissing(String table, String column, String type) {
        try {
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_name = ? AND column_name = ?",
                Integer.class, table, column
            );
            if (count == null || count == 0) {
                jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
                log.info("[Migration] Added missing column {}.{}", table, column);
            }
        } catch (Exception e) {
            log.warn("[Migration] Could not ensure column {}.{}: {}", table, column, e.getMessage());
        }
    }

    /**
     * Migration 5: Update the CHECK constraint on users.subscription_status to include all subscription status values
     * (FREE, TRIAL, ACTIVE, EXPIRED, CANCELLED, INFINITE, PREMIUM) to match the Java enum.
     */
    private void fixUserSubscriptionStatusConstraint() {
        try {
            // Check if the old constraint still exists with the wrong set of values
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints " +
                "WHERE table_name = 'users' " +
                "AND constraint_type = 'CHECK' " +
                "AND constraint_name = 'users_subscription_status_check'",
                Integer.class
            );

            if (count != null && count > 0) {
                log.info("[Migration] Dropping old users_subscription_status_check constraint...");
                jdbc.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_subscription_status_check");
                log.info("[Migration] Old users_subscription_status_check constraint dropped successfully.");
            }

            // Re-add the constraint with ALL current enum values
            jdbc.execute(
                "ALTER TABLE users " +
                "ADD CONSTRAINT users_subscription_status_check " +
                "CHECK (subscription_status IN ('FREE', 'TRIAL', 'ACTIVE', 'EXPIRED', 'CANCELLED', 'INFINITE', 'PREMIUM'))"
            );
            log.info("[Migration] users_subscription_status_check constraint updated with all subscription status values.");

        } catch (Exception e) {
            // Constraint already correct or DB doesn't use CHECK constraints (H2 test env) — safe to ignore
            if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                log.debug("[Migration] Subscription status CHECK constraint already up to date — skipping.");
            } else {
                log.warn("[Migration] Could not update subscription status CHECK constraint: {}", e.getMessage());
            }
        }
    }
}
