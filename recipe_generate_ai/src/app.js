import express from "express";
import cors from "cors";
import cookieParser from "cookie-parser";
import path from "path";
import { fileURLToPath } from "url";
import helmet from "helmet";
import swaggerUi from "swagger-ui-express";
import swaggerDocument from "./swagger.js";
import { ApiError } from "./utils/ApiError.js";

// Fix __dirname in ES modules
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();

// ✅ Middlewares
app.use(
  cors({
    origin: process.env.CORS_ORIGIN || "*", // fallback to avoid issues
    credentials: true,
  })
);

app.use(
  helmet({
    contentSecurityPolicy: false,
    crossOriginEmbedderPolicy: false,
  })
);

app.use(express.json({ limit: "10mb" }));
app.use(express.urlencoded({ extended: true, limit: "10mb" }));
app.use(express.static(path.join(__dirname, "public")));
app.use(cookieParser());

// ✅ Test route (IMPORTANT to prevent empty app confusion)
app.get("/", (req, res) => {
  res.send("API is running 🚀");
});

// ✅ Swagger Documentation
app.use(
  "/api/docs",
  swaggerUi.serve,
  swaggerUi.setup(swaggerDocument, {
    swaggerOptions: {
      url: "/api/docs.json",
    },
  })
);

app.get("/api/docs.json", (req, res) => {
  res.setHeader("Content-Type", "application/json");
  res.send(swaggerDocument);
});

// ✅ Routes
import {
  userRoutes,
  mainRoutes,
  imageRoutes,
  recipeRoutes,
} from "./routes/index.js";

app.use("/api/users", userRoutes);
app.use("/api/main", mainRoutes);
app.use("/api/extract", mainRoutes);
app.use("/api/image", imageRoutes);
app.use("/api/images", imageRoutes);
app.use("/api/analyze", imageRoutes);
app.use("/api/recipe", recipeRoutes);
app.use("/api/recipes", recipeRoutes);



// ✅ Health Check Route (For Docker/K8s/Load Balancers)
app.get("/health", (req, res) => {
  res.status(200).json({
    status: "UP",
    service: "recipe_generate_ai",
    timestamp: new Date().toISOString(),
  });
});

app.use((err, req, res, next) => {
  const statusCode = err instanceof ApiError ? err.statusCode : err.status || 500;
  let message = err.message || "Internal Server Error";

  // Security: Mask OpenAI secrets or internal paths in production errors
  if (message.includes("sk-") || message.includes("OPENAI_API_KEY")) {
    message = "AI service communication error";
  }

  return res.status(statusCode).json({
    statusCode,
    success: false,
    message,
    errors: err.errors || [],
  });
});

// ✅ Global error handlers
process.on("unhandledRejection", (reason, promise) => {
  console.error("❌ Unhandled Rejection:", reason);
});

process.on("uncaughtException", (err) => {
  console.error("❌ Uncaught Exception:", err);
  process.exit(1);
});

// ✅ Export app (DO NOT start server here)
export { app };