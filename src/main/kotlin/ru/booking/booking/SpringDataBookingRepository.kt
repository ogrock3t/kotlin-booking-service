package ru.booking.booking

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import java.time.LocalDateTime

interface SpringDataBookingRepository : JpaRepository<BookingEntity, Long> {
    fun findByRoomIdAndStartTimeLessThanAndEndTimeGreaterThanOrderByStartTimeAscIdAsc(
        roomId: Long,
        end: LocalDateTime,
        start: LocalDateTime,
    ): List<BookingEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findLockedById(id: Long): BookingEntity?
}
