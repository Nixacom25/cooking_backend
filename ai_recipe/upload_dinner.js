
const cloudinary = require('cloudinary').v2;
const fs = require('fs');

cloudinary.config({
  cloud_name: 'davj7mdjj',
  api_key: '979485862516478',
  api_secret: 'qZHkaX0of_imikGcFUdUSK_7l4g'
});

const folder = 'ai-recipe-app/taxonomy';

async function uploadOne() {
  const filePath = '/home/ousseynou_diedhiou/.gemini/antigravity/brain/49eaf70e-f79d-48e0-82a7-ac5b9edfd5ae/category_dinner_1778830886223.png';
  try {
    const res = await cloudinary.uploader.upload(filePath, { folder });
    console.log(`Uploaded Category: Dinner -> ${res.secure_url}`);
  } catch (err) {
    console.error(`Failed to upload Dinner:`, err.message);
  }
}

uploadOne();
