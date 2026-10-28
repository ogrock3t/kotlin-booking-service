package ru.booking.booking

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import ru.booking.room.RoomAccess
import ru.booking.shared.ConflictException
import ru.booking.shared.NotFoundException
import ru.booking.shared.ValidationException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class BookingServiceTest {
    private val repository = mock(BookingRepository::class.java)
    private val rooms = mock(RoomAccess::class.java)
    private val clock = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC)
    private val service = BookingService(repository, rooms, clock)
    private val input = BookingInput(
        1, "Алексей", "Семинар",
        LocalDateTime.parse("2030-01-02T10:00:00"), LocalDateTime.parse("2030-01-02T11:00:00"),
    )

    @Test
    fun `creates booking after locking room and checking availability`() {
        val saved = Booking(7, input.roomId, input.bookedBy, input.title, input.startTime, input.endTime)
        `when`(repository.create(input)).thenReturn(saved)

        assertEquals(saved, service.create(input))

        val order = inOrder(rooms, repository)
        order.verify(rooms).lockForBooking(1)
        order.verify(repository).findOverlapping(1, input.startTime, input.endTime)
        order.verify(repository).create(input)
    }

    @Test
    fun `rejects overlapping booking without inserting it`() {
        val existing = Booking(1, 1, "Иван", "Встреча", input.startTime, input.endTime)
        `when`(repository.findOverlapping(1, input.startTime, input.endTime)).thenReturn(listOf(existing))

        assertThrows(ConflictException::class.java) { service.create(input) }
        verify(repository, never()).create(input)
    }

    @Test
    fun `rejects past booking`() {
        val past = input.copy(startTime = input.startTime.minusYears(1), endTime = input.endTime.minusYears(1))
        assertThrows(ValidationException::class.java) { service.create(past) }
        verifyNoInteractions(repository)
    }

    @Test
    fun `rejects invalid intervals and fields even without HTTP`() {
        val invalidInputs = listOf(
            input.copy(endTime = input.startTime),
            input.copy(endTime = input.startTime.minusMinutes(1)),
            input.copy(roomId = 0),
            input.copy(bookedBy = "  "),
            input.copy(bookedBy = "a".repeat(101)),
            input.copy(title = ""),
            input.copy(title = "a".repeat(201)),
            input.copy(startTime = input.startTime.plusNanos(1)),
        )
        for (invalid in invalidInputs) {
            assertThrows(ValidationException::class.java) { service.create(invalid) }
        }
        verifyNoInteractions(repository, rooms)
    }

    @Test
    fun `does not insert booking for missing room`() {
        doThrow(NotFoundException("Room not found")).`when`(rooms).lockForBooking(1)
        assertThrows(NotFoundException::class.java) { service.create(input) }
        verifyNoInteractions(repository)
    }

    @Test
    fun `lists all intervals intersecting the selected day`() {
        val date = LocalDate.of(2030, 1, 2)
        service.findByRoomAndDate(1, date)
        verify(rooms).requireExists(1)
        verify(repository).findOverlapping(1, date.atStartOfDay(), date.plusDays(1).atStartOfDay())
    }

    @Test
    fun `cancels existing booking`() {
        `when`(repository.delete(7)).thenReturn(true)
        service.cancel(7)
        verify(repository).delete(7)
    }

    @Test
    fun `returns not found when cancelling missing booking`() {
        assertThrows(NotFoundException::class.java) { service.cancel(7) }
    }
}

