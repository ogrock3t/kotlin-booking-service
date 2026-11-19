package ru.booking.booking.history

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component

@Component
class BookingOutbox(
    private val jdbc: JdbcClient,
    private val json: ObjectMapper,
) {
    fun add(event: BookingEvent) =
        jdbc.sql("INSERT INTO booking_outbox (booking_id, payload) VALUES (:bookingId, :payload)")
            .param("bookingId", event.booking.id)
            .param("payload", json.writeValueAsString(event))
            .update()
}
