package ru.booking.booking

import java.time.LocalDateTime

data class BookingInput(
    val roomId: Long,
    val bookedBy: String,
    val title: String,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
)
