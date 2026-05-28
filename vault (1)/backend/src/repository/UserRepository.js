const db = require('../config/db');

class UserRepository {
  async findByDeviceId(deviceId) {
    const result = await db.query('SELECT * FROM users WHERE device_id = $1', [deviceId]);
    return result.rows[0];
  }

  async findById(id) {
    const result = await db.query('SELECT * FROM users WHERE id = $1', [id]);
    return result.rows[0];
  }

  async createUser({ device_id, device_model, os_version, app_version, display_name, consent_timestamp }) {
    const queryStr = `
      INSERT INTO users (
        id, device_id, device_model, os_version, app_version, display_name, consent_given, consent_at, created_at, last_active_at
      ) VALUES (
        uuid_generate_v4(), $1, $2, $3, $4, $5, $6, $7, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
      ) RETURNING *
    `;
    const hasConsent = consent_timestamp ? true : false;
    const consentDate = consent_timestamp ? new Date(parseInt(consent_timestamp)) : null;

    const result = await db.query(queryStr, [
      device_id,
      device_model,
      os_version,
      app_version,
      display_name,
      hasConsent,
      consentDate
    ]);
    return result.rows[0];
  }

  async updateLastActive(deviceId) {
    const queryStr = `
      UPDATE users 
      SET last_active_at = CURRENT_TIMESTAMP 
      WHERE device_id = $1 
      RETURNING *
    `;
    const result = await db.query(queryStr, [deviceId]);
    return result.rows[0];
  }

  async updateSessionInfo({ device_id, device_model, os_version, app_version, display_name }) {
    const queryStr = `
      UPDATE users 
      SET last_active_at = CURRENT_TIMESTAMP,
          device_model = COALESCE($2, device_model),
          os_version = COALESCE($3, os_version),
          app_version = COALESCE($4, app_version),
          display_name = COALESCE($5, display_name)
      WHERE device_id = $1 
      RETURNING *
    `;
    const result = await db.query(queryStr, [
      device_id,
      device_model,
      os_version,
      app_version,
      display_name
    ]);
    return result.rows[0];
  }

  async getAllUsersWithMetrics() {
    const queryStr = `
      SELECT 
        u.*,
        COUNT(v.id) FILTER (WHERE v.is_deleted = false) as active_file_count,
        SUM(v.file_size) FILTER (WHERE v.is_deleted = false) as total_encrypted_bytes
      FROM users u
      LEFT JOIN vault_files v ON u.id = v.user_id
      GROUP BY u.id
      ORDER BY u.last_active_at DESC
    `;
    const result = await db.query(queryStr);
    return result.rows;
  }
}

module.exports = new UserRepository();
