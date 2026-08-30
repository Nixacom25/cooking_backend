import express from "express";
import { main } from "../controllers/main.controller.js";

const router = express.Router();

/**
 * @swagger
 * /api/main:
 *   post:
 *     summary: Extract recipe from a URL
 *     description: Accepts a social media or web URL and returns parsed recipe data.
 *     tags:
 *       - Main
 *     operationId: extractRecipe
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - url
 *             properties:
 *               url:
 *                 type: string
 *                 format: uri
 *                 description: Source content URL from YouTube, TikTok, Instagram, or a website.
 *                 example: https://youtu.be/RaLzxZryEoA?si=uU0mXBgki2MjvtgS
 *           example:
 *             url: https://youtu.be/RaLzxZryEoA?si=uU0mXBgki2MjvtgS
 *     responses:
 *       200:
 *         description: Recipe extracted successfully
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 statusCode:
 *                   type: integer
 *                   example: 200
 *                 data:
 *                   type: object
 *                   properties:
 *                     source:
 *                       type: string
 *                       example: youtube
 *                     image:
 *                       type: string
 *                       nullable: true
 *                       example: https://i.ytimg.com/vi/abc123/maxresdefault.jpg
 *                     recipe:
 *                       type: object
 *                       description: Parsed recipe object returned by AI service.
 *                 message:
 *                   type: string
 *                   example: Main info fetched
 *                 success:
 *                   type: boolean
 *                   example: true
 *       400:
 *         description: Missing url in request body
 *       422:
 *         description: No description available to extract recipe
 *       500:
 *         description: Internal server error
 */
router.post(
  "/",
  main
);


export default router;