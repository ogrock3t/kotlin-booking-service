package ru.booking.booking.room

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import ru.booking.booking.BookingTransactions
import kotlin.coroutines.cancellation.CancellationException

@Service
class RoomDeletionService(
    private val rooms: RoomCatalogClient,
    private val transactions: BookingTransactions,
    private val bookingRooms: BookingRoomRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun delete(id: Long) {
        rooms.findById(id)
        withContext(Dispatchers.IO) { transactions.beginRoomDeletion(id) }
        finishDeletion(id)
    }

    suspend fun resumePending() {
        for (roomId in bookingRooms.pendingDeletions()) {
            try {
                finishDeletion(roomId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                log.warn("Room deletion is still pending, roomId={}", roomId, error)
            }
        }
    }

    private suspend fun finishDeletion(id: Long) {
        rooms.deleteIfExists(id)
        withContext(Dispatchers.IO) { bookingRooms.markDeleted(id) }
    }
}
