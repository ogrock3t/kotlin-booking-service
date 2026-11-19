package ru.booking.booking

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import ru.booking.booking.room.RoomCatalogClient
import ru.booking.shared.NotFoundException
import ru.booking.shared.ValidationException
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
class BookingService(
    private val repository: BookingRepository,
    private val rooms: RoomCatalogClient,
    private val transactions: BookingTransactions,
) {
    fun findById(id: Long): Booking =
        repository.findById(id)
            ?: throw NotFoundException("Booking $id not found")

    suspend fun findByRoomAndDate(
        roomId: Long,
        date: LocalDate,
    ): List<Booking> {
        rooms.findById(roomId)
        return withContext(Dispatchers.IO) { transactions.findByRoomAndDate(roomId, date) }
    }

    suspend fun create(input: BookingInput): Booking {
        validate(input)
        rooms.findById(input.roomId)
        return withContext(Dispatchers.IO) { transactions.create(input) }
    }

    fun cancel(id: Long) {
        transactions.cancel(id)
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
            input.endTime != input.endTime.truncatedTo(ChronoUnit.MICROS)
        ) {
            throw ValidationException("Time precision must not exceed 6 fractional digits")
        }
    }
}
