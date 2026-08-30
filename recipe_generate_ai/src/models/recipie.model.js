import mongoose from "mongoose";

const timeAndServingsSchema = new mongoose.Schema(
    {
        prep_time: { type: String, default: "" },
        cook_time: { type: String, default: "" },
        total_time: { type: String, default: "" },
        servings: { type: String, default: "" },
    },
    { _id: false }
);

const ingredientSchema = new mongoose.Schema(
    {
        name: { type: String, required: true },
        quantity: { type: String, default: "" },
        unit: { type: String, default: "" },
        note: { type: String, default: "" },
    },
    { _id: false }
);

const metadataSchema = new mongoose.Schema(
    {
        cuisine: { type: String, default: "" },
        meal_type: { type: String, default: "" },
        diet_type: { type: String, default: "" },
    },
    { _id: false }
);

const recipeDetailsSchema = new mongoose.Schema(
    {
        title: { type: String, default: "" },
        description: { type: String, default: "" },
        time_and_servings: { type: timeAndServingsSchema, default: () => ({}) },
        ingredients: { type: [ingredientSchema], default: [] },
        instructions: { type: [String], default: [] },
        metadata: { type: metadataSchema, default: () => ({}) },
    },
    { _id: false }
);

const recipePayloadSchema = new mongoose.Schema(
    {
        status: { type: String, enum: ["success", "failure"], required: true },
        chef_persona: { type: String, default: "" },
        recipe: { type: recipeDetailsSchema, default: null },
        fallback_message: { type: String, default: "" },
    },
    { _id: false }
);

const recipieSchema = new mongoose.Schema(
    {
        source: { type: String, required: true, trim: true },
        image: { type: String, default: null },
        url: { type: String, required: true, trim: true },
        recipe: { type: recipePayloadSchema, required: true },
    },
    { timestamps: true }
);

export const Recipie = mongoose.model("Recipie", recipieSchema);
