package ru.booking.room

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

data class RoomRequest(
    @field:NotBlank(message = "must not be blank")
    @field:Size(max = 100, message = "must contain at most {max} characters")
    val name: String,
    @field:Positive(message = "must be greater than 0")
    val capacity: Int,
    @field:Size(max = 1000, message = "must contain at most {max} characters")
    val description: String = "",
) {
    fun toInput() = RoomInput(name, capacity, description)
}
