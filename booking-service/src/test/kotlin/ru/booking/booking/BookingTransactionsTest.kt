package ru.booking.booking

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import ru.booking.booking.history.BookingEvent
import ru.booking.booking.history.BookingOutbox
import ru.booking.booking.history.BookingStatus
import ru.booking.booking.room.BookingRoomRepository
import ru.booking.shared.ConflictException
import ru.booking.shared.NotFoundException
import ru.booking.shared.ValidationException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class BookingTransactionsTest {
    private val bookings = mock(BookingRepository::class.java)
    private val rooms = mock(BookingRoomRepository::class.java)
    private val outbox = mock(BookingOutbox::class.java)
    private val clock = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC)
    private val transactions = BookingTransactions(bookings, rooms, outbox, clock)
    private val input =
        BookingInput(
            1,
            "Алексей",
            "Семинар",
            LocalDateTime.parse("2030-01-02T10:00:00"),
            LocalDateTime.parse("2030-01-02T11:00:00"),
        )
    private val saved = Booking(7, input.roomId, input.bookedBy, input.title, input.startTime, input.endTime)

    @Test
    fun `locks the room before checking time and records confirmation`() {
        `when`(bookings.create(input)).thenReturn(saved)

        assertEquals(saved, transactions.create(input))

        val order = inOrder(rooms, bookings, outbox)
        order.verify(rooms).lock(1)
        order.verify(rooms).isDeletingOrDeleted(1)
        order.verify(bookings).findOverlapping(1, input.startTime, input.endTime)
        order.verify(bookings).create(input)
        order.verify(outbox).add(BookingEvent(saved, BookingStatus.CONFIRMED, clock.instant()))
    }

    @Test
    fun `rejects overlapping bookings without writing an event`() {
        `when`(bookings.findOverlapping(1, input.startTime, input.endTime)).thenReturn(listOf(saved))

        assertThrows(ConflictException::class.java) { transactions.create(input) }
        verify(bookings, never()).create(input)
        verifyNoInteractions(outbox)
    }

    @Test
    fun `rejects a past booking and a booking for a room pending deletion`() {
        val past = input.copy(startTime = input.startTime.minusYears(1), endTime = input.endTime.minusYears(1))
        assertThrows(ValidationException::class.java) { transactions.create(past) }

        `when`(rooms.isDeletingOrDeleted(1)).thenReturn(true)
        assertThrows(NotFoundException::class.java) { transactions.create(input) }
        verifyNoInteractions(bookings, outbox)
    }

    @Test
    fun `uses the same lock for room deletion and rejects rooms with existing bookings`() {
        `when`(bookings.existsByRoomId(1)).thenReturn(true, false)
        assertThrows(ConflictException::class.java) { transactions.beginRoomDeletion(1) }
        verify(rooms, never()).beginDeletion(1)

        clearInvocations(rooms, bookings)
        transactions.beginRoomDeletion(1)
        val order = inOrder(rooms, bookings)
        order.verify(rooms).lock(1)
        order.verify(bookings).existsByRoomId(1)
        order.verify(rooms).beginDeletion(1)
    }

    @Test
    fun `lists intervals intersecting the selected day`() {
        val date = LocalDate.of(2030, 1, 2)
        transactions.findByRoomAndDate(1, date)
        verify(bookings).findOverlapping(1, date.atStartOfDay(), date.plusDays(1).atStartOfDay())
    }

    @Test
    fun `cancels an existing booking and records cancellation once`() {
        `when`(bookings.delete(7)).thenReturn(saved, null)

        transactions.cancel(7)
        assertThrows(NotFoundException::class.java) { transactions.cancel(7) }
        verify(outbox).add(BookingEvent(saved, BookingStatus.CANCELLED, clock.instant()))
    }
}
