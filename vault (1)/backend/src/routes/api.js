const express = require('express');
const AuthController = require('../controller/AuthController');
const FileController = require('../controller/FileController');
const SecurityController = require('../controller/SecurityController');
const AdminController = require('../controller/AdminController');

const authMiddleware = require('../middleware/auth');
const upload = require('../middleware/uploader');
const rateLimit = require('express-rate-limit');

const router = express.Router();

// Strict Rate Limiting on Authentication and Event alert triggers
const strictLimiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes window
  max: 100, // max 100 entries
  message: { error: 'Too many authentication/report requests, please wait 15 minutes before repeating' }
});

// Device Registration & Session Handlers
router.post('/auth/register', strictLimiter, AuthController.registerOrUpdateDevice);
router.patch('/users/:deviceId/active', AuthController.pingActiveState);

// File Vault Client Side Uploader (Multi-part binary metadata configuration)
router.post('/files/upload', upload.single('file'), FileController.uploadVaultFile);

// Alarm Incident Realtime Reporter
router.post('/security/event', upload.single('file'), SecurityController.reportSecurityEvent);

// Admin Control Panel Security Session Login
router.post('/admin/login', strictLimiter, AdminController.adminLogin);

// Protected Core Administration Boundaries Block
router.get('/admin/users', authMiddleware, AdminController.getUsersMetrics);
router.get('/admin/users/:id', authMiddleware, AdminController.getUserDetails);
router.delete('/admin/files/:id', authMiddleware, AdminController.deleteVaultFile);
router.get('/admin/files/:id/download', authMiddleware, AdminController.streamSecureFile);
router.post('/admin/notify/:userId', authMiddleware, AdminController.sendAlertNotification);
router.get('/admin/location/:userId', authMiddleware, AdminController.getUserLocationOverview);

module.exports = router;
