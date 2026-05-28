const SecurityEventRepository = require('../repository/SecurityEventRepository');
const UserRepository = require('../repository/UserRepository');

exports.reportSecurityEvent = async (req, res, next) => {
  try {
    let metadata = {};
    if (req.body.metadata) {
      try {
        metadata = JSON.parse(req.body.metadata);
      } catch (e) {
        // Fallback for form-data variables directly if parsing fails
        metadata = req.body;
      }
    } else {
      metadata = req.body;
    }

    const deviceId = req.headers['device_id'] || req.headers['x-device-id'] || metadata.deviceId;
    if (!deviceId) {
      return res.status(400).json({ error: 'Device identifying header missing' });
    }

    const user = await UserRepository.findByDeviceId(deviceId);
    if (!user) {
      return res.status(404).json({ error: 'Device identifier not registered' });
    }

    const lat = metadata.location_lat ? parseFloat(metadata.location_lat) : null;
    const lng = metadata.location_lng ? parseFloat(metadata.location_lng) : null;
    const details = metadata.details || 'Warning: Interactive bypass / failed keypad login threshold exceeded';
    const filePath = req.file ? req.file.path : null;

    const eventRecord = await SecurityEventRepository.insertEvent({
      user_id: user.id,
      image_url: filePath,
      location_lat: lat,
      location_lng: lng,
      details: details
    });

    return res.status(201).json({
      status: 'LOGGED_Urgently',
      event_id: eventRecord.id,
      message: 'Intrusion alert telemetry captured and archived in active logs'
    });
  } catch (err) {
    next(err);
  }
};
