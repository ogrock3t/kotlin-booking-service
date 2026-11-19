package ru.booking.booking

import io.grpc.Status
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import ru.booking.booking.room.RoomCatalogClient
import ru.booking.booking.room.RoomServiceException
import ru.booking.room.Room
import ru.booking.shared.NotFoundException
import ru.booking.shared.ValidationException
import java.time.LocalDateTime

class BookingServiceTest {
    private val bookings = mock(BookingRepository::class.java)
    private val rooms = mock(RoomCatalogClient::class.java)
    private val transactions = mock(BookingTransactions::class.java)
    private val service = BookingService(bookings, rooms, transactions)
    private val input =
        BookingInput(
            1,
            "Алексей",
            "Семинар",
            LocalDateTime.parse("2030-01-02T10:00:00"),
            LocalDateTime.parse("2030-01-02T11:00:00"),
        )

    @Test
    fun `checks the room over gRPC before creating a booking`() {
        runBlocking {
            val saved = Booking(7, input.roomId, input.bookedBy, input.title, input.startTime, input.endTime)
            `when`(rooms.findById(input.roomId)).thenReturn(Room(input.roomId, "101", 20, ""))
            `when`(transactions.create(input)).thenReturn(saved)

            assertEquals(saved, service.create(input))
            val order = inOrder(rooms, transactions)
            order.verify(rooms).findById(input.roomId)
            order.verify(transactions).create(input)
        }
    }

    @Test
    fun `rejects invalid payloads before accessing other components`() {
        val invalidInputs =
            listOf(
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
            assertThrows(ValidationException::class.java) { runBlocking { service.create(invalid) } }
        }
        verifyNoInteractions(rooms, transactions)
    }

    @Test
    fun `does not attempt writes if the room is missing or its service is unavailable`() {
        val missing = NotFoundException("Room not found")
        val unavailable = RoomServiceException(Status.Code.UNAVAILABLE, Status.UNAVAILABLE.asException())
        runBlocking {
            doThrow(missing, unavailable).`when`(rooms).findById(input.roomId)
        }

        assertThrows(NotFoundException::class.java) { runBlocking { service.create(input) } }
        assertThrows(RoomServiceException::class.java) { runBlocking { service.create(input) } }
        verifyNoInteractions(transactions)
    }
}
