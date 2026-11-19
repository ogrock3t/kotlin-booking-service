package ru.booking.room

import org.springframework.stereotype.Repository

@Repository
class JpaRoomRepository(private val repository: SpringDataRoomRepository) : RoomRepository {
    override fun findAll(): List<Room> = repository.findAllByOrderByIdAsc().map { it.toRoom() }

    override fun findById(id: Long): Room? = repository.findById(id).orElse(null)?.toRoom()

    override fun create(input: RoomInput): Room =
        repository.saveAndFlush(RoomEntity(input.name, input.capacity, input.description)).toRoom()

    override fun update(
        id: Long,
        input: RoomInput,
    ): Room? {
        val entity = repository.findLockedById(id) ?: return null
        val updated = RoomEntity(input.name, input.capacity, input.description, entity.id)
        return repository.saveAndFlush(updated).toRoom()
    }

    override fun delete(id: Long): Boolean {
        val entity = repository.findLockedById(id) ?: return false
        repository.delete(entity)
        repository.flush()
        return true
    }
}
