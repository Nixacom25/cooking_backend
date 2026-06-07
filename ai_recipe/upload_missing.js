const cloudinary = require('cloudinary').v2;
const fs = require('fs');

cloudinary.config({
  cloud_name: 'davj7mdjj',
  api_key: '979485862516478',
  api_secret: 'qZHkaX0of_imikGcFUdUSK_7l4g'
});

const folder = 'ai-recipe-app/taxonomy';
const brainDir = '/home/ousseynou_diedhiou/.gemini/antigravity/brain/de99fd8f-e6f3-44fe-87d3-f8ae90fd25b2/';

// We only need the base filenames since we know they are in brainDir
// I will just use fs.readdirSync to find the exact filenames since they have timestamps
const files = fs.readdirSync(brainDir);

const mappings = [
  { filePrefix: 'cat_snacks', targets: ["Snack", "Snacks", "Sides & Snacks", "Side Dishes", "Appetizers"] },
  { filePrefix: 'cat_main_dishes', targets: ["Main Dishes"] },
  { filePrefix: 'cat_drinks', targets: ["Drinks"] },
  { filePrefix: 'cat_baking', targets: ["Baking"] },
  { filePrefix: 'cat_salads', targets: ["Salads"] },
  { filePrefix: 'cat_protein', targets: ["Protein Plates"] },
  { filePrefix: 'cat_breakfast', targets: ["Breakfast"] },
  { filePrefix: 'cat_rice_grain', targets: ["Rice & Grain Dishes"] },
  { filePrefix: 'cat_stir_fry', targets: ["Stir Fry Sauté Wok"] },
  { filePrefix: 'cat_street_food', targets: ["Handheld Street Food"] },
  { filePrefix: 'cuisine_african', targets: ["African"] },
  { filePrefix: 'cuisine_german', targets: ["German"] },
  { filePrefix: 'cuisine_lebanese', targets: ["Lebanese"] },
  { filePrefix: 'cat_misc', targets: ["Miscellaneous"] }
];

async function uploadAndGenerateSQL() {
  console.log('-- SQL script to update missing taxonomy images');
  for (const map of mappings) {
    const matchingFile = files.find(f => f.startsWith(map.filePrefix) && f.endsWith('.png'));
    if (!matchingFile) {
      console.error(`-- Error: Could not find file for prefix ${map.filePrefix}`);
      continue;
    }
    
    const filePath = brainDir + matchingFile;
    try {
      const res = await cloudinary.uploader.upload(filePath, { folder });
      const secureUrl = res.secure_url;
      
      for (const target of map.targets) {
        // Escape single quotes if necessary, though these targets don't have any
        console.log(`UPDATE recipe_categories SET image = '${secureUrl}' WHERE name = '${target}';`);
      }
    } catch (err) {
      console.error(`-- Error uploading ${matchingFile}:`, err.message);
    }
  }
}

uploadAndGenerateSQL();
