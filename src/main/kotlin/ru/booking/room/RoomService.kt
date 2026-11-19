package ru.booking.room

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.booking.shared.NotFoundException
import ru.booking.shared.ValidationException

@Service
class RoomService(private val repository: RoomRepository) : RoomAccess {
    private val log = LoggerFactory.getLogger(javaClass)

    fun findAll(): List<Room> = repository.findAll()

    fun findById(id: Long): Room = repository.findById(id)
        ?: throw NotFoundException("Room $id not found")

    override fun requireExists(id: Long) {
        findById(id)
    }

    override fun lockForBooking(id: Long) {
        if (!repository.lockById(id)) throw NotFoundException("Room $id not found")
    }

    @Transactional
    fun create(input: RoomInput): Room {
        validate(input)
        val room = repository.create(input)
        log.info("Created room id={}", room.id)
        return room
    }

    @Transactional
    fun update(id: Long, input: RoomInput): Room {
        validate(input)
        val room = repository.update(id, input)
            ?: throw NotFoundException("Room $id not found")
        log.info("Updated room id={}", id)
        return room
    }

    @Transactional
    fun delete(id: Long) {
        if (!repository.delete(id)) throw NotFoundException("Room $id not found")
        log.info("Deleted room id={}", id)
    }

    private fun validate(input: RoomInput) {
        if (input.name.isBlank() || input.name.length > 100) {
            throw ValidationException("Room name must contain between 1 and 100 characters")
        }
        if (input.capacity <= 0) throw ValidationException("Capacity must be positive")
        if (input.description.length > 1000) throw ValidationException("Description must not exceed 1000 characters")
    }
}

