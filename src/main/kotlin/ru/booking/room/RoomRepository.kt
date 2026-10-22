package ru.booking.room

interface RoomRepository {
    fun findAll(): List<Room>
    fun findById(id: Long): Room?
    fun lockById(id: Long): Boolean
    fun create(input: RoomInput): Room
    fun update(id: Long, input: RoomInput): Room?
    fun delete(id: Long): Boolean
}
