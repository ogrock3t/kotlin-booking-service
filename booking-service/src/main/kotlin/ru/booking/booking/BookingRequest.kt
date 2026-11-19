package ru.booking.booking

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

data class BookingRequest(
    @field:Positive(message = "must be greater than 0")
    @field:Schema(description = "ID существующего помещения", example = "1")
    val roomId: Long,
    @field:NotBlank(message = "must not be blank")
    @field:Size(max = 100, message = "must contain at most {max} characters")
    @field:Schema(example = "Алексей")
    val bookedBy: String,
    @field:NotBlank(message = "must not be blank")
    @field:Size(max = 200, message = "must contain at most {max} characters")
    @field:Schema(example = "Семинар")
    val title: String,
    @field:Schema(
        implementation = String::class,
        example = "2030-01-02T10:00:00",
        description = "Начало в часовом поясе приложения",
    )
    val startTime: LocalDateTime,
    @field:Schema(
        implementation = String::class,
        example = "2030-01-02T11:00:00",
        description = "Окончание в часовом поясе приложения",
    )
    val endTime: LocalDateTime,
) {
    fun toInput() = BookingInput(roomId, bookedBy, title, startTime, endTime)
}
