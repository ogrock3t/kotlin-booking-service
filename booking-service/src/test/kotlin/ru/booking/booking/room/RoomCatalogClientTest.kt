package ru.booking.booking.room

import io.grpc.Status
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import ru.booking.contract.rooms.v1.RoomCatalogGrpcKt
import ru.booking.contract.rooms.v1.RoomData
import ru.booking.contract.rooms.v1.RoomDetails
import ru.booking.contract.rooms.v1.RoomId
import ru.booking.room.RoomInput
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class RoomCatalogClientTest {
    @Test
    fun `retries an unavailable read but sends a non-idempotent write only once`() {
        val reads = AtomicInteger()
        val writes = AtomicInteger()
        val endpoint =
            object : RoomCatalogGrpcKt.RoomCatalogCoroutineImplBase() {
                override suspend fun getRoom(request: RoomId): RoomDetails {
                    if (reads.incrementAndGet() == 1) throw Status.UNAVAILABLE.asException()
                    return room(request.id)
                }

                override suspend fun createRoom(request: RoomData): RoomDetails {
                    writes.incrementAndGet()
                    throw Status.UNAVAILABLE.asException()
                }
            }

        withClient(endpoint, 2_000) { client ->
            assertEquals(1L, runBlocking { client.findById(1).id })
            val error =
                assertThrows(RoomServiceException::class.java) {
                    runBlocking { client.create(RoomInput("Room", 10, "")) }
                }
            assertEquals(Status.Code.UNAVAILABLE, error.code)
            assertEquals(2, reads.get())
            assertEquals(1, writes.get())
        }
    }

    @Test
    fun `enforces the deadline on both read attempts`() {
        val slowReads = AtomicInteger()
        val endpoint =
            object : RoomCatalogGrpcKt.RoomCatalogCoroutineImplBase() {
                override suspend fun getRoom(request: RoomId): RoomDetails {
                    if (request.id == 2L) {
                        slowReads.incrementAndGet()
                        delay(10_000)
                    }
                    return room(request.id)
                }
            }

        withClient(endpoint, 750) { client ->
            assertEquals(1L, runBlocking { client.findById(1).id })
            val error =
                assertThrows(RoomServiceException::class.java) {
                    runBlocking { client.findById(2) }
                }
            assertEquals(Status.Code.DEADLINE_EXCEEDED, error.code)
            assertEquals(2, slowReads.get())
        }
    }

    private fun withClient(
        endpoint: RoomCatalogGrpcKt.RoomCatalogCoroutineImplBase,
        timeoutMillis: Long,
        test: (RoomCatalogClient) -> Unit,
    ) {
        val server = NettyServerBuilder.forPort(0).addService(endpoint).build().start()
        val client = RoomCatalogClient("localhost", server.port, timeoutMillis)
        try {
            test(client)
        } finally {
            client.close()
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    private fun room(id: Long): RoomDetails =
        RoomDetails.newBuilder()
            .setId(id)
            .setData(RoomData.newBuilder().setName("Room").setCapacity(10))
            .build()
}
