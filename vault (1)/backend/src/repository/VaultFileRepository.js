const db = require('../config/db');

class VaultFileRepository {
  async insertFile({ user_id, file_type, file_name, file_size, file_url, thumbnail_url, location_lat, location_lng, location_name }) {
    const queryStr = `
      INSERT INTO vault_files (
        id, user_id, file_type, file_name, file_size, file_url, thumbnail_url, location_lat, location_lng, location_name, uploaded_at, is_deleted, is_in_bin
      ) VALUES (
        uuid_generate_v4(), $1, $2, $3, $4, $5, $6, $7, $8, $9, CURRENT_TIMESTAMP, false, false
      ) RETURNING *
    `;
    const result = await db.query(queryStr, [
      user_id,
      file_type,
      file_name,
      file_size,
      file_url,
      thumbnail_url,
      location_lat,
      location_lng,
      location_name
    ]);
    return result.rows[0];
  }

  async findById(id) {
    const result = await db.query('SELECT * FROM vault_files WHERE id = $1', [id]);
    return result.rows[0];
  }

  async findByUserId(userId) {
    const result = await db.query(
      'SELECT * FROM vault_files WHERE user_id = $1 AND is_deleted = false ORDER BY uploaded_at DESC',
      [userId]
    );
    return result.rows[0];
  }

  async getFilesByUserIdFull(userId) {
    const result = await db.query(
      'SELECT * FROM vault_files WHERE user_id = $1 ORDER BY uploaded_at DESC',
      [userId]
    );
    return result.rows;
  }

  async softDeleteFile(id) {
    const result = await db.query(
      'UPDATE vault_files SET is_deleted = true WHERE id = $1 RETURNING *',
      [id]
    );
    return result.rows[0];
  }

  async getLatestLocation(userId) {
    const queryStr = `
      SELECT location_lat, location_lng, location_name, uploaded_at 
      FROM vault_files 
      WHERE user_id = $1 AND location_lat IS NOT NULL AND location_lng IS NOT NULL 
      ORDER BY uploaded_at DESC LIMIT 1
    `;
    const result = await db.query(queryStr, [userId]);
    return result.rows[0];
  }
}

module.exports = new VaultFileRepository();
