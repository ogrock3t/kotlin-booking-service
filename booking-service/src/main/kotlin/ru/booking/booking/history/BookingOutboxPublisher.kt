package ru.booking.booking.history

import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactoryOptions
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
import org.springframework.kafka.core.KafkaAdmin
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.awaitRowsUpdated
import org.springframework.r2dbc.core.flow
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class BookingOutboxPublisher(
    dataSource: DataSourceProperties,
    private val kafka: KafkaTemplate<String, String>,
    private val kafkaAdmin: KafkaAdmin,
    @Value("\${app.kafka.booking-events-topic}") private val topic: String,
    @Value("\${app.outbox.r2dbc-url}") r2dbcUrl: String,
) {
    private val pool = createPool(dataSource, r2dbcUrl)
    private val database = DatabaseClient.create(pool)
    private var topicsReady = false

    suspend fun publishBatch(): Boolean {
        if (!topicsReady) {
            topicsReady = withContext(Dispatchers.IO) { kafkaAdmin.initialize() }
            check(topicsReady) { "Kafka topics are unavailable" }
        }

        var published = false
        pendingMessages().collect { message ->
            publish(message)
            database.sql("DELETE FROM booking_outbox WHERE id = :id")
                .bind("id", message.id)
                .fetch()
                .awaitRowsUpdated()
            published = true
        }
        return published
    }

    @PreDestroy
    fun close() {
        pool.dispose()
    }

    private fun pendingMessages(): Flow<Message> =
        database.sql("SELECT id, booking_id, payload FROM booking_outbox ORDER BY id LIMIT :limit")
            .bind("limit", BATCH_SIZE)
            .map { row, _ ->
                Message(
                    id = checkNotNull(row.get("id", Long::class.javaObjectType)),
                    bookingId = checkNotNull(row.get("booking_id", Long::class.javaObjectType)),
                    payload = checkNotNull(row.get("payload", String::class.java)),
                )
            }
            .flow()
            .buffer(BATCH_SIZE)

    private suspend fun publish(message: Message) {
        try {
            val sent = withContext(Dispatchers.IO) { kafka.send(topic, message.bookingId.toString(), message.payload) }
            sent.await()
        } catch (error: Exception) {
            topicsReady = false
            throw error
        }
    }

    private data class Message(
        val id: Long,
        val bookingId: Long,
        val payload: String,
    )

    private companion object {
        const val BATCH_SIZE = 32

        fun createPool(
            dataSource: DataSourceProperties,
            overrideUrl: String,
        ): ConnectionPool {
            val jdbcAddress = dataSource.determineUrl().removePrefix("jdbc:postgresql:")
            val url =
                overrideUrl.ifBlank {
                    if (jdbcAddress.startsWith("//")) {
                        "r2dbc:postgresql:$jdbcAddress"
                    } else {
                        "r2dbc:postgresql://localhost/$jdbcAddress"
                    }
                }
            val options = ConnectionFactoryOptions.parse(url)
            val configured =
                options.mutate().apply {
                    if (!options.hasOption(ConnectionFactoryOptions.USER)) {
                        option(ConnectionFactoryOptions.USER, dataSource.determineUsername())
                    }
                    if (!options.hasOption(ConnectionFactoryOptions.PASSWORD)) {
                        option(ConnectionFactoryOptions.PASSWORD, dataSource.determinePassword())
                    }
                    option(ConnectionFactoryOptions.CONNECT_TIMEOUT, Duration.ofSeconds(2))
                    option(ConnectionFactoryOptions.STATEMENT_TIMEOUT, Duration.ofSeconds(5))
                }.build()

            return ConnectionPool(
                ConnectionPoolConfiguration.builder(ConnectionFactories.get(configured))
                    .initialSize(0)
                    .maxSize(2)
                    .maxAcquireTime(Duration.ofSeconds(2))
                    .build(),
            )
        }
    }
}
