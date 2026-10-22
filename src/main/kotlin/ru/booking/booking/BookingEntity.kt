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
    var roomId: Long,
    @Column(name = "booked_by", nullable = false, length = 100)
    var bookedBy: String,
    @Column(nullable = false, length = 200)
    var title: String,
    @Column(name = "start_time", nullable = false)
    var startTime: LocalDateTime,
    @Column(name = "end_time", nullable = false)
    var endTime: LocalDateTime,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
) {
    fun toBooking() = Booking(checkNotNull(id), roomId, bookedBy, title, startTime, endTime)
}
