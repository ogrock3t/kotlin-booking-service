package ru.booking.room

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "rooms")
class RoomEntity(
    @Column(nullable = false, length = 100)
    var name: String,
    @Column(nullable = false)
    var capacity: Int,
    @Column(nullable = false, length = 1000)
    var description: String,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
) {
    fun toRoom() = Room(checkNotNull(id), name, capacity, description)
}
