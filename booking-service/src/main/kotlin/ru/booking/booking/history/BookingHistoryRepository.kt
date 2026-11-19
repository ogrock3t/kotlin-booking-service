package ru.booking.booking.history

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import ru.booking.booking.Booking
import ru.booking.shared.ValidationException
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class BookingHistoryRepository(
    private val jdbc: JdbcClient,
) {
    fun record(event: BookingEvent) {
        val booking = event.booking
        jdbc.sql(
            """
            INSERT INTO booking_history
                (booking_id, room_id, booked_by, title, start_time, end_time, status, changed_at)
            VALUES
                (:bookingId, :roomId, :bookedBy, :title, :startTime, :endTime, :status, :changedAt)
            ON CONFLICT (booking_id) DO UPDATE SET status = EXCLUDED.status, changed_at = EXCLUDED.changed_at
            WHERE booking_history.status = 'CONFIRMED' AND EXCLUDED.status = 'CANCELLED'
            """.trimIndent(),
        )
            .param("bookingId", booking.id)
            .param("roomId", booking.roomId)
            .param("bookedBy", booking.bookedBy)
            .param("title", booking.title)
            .param("startTime", booking.startTime)
            .param("endTime", booking.endTime)
            .param("status", event.status.name)
            .param("changedAt", event.changedAt.atOffset(ZoneOffset.UTC))
            .update()
    }

    fun findPage(page: Int): BookingHistoryPage {
        if (page < 0) throw ValidationException("Page must be zero or positive")

        val events =
            jdbc.sql(
                """
                SELECT booking_id, room_id, booked_by, title, start_time, end_time, status, changed_at
                FROM booking_history ORDER BY booking_id DESC
                LIMIT :limit OFFSET :offset
                """.trimIndent(),
            )
                .param("limit", PAGE_SIZE + 1)
                .param("offset", page.toLong() * PAGE_SIZE)
                .query { row, _ ->
                    BookingEvent(
                        Booking(
                            row.getLong("booking_id"),
                            row.getLong("room_id"),
                            row.getString("booked_by"),
                            row.getString("title"),
                            row.getObject("start_time", LocalDateTime::class.java),
                            row.getObject("end_time", LocalDateTime::class.java),
                        ),
                        BookingStatus.valueOf(row.getString("status")),
                        row.getObject("changed_at", OffsetDateTime::class.java).toInstant(),
                    )
                }
                .list()

        return BookingHistoryPage(events.take(PAGE_SIZE), page, PAGE_SIZE, events.size > PAGE_SIZE)
    }

    private companion object {
        const val PAGE_SIZE = 10
    }
}
