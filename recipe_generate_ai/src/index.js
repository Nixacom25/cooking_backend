import dotenv from "dotenv";
dotenv.config();

import { connectToDatabase } from "./config/db.js";
import { app } from "./app.js";

const PORT = process.env.PORT || 3000;

const start = async () => {
    await connectToDatabase();
    app.listen(PORT, () => {
        console.log(`✅ Server is running at port ${PORT}`);
    });
};
start().catch((error) => {
    console.error("❌ Failed to start server:", error);
    process.exit(1);
});