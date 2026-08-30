import express from "express";
import { scanIngredients, generateImageHandler } from "../controllers/image.controller.js";
import { upload } from "../middleware/multer.js";

const router = express.Router();

router.post(
  "/",
  upload.any(),
  scanIngredients
);

router.post(
  "/generate",
  generateImageHandler
);

export default router;

