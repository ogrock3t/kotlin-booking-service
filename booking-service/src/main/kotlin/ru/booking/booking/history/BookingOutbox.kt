package ru.booking.booking.history

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.future.await
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.kafka.core.KafkaAdmin
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class BookingOutbox(
    private val jdbc: JdbcClient,
    private val json: ObjectMapper,
    private val kafka: KafkaTemplate<String, String>,
    private val kafkaAdmin: KafkaAdmin,
    @Value("\${app.kafka.booking-events-topic}") private val topic: String,
) {
    private var topicsReady = false

    fun add(event: BookingEvent) {
        jdbc.sql("INSERT INTO booking_outbox (booking_id, payload) VALUES (:bookingId, :payload)")
            .param("bookingId", event.booking.id)
            .param("payload", json.writeValueAsString(event))
            .update()
    }

    suspend fun publishNext(): Boolean {
        if (!topicsReady) {
            topicsReady = kafkaAdmin.initialize()
            check(topicsReady) { "Kafka topics are unavailable" }
        }
        val message =
            jdbc.sql("SELECT id, booking_id, payload FROM booking_outbox ORDER BY id LIMIT 1")
                .query { row, _ ->
                    Message(row.getLong("id"), row.getLong("booking_id"), row.getString("payload"))
                }
                .optional()
                .orElse(null) ?: return false

        try {
            kafka.send(topic, message.bookingId.toString(), message.payload).await()
        } catch (error: Exception) {
            topicsReady = false
            throw error
        }
        jdbc.sql("DELETE FROM booking_outbox WHERE id = :id")
            .param("id", message.id)
            .update()
        return true
    }

    private data class Message(
        val id: Long,
        val bookingId: Long,
        val payload: String,
    )
}
