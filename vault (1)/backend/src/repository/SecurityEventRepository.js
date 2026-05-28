const db = require('../config/db');

class SecurityEventRepository {
  async insertEvent({ user_id, image_url, location_lat, location_lng, details }) {
    const queryStr = `
      INSERT INTO security_events (
        id, user_id, timestamp, image_url, location_lat, location_lng, details
      ) VALUES (
        uuid_generate_v4(), $1, CURRENT_TIMESTAMP, $2, $3, $4, $5
      ) RETURNING *
    `;
    const result = await db.query(queryStr, [
      user_id,
      image_url,
      location_lat,
      location_lng,
      details
    ]);
    return result.rows[0];
  }

  async findByUserId(userId) {
    const result = await db.query(
      'SELECT * FROM security_events WHERE user_id = $1 ORDER BY timestamp DESC',
      [userId]
    );
    return result.rows;
  }

  async getAllEvents() {
    const queryStr = `
      SELECT s.*, u.display_name, u.device_model 
      FROM security_events s
      LEFT JOIN users u ON s.user_id = u.id
      ORDER BY s.timestamp DESC
    `;
    const result = await db.query(queryStr);
    return result.rows;
  }
}

module.exports = new SecurityEventRepository();
