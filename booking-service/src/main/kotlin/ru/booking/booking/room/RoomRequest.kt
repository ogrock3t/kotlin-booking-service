package ru.booking.booking.room

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import ru.booking.room.RoomInput

data class RoomRequest(
    @field:NotBlank(message = "must not be blank")
    @field:Size(max = 100, message = "must contain at most {max} characters")
    @field:Schema(example = "Аудитория 101")
    val name: String,
    @field:Positive(message = "must be greater than 0")
    @field:Schema(example = "20")
    val capacity: Int,
    @field:Size(max = 1000, message = "must contain at most {max} characters")
    @field:Schema(example = "Проектор и доска")
    val description: String = "",
) {
    fun toInput() = RoomInput(name, capacity, description)
}
