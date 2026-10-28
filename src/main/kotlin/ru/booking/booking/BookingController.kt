package ru.booking.booking

import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.net.URI
import java.time.LocalDate

@RestController
@RequestMapping("/api")
class BookingController(private val service: BookingService) {
    @GetMapping("/bookings/{id}")
    fun findById(@PathVariable id: Long): Booking = service.findById(id)

    @GetMapping("/rooms/{id}/bookings")
    fun findByRoomAndDate(
        @PathVariable id: Long,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
    ): List<Booking> = service.findByRoomAndDate(id, date)

    @PostMapping("/bookings")
    fun create(@Valid @RequestBody request: BookingRequest): ResponseEntity<Booking> {
        val booking = service.create(request.toInput())
        return ResponseEntity.created(URI.create("/api/bookings/${booking.id}")).body(booking)
    }

    @DeleteMapping("/bookings/{id}")
    fun cancel(@PathVariable id: Long): ResponseEntity<Void> {
        service.cancel(id)
        return ResponseEntity.noContent().build()
    }
}

