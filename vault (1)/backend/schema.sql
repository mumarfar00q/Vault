-- Database Migration Schema setup for secure synchronized cloud vault backend database

-- Enable the UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Table: Users device session info
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_id TEXT UNIQUE NOT NULL,
    device_model TEXT,
    os_version TEXT,
    app_version TEXT,
    display_name TEXT,
    profile_photo_url TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_active_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    consent_given BOOLEAN DEFAULT FALSE,
    consent_at TIMESTAMP WITH TIME ZONE
);

-- Table: Encrypted library files
CREATE TABLE IF NOT EXISTS vault_files (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    file_type TEXT,
    file_name TEXT,
    file_size BIGINT,
    file_url TEXT,
    thumbnail_url TEXT,
    uploaded_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    location_lat DOUBLE PRECISION,
    location_lng DOUBLE PRECISION,
    location_name TEXT,
    is_deleted BOOLEAN DEFAULT FALSE,
    is_in_bin BOOLEAN DEFAULT FALSE
);

-- Table: Failed entries, authentication failure incidents and security events
CREATE TABLE IF NOT EXISTS security_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    image_url TEXT,
    location_lat DOUBLE PRECISION,
    location_lng DOUBLE PRECISION,
    details TEXT
);

-- Indices for query optimization
CREATE INDEX IF NOT EXISTS idx_users_device_id ON users(device_id);
CREATE INDEX IF NOT EXISTS idx_vault_files_user_id ON vault_files(user_id);
CREATE INDEX IF NOT EXISTS idx_security_events_user_id ON security_events(user_id);
