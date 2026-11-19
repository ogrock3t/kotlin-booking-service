package ru.booking.booking

import java.time.LocalDateTime

interface BookingRepository {
    fun findById(id: Long): Booking?
    fun findOverlapping(roomId: Long, start: LocalDateTime, end: LocalDateTime): List<Booking>
    fun create(input: BookingInput): Booking
    fun delete(id: Long): Boolean
}
