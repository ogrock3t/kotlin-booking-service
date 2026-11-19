CREATE TABLE bookings (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    room_id BIGINT NOT NULL CHECK (room_id > 0),
    booked_by VARCHAR(100) NOT NULL CHECK (length(trim(booked_by)) > 0),
    title VARCHAR(200) NOT NULL CHECK (length(trim(title)) > 0),
    start_time TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    end_time TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CHECK (end_time > start_time)
);

CREATE INDEX idx_bookings_room_time ON bookings (room_id, start_time, end_time);

CREATE TABLE room_deletions (
    room_id BIGINT PRIMARY KEY,
    completed BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE booking_outbox (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    payload TEXT NOT NULL
);

CREATE TABLE booking_history (
    booking_id BIGINT PRIMARY KEY,
    room_id BIGINT NOT NULL,
    booked_by VARCHAR(100) NOT NULL,
    title VARCHAR(200) NOT NULL,
    start_time TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    end_time TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    status VARCHAR(9) NOT NULL CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
