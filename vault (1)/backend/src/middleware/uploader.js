const path = require('path');
const fs = require('fs');
const multer = require('multer');

// Configure storage configuration from environment options
const storagePath = process.env.STORAGE_PATH || './storage';

const storage = multer.diskStorage({
  destination: function (req, file, cb) {
    let destDir = path.join(storagePath);

    // Depending on the endpoint, create targeted child directory structure
    if (req.originalUrl.includes('/api/files/upload')) {
      // Expecting user_id in the uploaded fields metadata
      let userId = 'unattributed';
      if (req.body && req.body.metadata) {
        try {
          const parsed = JSON.parse(req.body.metadata);
          if (parsed.deviceId) userId = parsed.deviceId;
        } catch (e) {
          // Fallback if metadata is not yet parsed
        }
      }
      destDir = path.join(storagePath, userId);
    } else if (req.originalUrl.includes('/api/security/event')) {
      destDir = path.join(storagePath, 'security');
    }

    // Ensure physical folder is synchronously generated
    if (!fs.existsSync(destDir)) {
      fs.mkdirSync(destDir, { recursive: true });
    }

    cb(null, destDir);
  },
  filename: function (req, file, cb) {
    const uniqueSuffix = Date.now() + '-' + Math.round(Math.random() * 1E9);
    const ext = path.extname(file.originalname);
    
    // Check files configuration type
    if (req.originalUrl.includes('/api/files/upload')) {
      cb(null, uniqueSuffix + '.enc');
    } else if (req.originalUrl.includes('/api/security/event')) {
      cb(null, 'security_capture_' + uniqueSuffix + '.jpg');
    } else {
      cb(null, uniqueSuffix + ext);
    }
  }
});

// Enforce basic MIME/size limits
const fileFilter = (req, file, cb) => {
  // Vault allows encrypted bin files, security event requires standard visual formats
  if (req.originalUrl.includes('/api/security/event')) {
    if (file.mimetype.startsWith('image/')) {
      cb(null, true);
    } else {
      cb(new Error('Security incident file payload must be an image format!'), false);
    }
  } else {
    cb(null, true);
  }
};

const upload = multer({
  storage: storage,
  fileFilter: fileFilter,
  limits: {
    fileSize: 100 * 1024 * 1024 // 100MB limit for secure archives
  }
});

module.exports = upload;
