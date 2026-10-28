package ru.booking.booking

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

data class BookingRequest(
    @field:Positive(message = "must be greater than 0")
    val roomId: Long,
    @field:NotBlank(message = "must not be blank")
    @field:Size(max = 100, message = "must contain at most {max} characters")
    val bookedBy: String,
    @field:NotBlank(message = "must not be blank")
    @field:Size(max = 200, message = "must contain at most {max} characters")
    val title: String,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
) {
    fun toInput() = BookingInput(roomId, bookedBy, title, startTime, endTime)
}
