package ru.booking.booking

import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class JpaBookingRepository(private val repository: SpringDataBookingRepository) : BookingRepository {
    override fun findById(id: Long): Booking? = repository.findById(id).orElse(null)?.toBooking()

    override fun findOverlapping(
        roomId: Long,
        start: LocalDateTime,
        end: LocalDateTime,
    ): List<Booking> =
        repository.findByRoomIdAndStartTimeLessThanAndEndTimeGreaterThanOrderByStartTimeAscIdAsc(
            roomId,
            end,
            start,
        ).map(BookingEntity::toBooking)

    override fun create(input: BookingInput): Booking =
        repository.saveAndFlush(
            BookingEntity(input.roomId, input.bookedBy, input.title, input.startTime, input.endTime),
        ).toBooking()

    override fun existsByRoomId(roomId: Long): Boolean = repository.existsByRoomId(roomId)

    override fun delete(id: Long): Booking? {
        val entity = repository.findLockedById(id) ?: return null
        repository.delete(entity)
        repository.flush()
        return entity.toBooking()
    }
}
