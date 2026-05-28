const fs = require('fs');
const path = require('path');
const VaultFileRepository = require('../repository/VaultFileRepository');
const UserRepository = require('../repository/UserRepository');

exports.uploadVaultFile = async (req, res, next) => {
  try {
    if (!req.file) {
      return res.status(400).json({ error: 'Multipart binary payload data block missing/corrupt' });
    }

    let metadata = {};
    if (req.body.metadata) {
      try {
        metadata = JSON.parse(req.body.metadata);
      } catch (e) {
        return res.status(400).json({ error: 'Metadata parameters are not valid JSON configuration' });
      }
    }

    const deviceId = req.headers['device_id'] || req.headers['x-device-id'] || metadata.deviceId;
    if (!deviceId) {
      return res.status(400).json({ error: 'X-Device-ID context mapping missing from request' });
    }

    const user = await UserRepository.findByDeviceId(deviceId);
    if (!user) {
      return res.status(404).json({ error: 'Associated device registration session not compiled on system' });
    }

    // Extract GPS optional telemetry attributes
    const lat = metadata.location ? parseFloat(metadata.location.latitude) : null;
    const lng = metadata.location ? parseFloat(metadata.location.longitude) : null;
    const address = metadata.location ? metadata.location.address : null;

    const fileRecord = await VaultFileRepository.insertFile({
      user_id: user.id,
      file_type: req.file.mimetype || 'application/octet-stream',
      file_name: req.file.originalname || 'unknown.enc',
      file_size: req.file.size,
      file_url: req.file.path, // Direct references to localized storage path
      thumbnail_url: null,
      location_lat: lat,
      location_lng: lng,
      location_name: address
    });

    return res.status(201).json({
      file_id: fileRecord.id,
      status: 'SYNCHRONIZED',
      message: 'Encrypted block written securely'
    });
  } catch (err) {
    next(err);
  }
};
