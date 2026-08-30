import mongoose from "mongoose";

let cachedConnection = null;

export const connectToDatabase = async () => {
  if (cachedConnection) {
    return cachedConnection;
  }

  const mongoUri = process.env.MONGODB_URI;
  if (!mongoUri) {
    console.warn("⚠️ MONGODB_URI is not set. Database logging is disabled.");
    return null;
  }

  try {
    cachedConnection = await mongoose.connect(mongoUri, {
      serverSelectionTimeoutMS: 5000,
    });
    console.log("✅ Connected to MongoDB Atlas");
    return cachedConnection;
  } catch (error) {
    console.warn("⚠️ MongoDB Atlas connection failed (IP not whitelisted or network issue). App server is continuing in fallback mode.");
    return null;
  }
};
