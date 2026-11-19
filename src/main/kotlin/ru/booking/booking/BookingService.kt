package ru.booking.booking

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.booking.room.RoomAccess
import ru.booking.shared.ConflictException
import ru.booking.shared.NotFoundException
import ru.booking.shared.ValidationException
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Service
class BookingService(
    private val repository: BookingRepository,
    private val rooms: RoomAccess,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun findById(id: Long): Booking = repository.findById(id)
        ?: throw NotFoundException("Booking $id not found")

    fun findByRoomAndDate(roomId: Long, date: LocalDate): List<Booking> {
        rooms.requireExists(roomId)
        return repository.findOverlapping(roomId, date.atStartOfDay(), date.plusDays(1).atStartOfDay())
    }

    @Transactional
    fun create(input: BookingInput): Booking {
        validate(input)
        rooms.lockForBooking(input.roomId)
        if (input.startTime.isBefore(LocalDateTime.now(clock))) {
            throw ValidationException("Booking start time must not be in the past")
        }
        if (repository.findOverlapping(input.roomId, input.startTime, input.endTime).isNotEmpty()) {
            throw ConflictException("Room is already booked for this time")
        }
        val booking = repository.create(input)
        log.info("Created booking id={} roomId={}", booking.id, booking.roomId)
        return booking
    }

    @Transactional
    fun cancel(id: Long) {
        if (!repository.delete(id)) throw NotFoundException("Booking $id not found")
        log.info("Cancelled booking id={}", id)
    }

    private fun validate(input: BookingInput) {
        if (input.roomId <= 0) throw ValidationException("Room ID must be positive")
        if (input.bookedBy.isBlank() || input.bookedBy.length > 100) {
            throw ValidationException("Booker name must contain between 1 and 100 characters")
        }
        if (input.title.isBlank() || input.title.length > 200) {
            throw ValidationException("Booking title must contain between 1 and 200 characters")
        }
        if (!input.endTime.isAfter(input.startTime)) {
            throw ValidationException("End time must be after start time")
        }
        if (input.startTime != input.startTime.truncatedTo(ChronoUnit.MICROS) ||
            input.endTime != input.endTime.truncatedTo(ChronoUnit.MICROS)) {
            throw ValidationException("Time precision must not exceed 6 fractional digits")
        }
    }
}

