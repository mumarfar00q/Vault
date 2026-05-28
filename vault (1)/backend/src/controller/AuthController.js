const UserRepository = require('../repository/UserRepository');

exports.registerOrUpdateDevice = async (req, res, next) => {
  try {
    const { device_id, device_model, os_version, app_version, display_name, consent_timestamp } = req.body;

    if (!device_id) {
      return res.status(400).json({ error: 'device_id parameter is strictly required' });
    }

    let user = await UserRepository.findByDeviceId(device_id);

    if (user) {
      // Existing device, run dynamic updates to metadata on reconnection
      user = await UserRepository.updateSessionInfo({
        device_id,
        device_model,
        os_version,
        app_version,
        display_name
      });
      return res.status(200).json({
        user_id: user.id,
        status: 'SESSION_RENEWED',
        message: 'Device footprint details updated successfully'
      });
    } else {
      // Register secure new client unit session
      user = await UserRepository.createUser({
        device_id,
        device_model,
        os_version,
        app_version,
        display_name,
        consent_timestamp
      });
      return res.status(201).json({
        user_id: user.id,
        status: 'INITIAL_REGISTRATION_SUCCESS',
        message: 'New hardware node initialized securely'
      });
    }
  } catch (err) {
    next(err);
  }
};

exports.pingActiveState = async (req, res, next) => {
  try {
    const { deviceId } = req.params;
    if (!deviceId) {
      return res.status(400).json({ error: 'Device fingerprint spec required' });
    }

    const updated = await UserRepository.updateLastActive(deviceId);
    if (!updated) {
      return res.status(404).json({ error: 'Device node not found in repository logs' });
    }

    return res.status(200).json({ status: 'OK', last_active: updated.last_active_at });
  } catch (err) {
    next(err);
  }
};
