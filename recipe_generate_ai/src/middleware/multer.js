import multer from "multer";

const fileFilter = (req, file, cb) => {
  if (file.mimetype.startsWith("image/")) {
    cb(null, true);
  } else {
    cb(new Error("Only image files are allowed"), false);
  }
};

// Images stay in memory: they are resized with sharp and sent to OpenAI
// right away, so writing them to disk only added I/O and cleanup.
// Limits are generous because phone photos are big; we downscale anyway.
export const upload = multer({
  storage: multer.memoryStorage(),
  fileFilter,
  limits: {
    fileSize: 15 * 1024 * 1024,
    files: 4,
  },
});
