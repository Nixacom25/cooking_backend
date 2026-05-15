
const cloudinary = require('cloudinary').v2;
const fs = require('fs');
const path = require('path');

// Configuration
cloudinary.config({
  cloud_name: 'davj7mdjj',
  api_key: '979485862516478',
  api_secret: 'qZHkaX0of_imikGcFUdUSK_7l4g'
});

const folder = 'ai-recipe-app/taxonomy';

const categories = [
  { name: 'High Protein Picks', local: '/home/ousseynou_diedhiou/Bureau/Nixacom/cooked/mobile/assets/images/higth-proteins.png' },
  { name: 'Easy Desserts', local: '/home/ousseynou_diedhiou/Bureau/Nixacom/cooked/mobile/assets/images/easy-desserts.png' },
  { name: '30-Minute Meals', local: '/home/ousseynou_diedhiou/Bureau/Nixacom/cooked/mobile/assets/images/30-Minutes.png' },
  { name: 'Healthy Breakfasts', local: '/home/ousseynou_diedhiou/Bureau/Nixacom/cooked/mobile/assets/images/explore_summer.png' },
  { name: 'Plant-Based Essentials', local: '/home/ousseynou_diedhiou/Bureau/Nixacom/cooked/mobile/assets/images/Plant-Based.png' },
  { name: 'Low-Carb Meals', local: '/home/ousseynou_diedhiou/Bureau/Nixacom/cooked/mobile/assets/images/low-cards.png' },
  { name: 'Bowls & Salads', generated: '/home/ousseynou_diedhiou/.gemini/antigravity/brain/49eaf70e-f79d-48e0-82a7-ac5b9edfd5ae/category_bowls_salads_1778825146258.png' },
  { name: 'Desserts', generated: '/home/ousseynou_diedhiou/.gemini/antigravity/brain/49eaf70e-f79d-48e0-82a7-ac5b9edfd5ae/category_desserts_1778825159064.png' },
  { name: 'Pasta & Noodles', generated: '/home/ousseynou_diedhiou/.gemini/antigravity/brain/49eaf70e-f79d-48e0-82a7-ac5b9edfd5ae/category_pasta_noodles_1778825174579.png' },
  { name: 'Pizza & Flatbreads', generated: '/home/ousseynou_diedhiou/.gemini/antigravity/brain/49eaf70e-f79d-48e0-82a7-ac5b9edfd5ae/category_pizza_flatbreads_1778825191800.png' },
  { name: 'Curries & Stews', generated: '/home/ousseynou_diedhiou/.gemini/antigravity/brain/49eaf70e-f79d-48e0-82a7-ac5b9edfd5ae/category_curries_stews_1778825248876.png' },
  { name: 'Handheld / Street Food', generated: '/home/ousseynou_diedhiou/.gemini/antigravity/brain/49eaf70e-f79d-48e0-82a7-ac5b9edfd5ae/category_street_food_1778825261748.png' },
  { name: 'Soups', generated: '/home/ousseynou_diedhiou/.gemini/antigravity/brain/49eaf70e-f79d-48e0-82a7-ac5b9edfd5ae/category_soups_1778825276904.png' },
  { name: 'Rice & Grain', generated: '/home/ousseynou_diedhiou/.gemini/antigravity/brain/49eaf70e-f79d-48e0-82a7-ac5b9edfd5ae/category_rice_grain_1778825590577.png' },
  { name: 'Stir Fry', generated: '/home/ousseynou_diedhiou/.gemini/antigravity/brain/49eaf70e-f79d-48e0-82a7-ac5b9edfd5ae/category_stir_fry_1778825606515.png' }
];

const cuisines = [
  { name: 'Italy', local: '/home/ousseynou_diedhiou/Bureau/Nixacom/cooked/mobile/assets/images/italian.png' },
  { name: 'Mexico', local: '/home/ousseynou_diedhiou/Bureau/Nixacom/cooked/mobile/assets/images/mexican1.png' },
  { name: 'China', local: '/home/ousseynou_diedhiou/Bureau/Nixacom/cooked/mobile/assets/images/chinese.png' },
  { name: 'Japan', local: '/home/ousseynou_diedhiou/Bureau/Nixacom/cooked/mobile/assets/images/japanese.png' },
  { name: 'Thailand', local: '/home/ousseynou_diedhiou/Bureau/Nixacom/cooked/mobile/assets/images/thai.png' },
  { name: 'India', local: '/home/ousseynou_diedhiou/Bureau/Nixacom/cooked/mobile/assets/images/indian.png' },
  { name: 'South Korea', local: '/home/ousseynou_diedhiou/Bureau/Nixacom/cooked/mobile/assets/images/korean.png' },
  { name: 'Mediterranean', local: '/home/ousseynou_diedhiou/Bureau/Nixacom/cooked/mobile/assets/images/mediterranean.png' },
  { name: 'Middle East', local: '/home/ousseynou_diedhiou/Bureau/Nixacom/cooked/mobile/assets/images/east.png' },
  { name: 'France', local: '/home/ousseynou_diedhiou/Bureau/Nixacom/cooked/mobile/assets/images/french1.png' },
  { name: 'Greece', local: '/home/ousseynou_diedhiou/Bureau/Nixacom/cooked/mobile/assets/images/greek1.png' },
  { name: 'Caribbean', local: '/home/ousseynou_diedhiou/Bureau/Nixacom/cooked/mobile/assets/images/caribbean1.png' },
  { name: 'West Africa', local: '/home/ousseynou_diedhiou/Bureau/Nixacom/cooked/mobile/assets/images/west-african.png' },
  { name: 'American', generated: '/home/ousseynou_diedhiou/.gemini/antigravity/brain/49eaf70e-f79d-48e0-82a7-ac5b9edfd5ae/cuisine_american_food_1778825289941.png' },
  { name: 'Senegalese', generated: '/home/ousseynou_diedhiou/.gemini/antigravity/brain/49eaf70e-f79d-48e0-82a7-ac5b9edfd5ae/cuisine_senegalese_food_1778825205603.png' },
  { name: 'Vietnamese', generated: '/home/ousseynou_diedhiou/.gemini/antigravity/brain/49eaf70e-f79d-48e0-82a7-ac5b9edfd5ae/cuisine_vietnamese_food_1778825217990.png' },
  { name: 'Moroccan', generated: '/home/ousseynou_diedhiou/.gemini/antigravity/brain/49eaf70e-f79d-48e0-82a7-ac5b9edfd5ae/cuisine_moroccan_food_1778825307105.png' },
  { name: 'Asian Fusion', generated: '/home/ousseynou_diedhiou/.gemini/antigravity/brain/49eaf70e-f79d-48e0-82a7-ac5b9edfd5ae/cuisine_asian_fusion_1778825618550.png' },
  { name: 'Brazilian', generated: '/home/ousseynou_diedhiou/.gemini/antigravity/brain/49eaf70e-f79d-48e0-82a7-ac5b9edfd5ae/cuisine_brazilian_food_1778825632386.png' },
  { name: 'British', generated: '/home/ousseynou_diedhiou/.gemini/antigravity/brain/49eaf70e-f79d-48e0-82a7-ac5b9edfd5ae/cuisine_british_food_1778825646645.png' }
];

async function uploadAll() {
  const results = {
    categories: {},
    cuisines: {}
  };

  console.log('Starting upload to Cloudinary...');

  for (const item of categories) {
    const filePath = item.local || item.generated;
    if (fs.existsSync(filePath)) {
      try {
        const res = await cloudinary.uploader.upload(filePath, { folder });
        results.categories[item.name] = res.secure_url;
        console.log(`Uploaded Category: ${item.name} -> ${res.secure_url}`);
      } catch (err) {
        console.error(`Failed to upload ${item.name}:`, err.message);
      }
    } else {
      console.warn(`File not found for ${item.name}: ${filePath}`);
    }
  }

  for (const item of cuisines) {
    const filePath = item.local || item.generated;
    if (fs.existsSync(filePath)) {
      try {
        const res = await cloudinary.uploader.upload(filePath, { folder });
        results.cuisines[item.name] = res.secure_url;
        console.log(`Uploaded Cuisine: ${item.name} -> ${res.secure_url}`);
      } catch (err) {
        console.error(`Failed to upload ${item.name}:`, err.message);
      }
    } else {
      console.warn(`File not found for ${item.name}: ${filePath}`);
    }
  }

  const outputPath = '/home/ousseynou_diedhiou/Bureau/Nixacom/cooked/cooked backend/backend/src/main/resources/taxonomy_images.json';
  fs.writeFileSync(outputPath, JSON.stringify(results, null, 2));
  console.log(`Results saved to ${outputPath}`);
}

uploadAll();
