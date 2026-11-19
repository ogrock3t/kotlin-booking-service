package ru.booking.booking.history

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class BookingHistoryConsumer(
    private val json: ObjectMapper,
    private val history: BookingHistoryRepository,
) {
    @KafkaListener(topics = ["\${app.kafka.booking-events-topic}"])
    fun consume(payload: String) {
        history.record(json.readValue(payload, BookingEvent::class.java))
    }
}
