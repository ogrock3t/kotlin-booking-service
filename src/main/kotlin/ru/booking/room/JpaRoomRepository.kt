package ru.booking.room

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Repository
import ru.booking.shared.ConflictException

@Repository
class JpaRoomRepository(private val repository: SpringDataRoomRepository) : RoomRepository {
    override fun findAll(): List<Room> = repository.findAllByOrderByIdAsc().map { it.toRoom() }

    override fun findById(id: Long): Room? = repository.findById(id).orElse(null)?.toRoom()

    override fun lockById(id: Long): Boolean = repository.findLockedById(id) != null

    override fun create(input: RoomInput): Room =
        repository.saveAndFlush(RoomEntity(input.name, input.capacity, input.description)).toRoom()

    override fun update(id: Long, input: RoomInput): Room? {
        val entity = repository.findLockedById(id) ?: return null
        entity.name = input.name
        entity.capacity = input.capacity
        entity.description = input.description
        return repository.saveAndFlush(entity).toRoom()
    }

    override fun delete(id: Long): Boolean {
        val entity = repository.findLockedById(id) ?: return false
        try {
            repository.delete(entity)
            repository.flush()
            return true
        } catch (error: DataIntegrityViolationException) {
            throw ConflictException("Cancel all bookings for this room before deleting it")
        }
    }
}
