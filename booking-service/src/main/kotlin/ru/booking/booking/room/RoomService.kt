package ru.booking.booking.room

import org.springframework.stereotype.Service
import ru.booking.room.Room
import ru.booking.room.RoomInput

@Service
class RoomService(
    private val catalog: RoomCatalogClient,
    private val deletions: RoomDeletionService,
) {
    suspend fun findAll(): List<Room> = catalog.findAll()

    suspend fun findById(id: Long): Room = catalog.findById(id)

    suspend fun create(input: RoomInput): Room = catalog.create(input)

    suspend fun update(
        id: Long,
        input: RoomInput,
    ): Room = catalog.update(id, input)

    suspend fun delete(id: Long) = deletions.delete(id)
}
