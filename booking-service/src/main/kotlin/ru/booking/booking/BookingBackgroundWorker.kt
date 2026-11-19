package ru.booking.booking

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import org.springframework.boot.web.context.WebServerGracefulShutdownLifecycle
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component
import ru.booking.booking.history.BookingOutbox
import ru.booking.booking.room.RoomDeletionService
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Component
class BookingBackgroundWorker(
    private val outbox: BookingOutbox,
    private val deletions: RoomDeletionService,
) : SmartLifecycle {
    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var running = false
    private var jobs = emptyList<Job>()

    override fun start() {
        running = true
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        jobs =
            listOf(
                scope.launch { work("publish booking events", 250.milliseconds, outbox::publishNext) },
                scope.launch {
                    work("finish room deletions", 2.seconds) {
                        deletions.resumePending()
                        false
                    }
                },
            )
    }

    override fun isRunning(): Boolean = running

    override fun getPhase(): Int = WebServerGracefulShutdownLifecycle.SMART_LIFECYCLE_PHASE - 1

    override fun stop() {
        running = false
        runBlocking {
            if (withTimeoutOrNull(10.seconds) { jobs.joinAll() } == null) {
                jobs.forEach { it.cancel() }
                withTimeoutOrNull(2.seconds) { jobs.joinAll() }
            }
        }
    }

    private suspend fun work(
        name: String,
        idleDelay: Duration,
        action: suspend () -> Boolean,
    ) {
        while (running) {
            try {
                if (!action()) delay(idleDelay)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                log.warn("Failed to {}; will retry", name, error)
                delay(2.seconds)
            }
        }
    }
}
