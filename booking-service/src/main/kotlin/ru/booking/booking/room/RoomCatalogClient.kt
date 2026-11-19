package ru.booking.booking.room

import com.google.protobuf.Empty
import io.grpc.ManagedChannelBuilder
import io.grpc.Status
import io.grpc.StatusException
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.delay
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import ru.booking.contract.rooms.v1.RoomCatalogGrpcKt.RoomCatalogCoroutineStub
import ru.booking.contract.rooms.v1.RoomData
import ru.booking.contract.rooms.v1.RoomDetails
import ru.booking.contract.rooms.v1.RoomId
import ru.booking.contract.rooms.v1.UpdateRoomRequest
import ru.booking.room.Room
import ru.booking.room.RoomInput
import ru.booking.shared.NotFoundException
import ru.booking.shared.ValidationException
import java.util.concurrent.TimeUnit

@Component
class RoomCatalogClient(
    @Value("\${app.rooms.host}") host: String,
    @Value("\${app.rooms.port}") port: Int,
    @Value("\${app.rooms.timeout-ms}") private val timeoutMillis: Long,
) {
    private val channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build()
    private val stub = RoomCatalogCoroutineStub(channel)

    suspend fun findAll(): List<Room> = call(retry = true) { listRooms(Empty.getDefaultInstance()).roomsList.map { it.toRoom() } }

    suspend fun findById(id: Long): Room = call(retry = true) { getRoom(roomId(id)).toRoom() }

    suspend fun create(input: RoomInput): Room = call(retry = false) { createRoom(input.toData()).toRoom() }

    suspend fun update(
        id: Long,
        input: RoomInput,
    ): Room =
        call(retry = false) {
            updateRoom(UpdateRoomRequest.newBuilder().setId(id).setData(input.toData()).build()).toRoom()
        }

    suspend fun deleteIfExists(id: Long) {
        call(retry = true) { deleteRoom(roomId(id)) }
    }

    private suspend fun <T> call(
        retry: Boolean,
        operation: suspend RoomCatalogCoroutineStub.() -> T,
    ): T {
        try {
            return stub.withDeadlineAfter(timeoutMillis, TimeUnit.MILLISECONDS).operation()
        } catch (error: StatusException) {
            if (!retry || error.status.code !in retryableStatuses) throw translate(error)
        }

        delay(100)
        try {
            return stub.withDeadlineAfter(timeoutMillis, TimeUnit.MILLISECONDS).operation()
        } catch (error: StatusException) {
            throw translate(error)
        }
    }

    private fun translate(error: StatusException): RuntimeException =
        when (error.status.code) {
            Status.Code.NOT_FOUND -> NotFoundException(error.status.description ?: "Room not found")
            Status.Code.INVALID_ARGUMENT -> ValidationException(error.status.description ?: "Invalid room data")
            else -> RoomServiceException(error.status.code, error)
        }

    @PreDestroy
    fun close() {
        channel.shutdown()
        try {
            if (!channel.awaitTermination(3, TimeUnit.SECONDS)) channel.shutdownNow()
        } catch (_: InterruptedException) {
            channel.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }

    private fun roomId(id: Long): RoomId = RoomId.newBuilder().setId(id).build()

    private fun RoomInput.toData(): RoomData = RoomData.newBuilder().setName(name).setCapacity(capacity).setDescription(description).build()

    private fun RoomDetails.toRoom() = Room(id, data.name, data.capacity, data.description)

    private companion object {
        val retryableStatuses = setOf(Status.Code.UNAVAILABLE, Status.Code.DEADLINE_EXCEEDED)
    }
}
