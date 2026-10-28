package ru.booking.room

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock

interface SpringDataRoomRepository : JpaRepository<RoomEntity, Long> {
    fun findAllByOrderByIdAsc(): List<RoomEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findLockedById(id: Long): RoomEntity?
}
