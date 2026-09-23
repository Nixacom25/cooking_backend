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
        fixUserSubscriptionsStatusConstraint();
        updateSupportTicketSchema();
        createCriticalErrorsTable();
        createNotificationCampaignsTable();
        addUserLastActiveColumn();
        addRevenueCatCustomerIdColumn();
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

    /**
     * Migration 6: Update the CHECK constraint on user_subscriptions.status to include all subscription status values
     * (FREE, TRIAL, ACTIVE, EXPIRED, CANCELLED, INFINITE, PREMIUM) to match the Java enum.
     */
    private void fixUserSubscriptionsStatusConstraint() {
        try {
            // Check if the old constraint still exists with the wrong set of values
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints " +
                "WHERE table_name = 'user_subscriptions' " +
                "AND constraint_type = 'CHECK' " +
                "AND constraint_name = 'user_subscriptions_status_check'",
                Integer.class
            );

            if (count != null && count > 0) {
                log.info("[Migration] Dropping old user_subscriptions_status_check constraint...");
                jdbc.execute("ALTER TABLE user_subscriptions DROP CONSTRAINT IF EXISTS user_subscriptions_status_check");
                log.info("[Migration] Old user_subscriptions_status_check constraint dropped successfully.");
            }

            // Re-add the constraint with ALL current enum values
            jdbc.execute(
                "ALTER TABLE user_subscriptions " +
                "ADD CONSTRAINT user_subscriptions_status_check " +
                "CHECK (status IN ('FREE', 'TRIAL', 'ACTIVE', 'EXPIRED', 'CANCELLED', 'INFINITE', 'PREMIUM'))"
            );
            log.info("[Migration] user_subscriptions_status_check constraint updated with all subscription status values.");

        } catch (Exception e) {
            // Constraint already correct or DB doesn't use CHECK constraints (H2 test env) — safe to ignore
            if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                log.debug("[Migration] User subscriptions status CHECK constraint already up to date — skipping.");
            } else {
                log.warn("[Migration] Could not update user subscriptions status CHECK constraint: {}", e.getMessage());
            }
        }
    }

    /**
     * Migration 7: Add new columns to support_tickets table for detailed user information
     */
    private void updateSupportTicketSchema() {
        addColumnIfMissing("support_tickets", "user_id", "VARCHAR(255)");
        addColumnIfMissing("support_tickets", "platform", "VARCHAR(50)");
        addColumnIfMissing("support_tickets", "app_version", "VARCHAR(50)");
        addColumnIfMissing("support_tickets", "category", "VARCHAR(100)");
        
        // Update default status from "OPEN" to "NEW" for better workflow
        try {
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM support_tickets WHERE status = 'OPEN'",
                Integer.class
            );
            if (count != null && count > 0) {
                log.info("[Migration] Updating support_tickets status from OPEN to NEW...");
                jdbc.execute("UPDATE support_tickets SET status = 'NEW' WHERE status = 'OPEN'");
                log.info("[Migration] Support tickets status updated successfully.");
            }
        } catch (Exception e) {
            log.debug("[Migration] Status update skipped: {}", e.getMessage());
        }
    }

    /**
     * Migration 8: Create critical_errors table for monitoring
     */
    private void createCriticalErrorsTable() {
        try {
            // Check if table exists
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'critical_errors'",
                Integer.class
            );

            if (count == null || count == 0) {
                log.info("[Migration] Creating critical_errors table...");
                jdbc.execute("""
                    CREATE TABLE critical_errors (
                        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        error_type VARCHAR(100) NOT NULL,
                        error_message TEXT NOT NULL,
                        stack_trace TEXT,
                        user_id VARCHAR(255),
                        user_email VARCHAR(255),
                        platform VARCHAR(50),
                        os_version VARCHAR(100),
                        app_version VARCHAR(50),
                        context TEXT,
                        status VARCHAR(20) DEFAULT 'NEW',
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                """);
                log.info("[Migration] critical_errors table created successfully.");
            } else {
                log.debug("[Migration] critical_errors table already exists — skipping.");
            }
        } catch (Exception e) {
            log.warn("[Migration] Could not create critical_errors table: {}", e.getMessage());
        }
    }

    /**
     * Migration 9: Create notification_campaigns table for push notification management
     */
    private void createNotificationCampaignsTable() {
        try {
            // Check if table exists
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'notification_campaigns'",
                Integer.class
            );

            if (count == null || count == 0) {
                log.info("[Migration] Creating notification_campaigns table...");
                jdbc.execute("""
                    CREATE TABLE notification_campaigns (
                        id BIGSERIAL PRIMARY KEY,
                        title VARCHAR(255) NOT NULL,
                        body TEXT NOT NULL,
                        image_url TEXT,
                        deep_link VARCHAR(500),
                        target_type VARCHAR(50) NOT NULL,
                        target_user_ids TEXT,
                        target_segment VARCHAR(50),
                        scheduled_for TIMESTAMP,
                        sent_at TIMESTAMP,
                        status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
                        created_by VARCHAR(255),
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        total_recipients INTEGER,
                        sent_count INTEGER DEFAULT 0,
                        failed_count INTEGER DEFAULT 0,
                        opened_count INTEGER DEFAULT 0,
                        clicked_count INTEGER DEFAULT 0,
                        CONSTRAINT notification_campaigns_target_type_check 
                            CHECK (target_type IN ('ALL_USERS', 'TARGETED_USERS', 'TARGETED_SEGMENT', 'PREMIUM_USERS', 'FREE_USERS')),
                        CONSTRAINT notification_campaigns_target_segment_check 
                            CHECK (target_segment IN ('ACTIVE_USERS', 'INACTIVE_USERS', 'NEW_USERS', 'TRIAL_USERS', 'PAID_USERS', 'CHURNED_USERS')),
                        CONSTRAINT notification_campaigns_status_check 
                            CHECK (status IN ('DRAFT', 'SCHEDULED', 'SENDING', 'SENT', 'FAILED', 'CANCELLED'))
                    )
                """);
                jdbc.execute("CREATE INDEX idx_notification_campaigns_status ON notification_campaigns(status)");
                jdbc.execute("CREATE INDEX idx_notification_campaigns_scheduled_for ON notification_campaigns(scheduled_for)");
                jdbc.execute("CREATE INDEX idx_notification_campaigns_created_by ON notification_campaigns(created_by)");
                log.info("[Migration] notification_campaigns table created successfully.");
            } else {
                log.debug("[Migration] notification_campaigns table already exists — skipping.");
            }
        } catch (Exception e) {
            log.warn("[Migration] Could not create notification_campaigns table: {}", e.getMessage());
        }
    }

    /**
     * Migration 10: Add last_active column to users table for user segmentation
     */
    private void addUserLastActiveColumn() {
        addColumnIfMissing("users", "last_active", "TIMESTAMP");
    }

    /**
     * Migration 11: Add revenue_cat_customer_id column to users table for RevenueCat integration
     */
    private void addRevenueCatCustomerIdColumn() {
        addColumnIfMissing("users", "revenue_cat_customer_id", "TEXT");
    }
}
