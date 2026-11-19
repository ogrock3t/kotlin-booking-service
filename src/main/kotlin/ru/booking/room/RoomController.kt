package ru.booking.room

import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.net.URI

@RestController
@RequestMapping("/api/rooms")
class RoomController(private val service: RoomService) {
    @GetMapping
    fun findAll(): List<Room> = service.findAll()

    @GetMapping("/{id}")
    fun findById(@PathVariable id: Long): Room = service.findById(id)

    @PostMapping
    fun create(@Valid @RequestBody request: RoomRequest): ResponseEntity<Room> {
        val room = service.create(request.toInput())
        return ResponseEntity.created(URI.create("/api/rooms/${room.id}")).body(room)
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody request: RoomRequest): Room =
        service.update(id, request.toInput())

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Void> {
        service.delete(id)
        return ResponseEntity.noContent().build()
    }
}

