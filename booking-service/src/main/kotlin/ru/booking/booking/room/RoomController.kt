package ru.booking.booking.room

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.booking.room.Room
import java.net.URI

@RestController
@RequestMapping("/api/rooms")
@Tag(name = "Помещения")
class RoomController(
    private val rooms: RoomCatalogClient,
    private val deletions: RoomDeletionService,
) {
    @GetMapping
    @Operation(summary = "Получить список помещений")
    suspend fun findAll(): List<Room> = rooms.findAll()

    @GetMapping("/{id}")
    @Operation(summary = "Получить помещение по ID")
    suspend fun findById(
        @PathVariable id: Long,
    ): Room = rooms.findById(id)

    @PostMapping
    @Operation(summary = "Создать помещение")
    @ApiResponse(responseCode = "201", description = "Помещение создано", useReturnTypeSchema = true)
    suspend fun create(
        @Valid @RequestBody request: RoomRequest,
    ): ResponseEntity<Room> {
        val room = rooms.create(request.toInput())
        return ResponseEntity.created(URI.create("/api/rooms/${room.id}")).body(room)
    }

    @PutMapping("/{id}")
    @Operation(summary = "Изменить помещение")
    suspend fun update(
        @PathVariable id: Long,
        @Valid @RequestBody request: RoomRequest,
    ): Room = rooms.update(id, request.toInput())

    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить помещение без бронирований")
    @ApiResponse(responseCode = "204", description = "Помещение удалено", content = [Content()])
    suspend fun delete(
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        deletions.delete(id)
        return ResponseEntity.noContent().build()
    }
}
