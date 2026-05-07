-- Delete related data first to avoid foreign key violations
DELETE FROM recipe_steps WHERE recipe_id IN (SELECT id FROM recipes WHERE origin = 'EXPLORE');
DELETE FROM recipe_equipment WHERE recipe_id IN (SELECT id FROM recipes WHERE origin = 'EXPLORE');
DELETE FROM recipe_ingredients WHERE recipe_id IN (SELECT id FROM recipes WHERE origin = 'EXPLORE');
DELETE FROM cookbook_recipes WHERE recipe_id IN (SELECT id FROM recipes WHERE origin = 'EXPLORE');
DELETE FROM favorite_recipes WHERE recipe_id IN (SELECT id FROM recipes WHERE origin = 'EXPLORE');
DELETE FROM meal_plans WHERE recipe_id IN (SELECT id FROM recipes WHERE origin = 'EXPLORE');
DELETE FROM grocery_items WHERE recipe_id IN (SELECT id FROM recipes WHERE origin = 'EXPLORE');

-- Finally delete the recipes themselves
DELETE FROM recipes WHERE origin = 'EXPLORE';
