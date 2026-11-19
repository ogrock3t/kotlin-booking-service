package ru.booking.booking.room

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

@Repository
class BookingRoomRepository(
    private val jdbc: JdbcClient,
) {
    fun lock(roomId: Long) {
        jdbc.sql("SELECT 1 FROM pg_advisory_xact_lock(:roomId)")
            .param("roomId", roomId)
            .query(Int::class.java)
            .single()
    }

    fun isDeletingOrDeleted(roomId: Long): Boolean =
        jdbc.sql("SELECT EXISTS (SELECT 1 FROM room_deletions WHERE room_id = :roomId)")
            .param("roomId", roomId)
            .query(Boolean::class.java)
            .single()

    fun beginDeletion(roomId: Long) {
        jdbc.sql("INSERT INTO room_deletions (room_id) VALUES (:roomId) ON CONFLICT DO NOTHING")
            .param("roomId", roomId)
            .update()
    }

    fun markDeleted(roomId: Long) {
        jdbc.sql("UPDATE room_deletions SET completed = true WHERE room_id = :roomId")
            .param("roomId", roomId)
            .update()
    }

    fun pendingDeletions(): List<Long> =
        jdbc.sql("SELECT room_id FROM room_deletions WHERE NOT completed ORDER BY room_id")
            .query(Long::class.java)
            .list()
}
