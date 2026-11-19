package ru.booking.booking.history

data class BookingHistoryPage(
    val content: List<BookingEvent>,
    val page: Int,
    val size: Int,
    val hasNext: Boolean,
)
