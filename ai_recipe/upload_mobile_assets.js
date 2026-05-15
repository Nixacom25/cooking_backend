
const cloudinary = require('cloudinary').v2;
const fs = require('fs');
const path = require('path');

cloudinary.config({
  cloud_name: 'davj7mdjj',
  api_key: '979485862516478',
  api_secret: 'qZHkaX0of_imikGcFUdUSK_7l4g'
});

const mobileAssetsPath = '/home/ousseynou_diedhiou/Bureau/Nixacom/cooked/mobile/assets/images';
const folder = 'ai-recipe-app/taxonomy/mobile';

const filesToUpload = [
  '30-Minutes.png',
  'Plant-Based.png',
  'caribbean.png',
  'chinese.png',
  'easy-desserts.png',
  'french1.png',
  'greek1.png',
  'higth-proteins.png',
  'indian.png',
  'italian.png',
  'japanese.png',
  'korean.png',
  'low-cards.png',
  'mediterranean.png',
  'mexican.png',
  'west-african.png',
  'others.png'
];

async function uploadMobileAssets() {
  const results = {};
  for (const file of filesToUpload) {
    const filePath = path.join(mobileAssetsPath, file);
    if (fs.existsSync(filePath)) {
      try {
        const res = await cloudinary.uploader.upload(filePath, { folder });
        results[file] = res.secure_url;
        console.log(`Uploaded ${file} -> ${res.secure_url}`);
      } catch (err) {
        console.error(`Failed to upload ${file}:`, err.message);
      }
    } else {
      console.warn(`File not found: ${filePath}`);
    }
  }
  console.log('\n--- UPLOAD SUMMARY ---');
  console.log(JSON.stringify(results, null, 2));
}

uploadMobileAssets();
