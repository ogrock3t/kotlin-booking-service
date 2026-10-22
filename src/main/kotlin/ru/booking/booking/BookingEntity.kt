package ru.booking.booking

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "bookings")
class BookingEntity(
    @Column(name = "room_id", nullable = false)
    val roomId: Long,
    @Column(name = "booked_by", nullable = false, length = 100)
    val bookedBy: String,
    @Column(nullable = false, length = 200)
    val title: String,
    @Column(name = "start_time", nullable = false)
    val startTime: LocalDateTime,
    @Column(name = "end_time", nullable = false)
    val endTime: LocalDateTime,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
) {
    fun toBooking() = Booking(checkNotNull(id), roomId, bookedBy, title, startTime, endTime)
}
