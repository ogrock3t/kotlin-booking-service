CREATE TABLE rooms (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL CHECK (length(trim(name)) > 0),
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    description VARCHAR(1000) NOT NULL DEFAULT ''
);

CREATE TABLE bookings (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    room_id BIGINT NOT NULL REFERENCES rooms(id),
    booked_by VARCHAR(100) NOT NULL CHECK (length(trim(booked_by)) > 0),
    title VARCHAR(200) NOT NULL CHECK (length(trim(title)) > 0),
    start_time TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    end_time TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CHECK (end_time > start_time)
);

CREATE INDEX idx_bookings_room_time ON bookings (room_id, start_time, end_time);