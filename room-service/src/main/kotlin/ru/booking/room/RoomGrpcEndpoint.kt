package ru.booking.room

import com.google.protobuf.Empty
import io.grpc.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.stereotype.Component
import org.springframework.transaction.CannotCreateTransactionException
import ru.booking.contract.rooms.v1.ListRoomsResponse
import ru.booking.contract.rooms.v1.RoomCatalogGrpcKt
import ru.booking.contract.rooms.v1.RoomData
import ru.booking.contract.rooms.v1.RoomDetails
import ru.booking.contract.rooms.v1.RoomId
import ru.booking.contract.rooms.v1.UpdateRoomRequest
import ru.booking.shared.NotFoundException
import ru.booking.shared.ValidationException
import kotlin.coroutines.cancellation.CancellationException

@Component
class RoomGrpcEndpoint(
    private val rooms: RoomService,
) : RoomCatalogGrpcKt.RoomCatalogCoroutineImplBase() {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun listRooms(request: Empty): ListRoomsResponse =
        execute {
            ListRoomsResponse.newBuilder().addAllRooms(rooms.findAll().map { it.toDetails() }).build()
        }

    override suspend fun getRoom(request: RoomId): RoomDetails = execute { rooms.findById(request.id).toDetails() }

    override suspend fun createRoom(request: RoomData): RoomDetails = execute { rooms.create(request.toInput()).toDetails() }

    override suspend fun updateRoom(request: UpdateRoomRequest): RoomDetails =
        execute { rooms.update(request.id, request.data.toInput()).toDetails() }

    override suspend fun deleteRoom(request: RoomId): Empty =
        execute {
            rooms.deleteIfExists(request.id)
            Empty.getDefaultInstance()
        }

    private suspend fun <T> execute(block: () -> T): T =
        withContext(Dispatchers.IO) {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: NotFoundException) {
                throw Status.NOT_FOUND.withDescription(error.message).asException()
            } catch (error: ValidationException) {
                throw Status.INVALID_ARGUMENT.withDescription(error.message).asException()
            } catch (error: Exception) {
                log.error("Room operation failed", error)
                val status =
                    when (error) {
                        is CannotCreateTransactionException, is DataAccessResourceFailureException -> Status.UNAVAILABLE
                        else -> Status.INTERNAL
                    }
                throw status.asException()
            }
        }

    private fun RoomData.toInput() = RoomInput(name, capacity, description)

    private fun Room.toDetails(): RoomDetails =
        RoomDetails.newBuilder()
            .setId(id)
            .setData(RoomData.newBuilder().setName(name).setCapacity(capacity).setDescription(description))
            .build()
}
