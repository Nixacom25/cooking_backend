import express from "express";
import {
  generateRecipesHandler,
  suggestRecipesHandler,
  getTrendingDishesHandler,
} from "../controllers/recipe.controller.js";

const router = express.Router();

router.post("/generate", generateRecipesHandler);
router.post("/suggest", suggestRecipesHandler);
router.get("/trending", getTrendingDishesHandler);

export default router;
