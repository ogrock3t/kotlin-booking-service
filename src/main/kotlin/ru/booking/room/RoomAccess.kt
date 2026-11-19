package ru.booking.room

interface RoomAccess {
    fun requireExists(id: Long)
    fun lockForBooking(id: Long)
}
