package ru.booking.booking

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.booking.booking.history.BookingHistoryPage
import ru.booking.booking.history.BookingHistoryRepository
import java.net.URI
import java.time.LocalDate

@RestController
@RequestMapping("/api")
@Tag(name = "Бронирования")
class BookingController(
    private val service: BookingService,
    private val history: BookingHistoryRepository,
) {
    @GetMapping("/bookings/history")
    @Operation(summary = "История бронирований", description = "По 10 броней на страницу. Нумерация начинается с 0.")
    fun history(
        @RequestParam(defaultValue = "0") page: Int,
    ): BookingHistoryPage = history.findPage(page)

    @GetMapping("/bookings/{id}")
    @Operation(summary = "Получить действующую бронь по ID")
    fun findById(
        @PathVariable id: Long,
    ): Booking = service.findById(id)

    @GetMapping("/rooms/{id}/bookings")
    @Operation(summary = "Посмотреть расписание помещения на дату")
    suspend fun findByRoomAndDate(
        @PathVariable id: Long,
        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Parameter(example = "2030-01-02")
        date: LocalDate,
    ): List<Booking> = service.findByRoomAndDate(id, date)

    @PostMapping("/bookings")
    @Operation(summary = "Создать бронь")
    @ApiResponse(responseCode = "201", description = "Бронь создана", useReturnTypeSchema = true)
    suspend fun create(
        @Valid @RequestBody request: BookingRequest,
    ): ResponseEntity<Booking> {
        val booking = service.create(request.toInput())
        return ResponseEntity.created(URI.create("/api/bookings/${booking.id}")).body(booking)
    }

    @DeleteMapping("/bookings/{id}")
    @Operation(summary = "Отменить бронь")
    @ApiResponse(responseCode = "204", description = "Бронь отменена", content = [Content()])
    fun cancel(
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        service.cancel(id)
        return ResponseEntity.noContent().build()
    }
}
