package ru.booking.booking.history

import ru.booking.booking.Booking
import java.time.Instant

data class BookingEvent(
    val booking: Booking,
    val status: BookingStatus,
    val changedAt: Instant,
)
