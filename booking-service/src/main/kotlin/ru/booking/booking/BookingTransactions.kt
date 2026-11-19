package ru.booking.booking

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import ru.booking.booking.history.BookingEvent
import ru.booking.booking.history.BookingOutbox
import ru.booking.booking.history.BookingStatus
import ru.booking.booking.room.BookingRoomRepository
import ru.booking.shared.ConflictException
import ru.booking.shared.NotFoundException
import ru.booking.shared.ValidationException
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class BookingTransactions(
    private val bookings: BookingRepository,
    private val bookingRooms: BookingRoomRepository,
    private val outbox: BookingOutbox,
    private val clock: Clock,
) {
    fun findByRoomAndDate(
        roomId: Long,
        date: LocalDate,
    ): List<Booking> {
        requireBookableRoom(roomId)
        return bookings.findOverlapping(roomId, date.atStartOfDay(), date.plusDays(1).atStartOfDay())
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = [Exception::class])
    fun create(input: BookingInput): Booking {
        bookingRooms.lock(input.roomId)
        requireBookableRoom(input.roomId)
        if (input.startTime.isBefore(LocalDateTime.now(clock))) {
            throw ValidationException("Booking start time must not be in the past")
        }
        if (bookings.findOverlapping(input.roomId, input.startTime, input.endTime).isNotEmpty()) {
            throw ConflictException("Room is already booked for this time")
        }
        return bookings.create(input).also { record(it, BookingStatus.CONFIRMED) }
    }

    @Transactional(rollbackFor = [Exception::class])
    fun cancel(id: Long) {
        val booking = bookings.delete(id) ?: throw NotFoundException("Booking $id not found")
        record(booking, BookingStatus.CANCELLED)
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun beginRoomDeletion(roomId: Long) {
        bookingRooms.lock(roomId)
        if (bookings.existsByRoomId(roomId)) {
            throw ConflictException("Cancel all bookings for this room before deleting it")
        }
        bookingRooms.beginDeletion(roomId)
    }

    private fun requireBookableRoom(roomId: Long) {
        if (bookingRooms.isDeletingOrDeleted(roomId)) throw NotFoundException("Room $roomId not found")
    }

    private fun record(
        booking: Booking,
        status: BookingStatus,
    ) {
        outbox.add(BookingEvent(booking, status, clock.instant()))
    }
}
