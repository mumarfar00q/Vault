const jwt = require('jsonwebtoken');
const bcrypt = require('bcrypt');
const admin = require('firebase-admin');
const fs = require('fs');
const UserRepository = require('../repository/UserRepository');
const VaultFileRepository = require('../repository/VaultFileRepository');
const SecurityEventRepository = require('../repository/SecurityEventRepository');

// Initialize Firebase Admin dynamically representation
let firebaseInitialized = false;
try {
  const saString = process.env.FIREBASE_SERVICE_ACCOUNT;
  if (saString) {
    const serviceAccount = JSON.parse(saString);
    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount)
    });
    firebaseInitialized = true;
    console.log('Firebase Admin SDK integrated successfully');
  } else {
    console.warn('Firebase Service Account env empty. Push notifications will run mock fallback logs.');
  }
} catch (e) {
  console.error('Failed to configure Firebase SDK integration details', e);
}

exports.adminLogin = async (req, res, next) => {
  try {
    const { password } = req.body;
    if (!password) {
      return res.status(400).json({ error: 'Core administration passphrase required' });
    }

    const hashedEnv = process.env.ADMIN_PASSWORD_HASH;
    if (!hashedEnv) {
      return res.status(500).json({ error: 'Server authentication hash configuration missing from instance' });
    }

    const match = await bcrypt.compare(password, hashedEnv);
    if (!match) {
      return res.status(401).json({ error: 'Invalid administrators security token passphrase' });
    }

    // Sign 1 hour validity secure JWT
    const token = jwt.sign(
      { admin: true, loggedAt: new Date() },
      process.env.JWT_SECRET || 'fallback_secret',
      { expiresIn: '1h' }
    );

    return res.status(200).json({
      token,
      expires_in: '1 hour',
      status: 'ADMIN_ACCESS_GRANTED'
    });
  } catch (err) {
    next(err);
  }
};

exports.getUsersMetrics = async (req, res, next) => {
  try {
    const users = await UserRepository.getAllUsersWithMetrics();
    return res.status(200).json(users);
  } catch (err) {
    next(err);
  }
};

exports.getUserDetails = async (req, res, next) => {
  try {
    const { id } = req.params;
    const user = await UserRepository.findById(id);
    if (!user) {
      return res.status(404).json({ error: 'User file record not generated' });
    }

    const files = await VaultFileRepository.getFilesByUserIdFull(id);
    const incidents = await SecurityEventRepository.findByUserId(id);

    return res.status(200).json({
      user,
      files,
      incidents
    });
  } catch (err) {
    next(err);
  }
};

exports.deleteVaultFile = async (req, res, next) => {
  try {
    const { id } = req.params;
    const target = await VaultFileRepository.findById(id);
    if (!target) {
      return res.status(404).json({ error: 'Encrypted library item not found' });
    }

    await VaultFileRepository.softDeleteFile(id);
    return res.status(200).json({
      status: 'DELETED',
      file_id: id,
      message: 'Item soft-deleted successfully from accessible archives'
    });
  } catch (err) {
    next(err);
  }
};

exports.streamSecureFile = async (req, res, next) => {
  try {
    const { id } = req.params;
    const target = await VaultFileRepository.findById(id);
    if (!target) {
      return res.status(404).json({ error: 'Requested item does not exist' });
    }

    const physicalPath = target.file_url;
    if (!fs.existsSync(physicalPath)) {
      return res.status(404).json({ error: 'Physical storage blocks removed/archived offline' });
    }

    res.setHeader('Content-Type', target.file_type || 'application/octet-stream');
    res.setHeader('Content-Disposition', `attachment; filename="${target.file_name}"`);
    
    const stream = fs.createReadStream(physicalPath);
    stream.pipe(res);
  } catch (err) {
    next(err);
  }
};

exports.sendAlertNotification = async (req, res, next) => {
  try {
    const { userId } = req.params;
    const { title, body } = req.body;

    const user = await UserRepository.findById(userId);
    if (!user) {
      return res.status(404).json({ error: 'User device footprint not targeted' });
    }

    const responsePayload = {
      userId,
      title: title || 'SECURITY COMPLIANCE NOTIFICATION',
      body: body || 'A security supervisor requests mandatory backup audit reconciliation now',
      timestamp: new Date()
    };

    if (firebaseInitialized) {
      // Trigger actual FCM push notification payload broadcast
      try {
        const message = {
          topic: `user-${userId}`,
          notification: {
            title: responsePayload.title,
            body: responsePayload.body
          },
          data: {
            click_action: 'FLUTTER_NOTIFICATION_CLICK',
            action: 'FORCE_BACKGROUND_SYNC'
          }
        };
        const fcmId = await admin.messaging().send(message);
        return res.status(200).json({
          status: 'BROADCAST_OK',
          fcmId,
          payload: responsePayload
        });
      } catch (fcmErr) {
        console.error('FCM exception route failed, running mock logging', fcmErr);
      }
    }

    // Fallback Mock representation logged silently
    console.log(`[PUSH BACKFLOW FALLBACK] Notification sent to USER: ${userId} (${user.display_name}). Description: "${responsePayload.body}"`);
    return res.status(200).json({
      status: 'MOCK_SENT_LOGGED',
      notice: 'Firebase configuration empty, running local telemetry simulation logs instead',
      payload: responsePayload
    });
  } catch (err) {
    next(err);
  }
};

exports.getUserLocationOverview = async (req, res, next) => {
  try {
    const { userId } = req.params;
    const lastLocation = await VaultFileRepository.getLatestLocation(userId);
    if (!lastLocation) {
      return res.status(404).json({ error: 'No coordinate telemetry captured for device node' });
    }
    return res.status(200).json({
      userId,
      telemetry: lastLocation
    });
  } catch (err) {
    next(err);
  }
};
