package ru.booking.room

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import ru.booking.shared.NotFoundException
import ru.booking.shared.ValidationException

class RoomServiceTest {
    private val repository = mock(RoomRepository::class.java)
    private val service = RoomService(repository)

    @Test
    fun `creates valid room`() {
        val input = RoomInput("Аудитория 101", 20, "Проектор")
        val saved = Room(1, input.name, input.capacity, input.description)
        `when`(repository.create(input)).thenReturn(saved)
        assertEquals(saved, service.create(input))
    }

    @Test
    fun `rejects invalid room on create and update`() {
        val invalidInputs = listOf(
            RoomInput(" ", 10, ""), RoomInput("a".repeat(101), 10, ""),
            RoomInput("101", 0, ""), RoomInput("101", -1, ""),
            RoomInput("101", 10, "a".repeat(1001)),
        )
        for (input in invalidInputs) {
            assertThrows(ValidationException::class.java) { service.create(input) }
            assertThrows(ValidationException::class.java) { service.update(1, input) }
        }
        verifyNoInteractions(repository)
    }

    @Test
    fun `missing room returns not found`() {
        assertThrows(NotFoundException::class.java) { service.findById(1) }
        assertThrows(NotFoundException::class.java) { service.lockForBooking(1) }
        assertThrows(NotFoundException::class.java) { service.update(1, RoomInput("101", 10, "")) }
        assertThrows(NotFoundException::class.java) { service.delete(1) }
    }
}

