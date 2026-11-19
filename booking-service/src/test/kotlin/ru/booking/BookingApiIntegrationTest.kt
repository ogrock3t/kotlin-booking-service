package ru.booking

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.KafkaContainer
import ru.booking.booking.BookingInput
import ru.booking.booking.BookingTransactions
import ru.booking.room.RoomApplication
import ru.booking.room.RoomGrpcServer
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Tag("integration")
@DirtiesContext
@Import(FixedClockConfig::class)
class BookingApiIntegrationTest
    @Autowired
    constructor(
        private val http: TestRestTemplate,
        private val json: ObjectMapper,
        private val jdbc: JdbcTemplate,
        private val transactions: BookingTransactions,
    ) {
        @BeforeEach
        fun cleanBookings() {
            jdbc.execute("TRUNCATE bookings, room_deletions")
        }

        @Test
        fun `Swagger UI documents the public REST operations`() {
            val swagger = get("/swagger-ui/index.html")
            assertEquals(HttpStatus.OK, swagger.statusCode)
            assertTrue(swagger.body.orEmpty().contains("Swagger UI"))

            val response = get("/v3/api-docs")
            assertEquals(HttpStatus.OK, response.statusCode)
            val document = body(response)
            val paths = document["paths"]
            val operations =
                mapOf(
                    "/api/rooms" to setOf("get", "post"),
                    "/api/rooms/{id}" to setOf("get", "put", "delete"),
                    "/api/rooms/{id}/bookings" to setOf("get"),
                    "/api/bookings" to setOf("post"),
                    "/api/bookings/{id}" to setOf("get", "delete"),
                    "/api/bookings/history" to setOf("get"),
                )
            assertEquals(operations.keys, paths.fieldNames().asSequence().toSet())
            operations.forEach { (path, methods) ->
                assertEquals(methods, paths[path].fieldNames().asSequence().toSet(), path)
            }

            val createBooking = paths["/api/bookings"]["post"]
            assertTrue(createBooking["responses"].has("201"))
            assertTrue(createBooking["requestBody"]["content"].has("application/json"))
            val bookingProperties = document["components"]["schemas"]["BookingRequest"]["properties"]
            assertEquals("2030-01-02T10:00:00", bookingProperties["startTime"]["example"].asText())
        }

        @Test
        fun `room and booking lifecycle reaches the Kafka history`() {
            val roomId = createRoom()
            val rooms = get("/api/rooms")
            assertEquals(HttpStatus.OK, rooms.statusCode)
            assertTrue(body(rooms).any { it["id"].asLong() == roomId })

            val update =
                request(
                    HttpMethod.PUT,
                    "/api/rooms/$roomId",
                    """{"name":"102","capacity":15,"description":"Доска"}""",
                )
            assertEquals(HttpStatus.OK, update.statusCode)
            val updated = body(get("/api/rooms/$roomId"))
            assertEquals("102", updated["name"].asText())
            assertEquals(15, updated["capacity"].asInt())
            assertEquals("Доска", updated["description"].asText())

            val created = createBooking(input(roomId))
            assertEquals(HttpStatus.CREATED, created.statusCode)
            val bookingId = body(created)["id"].asLong()
            assertEquals("/api/bookings/$bookingId", created.headers.location?.path)
            assertEquals(1, bookingCount())
            assertEquals(roomId, body(get("/api/bookings/$bookingId"))["roomId"].asLong())
            assertEquals(1, body(get("/api/rooms/$roomId/bookings?date=2030-01-02")).size())
            awaitHistory(bookingId, "CONFIRMED")

            assertEquals(HttpStatus.CONFLICT, delete("/api/rooms/$roomId").statusCode)
            assertEquals(HttpStatus.NO_CONTENT, delete("/api/bookings/$bookingId").statusCode)
            assertEquals(HttpStatus.NOT_FOUND, delete("/api/bookings/$bookingId").statusCode)
            assertEquals(HttpStatus.NOT_FOUND, get("/api/bookings/$bookingId").statusCode)
            assertEquals(0, bookingCount())
            val history = awaitHistory(bookingId, "CANCELLED")
            assertEquals(roomId, history["booking"]["roomId"].asLong())
            assertEquals(HttpStatus.NO_CONTENT, delete("/api/rooms/$roomId").statusCode)
            assertEquals(HttpStatus.NOT_FOUND, get("/api/rooms/$roomId").statusCode)
        }

        @Test
        fun `history returns ten most recent bookings at a time`() {
            val roomId = createRoom()
            val original = input(roomId)
            val ids =
                (0L..11L).map { index ->
                    val result =
                        createBooking(
                            original.copy(startTime = original.startTime.plusHours(index), endTime = original.endTime.plusHours(index)),
                        )
                    assertEquals(HttpStatus.CREATED, result.statusCode)
                    body(result)["id"].asLong()
                }.reversed()

            await().atMost(Duration.ofSeconds(15)).untilAsserted {
                val firstPage = body(get("/api/bookings/history"))
                val nextPage = body(get("/api/bookings/history?page=1"))
                assertEquals(0, firstPage["page"].asInt())
                assertEquals(10, firstPage["size"].asInt())
                assertTrue(firstPage["hasNext"].asBoolean())
                assertEquals(ids.take(10), firstPage["content"].map { it["booking"]["id"].asLong() })
                assertEquals(ids.drop(10), nextPage["content"].take(2).map { it["booking"]["id"].asLong() })
            }
            assertEquals(0, body(get("/api/bookings/history?page=1000"))["content"].size())
            assertEquals(HttpStatus.BAD_REQUEST, get("/api/bookings/history?page=-1").statusCode)
        }

        @Test
        fun `rejects overlapping bookings but allows adjacent bookings and other rooms`() {
            val original = input(createRoom())
            assertEquals(HttpStatus.CREATED, createBooking(original).statusCode)
            val overlaps =
                listOf(
                    original,
                    original.copy(startTime = original.startTime.plusMinutes(10), endTime = original.endTime.minusMinutes(10)),
                    original.copy(startTime = original.startTime.minusHours(1), endTime = original.endTime.plusHours(1)),
                    original.copy(startTime = original.startTime.minusMinutes(30), endTime = original.startTime.plusMinutes(30)),
                    original.copy(startTime = original.endTime.minusMinutes(30), endTime = original.endTime.plusMinutes(30)),
                )
            overlaps.forEach { assertEquals(HttpStatus.CONFLICT, createBooking(it).statusCode) }

            val after = original.copy(startTime = original.endTime, endTime = original.endTime.plusHours(1))
            val before = original.copy(startTime = original.startTime.minusHours(1), endTime = original.startTime)
            assertEquals(HttpStatus.CREATED, createBooking(after).statusCode)
            assertEquals(HttpStatus.CREATED, createBooking(before).statusCode)
            assertEquals(HttpStatus.CREATED, createBooking(original.copy(roomId = createRoom())).statusCode)
        }

        @Test
        fun `schedule includes overnight bookings and excludes the midnight boundary`() {
            val room = createRoom()
            val overnight =
                input(room).copy(
                    startTime = LocalDateTime.parse("2030-01-02T23:00:00"),
                    endTime = LocalDateTime.parse("2030-01-03T01:00:00"),
                )
            val earlier = overnight.copy(startTime = overnight.startTime.minusHours(1), endTime = overnight.startTime)
            assertEquals(HttpStatus.CREATED, createBooking(overnight).statusCode)
            assertEquals(HttpStatus.CREATED, createBooking(earlier).statusCode)
            assertEquals(1, body(get("/api/rooms/$room/bookings?date=2030-01-03")).size())
            assertEquals(0, body(get("/api/rooms/$room/bookings?date=2030-01-04")).size())
        }

        @Test
        fun `returns 400 for invalid requests and 404 for missing resources`() {
            val roomId = createRoom()
            val valid = input(roomId)
            listOf(
                valid.copy(endTime = valid.startTime),
                valid.copy(startTime = valid.startTime.minusYears(1), endTime = valid.endTime.minusYears(1)),
                valid.copy(bookedBy = " "),
                valid.copy(roomId = -1),
                valid.copy(startTime = valid.startTime.plusNanos(1)),
            ).forEach { assertEquals(HttpStatus.BAD_REQUEST, createBooking(it).statusCode) }

            for (invalid in listOf("{}", "{", """{"roomId":null}""", """{"startTime":"bad-date"}""")) {
                val result = request(HttpMethod.POST, "/api/bookings", invalid)
                assertEquals(HttpStatus.BAD_REQUEST, result.statusCode)
                assertTrue(body(result)["message"].asText().isNotBlank())
            }
            for (path in listOf("/api/rooms/$roomId/bookings", "/api/rooms/$roomId/bookings?date=wrong", "/api/rooms/wrong")) {
                assertEquals(HttpStatus.BAD_REQUEST, get(path).statusCode)
            }
            val missingId = Long.MAX_VALUE
            assertEquals(HttpStatus.NOT_FOUND, createBooking(valid.copy(roomId = missingId)).statusCode)
            assertEquals(HttpStatus.NOT_FOUND, get("/api/rooms/$missingId/bookings?date=2030-01-02").statusCode)
            assertEquals(
                HttpStatus.NOT_FOUND,
                request(HttpMethod.PUT, "/api/rooms/$missingId", """{"name":"101","capacity":10}""").statusCode,
            )
            assertEquals(HttpStatus.NOT_FOUND, delete("/api/rooms/$missingId").statusCode)
            assertEquals(0, bookingCount())
        }

        @Test
        fun `two concurrent requests for the same room and time create only one booking`() {
            val booking = input(createRoom())
            val statuses = race({ createBooking(booking).statusCode.value() }, { createBooking(booking).statusCode.value() })
            assertEquals(listOf(201, 409), statuses.sorted())
            assertEquals(1, bookingCount())
        }

        @Test
        fun `deleting a room concurrently with booking never leaves an orphan`() {
            val room = createRoom()
            val statuses =
                race(
                    { createBooking(input(room)).statusCode.value() },
                    { delete("/api/rooms/$room").statusCode.value() },
                )
            assertTrue(statuses == listOf(201, 409) || statuses == listOf(404, 204), "Statuses: $statuses")
            if (bookingCount() == 1) {
                assertEquals(HttpStatus.OK, get("/api/rooms/$room").statusCode)
            } else {
                assertEquals(HttpStatus.NOT_FOUND, get("/api/rooms/$room").statusCode)
            }
        }

        @Test
        fun `room service outage returns 503 and pending deletion resumes after recovery`() {
            val roomId = createRoom()
            val roomToDelete = createRoom()
            roomContext.stop()
            try {
                val response = createBooking(input(roomId))
                assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
                assertEquals("Room service is unavailable", body(response)["message"].asText())
                assertEquals(0, bookingCount())

                // Simulate an interruption after the deletion marker commits but before the gRPC call succeeds.
                transactions.beginRoomDeletion(roomToDelete)
            } finally {
                roomContext.start()
            }

            await().atMost(Duration.ofSeconds(15)).untilAsserted {
                assertEquals(HttpStatus.NOT_FOUND, get("/api/rooms/$roomToDelete").statusCode)
                assertEquals(
                    true,
                    jdbc.queryForObject("SELECT completed FROM room_deletions WHERE room_id = ?", Boolean::class.java, roomToDelete),
                )
            }
            assertEquals(HttpStatus.CREATED, createBooking(input(roomId)).statusCode)
        }

        @Test
        fun `booking remains durable while Kafka is unavailable and history catches up`() {
            val roomId = createRoom()
            val docker = DockerClientFactory.instance().client()
            val bookingId: Long
            docker.pauseContainerCmd(kafka.containerId).exec()
            try {
                val response = createBooking(input(roomId))
                assertEquals(HttpStatus.CREATED, response.statusCode)
                bookingId = body(response)["id"].asLong()
                assertEquals(1, bookingCount())
                assertEquals(1, pendingEvents(bookingId))
            } finally {
                docker.unpauseContainerCmd(kafka.containerId).exec()
            }

            awaitHistory(bookingId, "CONFIRMED")
            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                assertEquals(0, pendingEvents(bookingId))
            }
        }

        private fun pendingEvents(bookingId: Long): Int? =
            jdbc.queryForObject("SELECT count(*) FROM booking_outbox WHERE booking_id = ?", Int::class.java, bookingId)

        private fun awaitHistory(
            id: Long,
            status: String,
        ): JsonNode {
            var result: JsonNode? = null
            await().atMost(Duration.ofSeconds(15)).untilAsserted {
                result = body(get("/api/bookings/history"))["content"].find { it["booking"]["id"].asLong() == id }
                assertEquals(status, result?.get("status")?.asText())
            }
            return requireNotNull(result)
        }

        private fun race(
            first: () -> Int,
            second: () -> Int,
        ): List<Int> {
            val pool = Executors.newFixedThreadPool(2)
            val ready = CountDownLatch(2)
            val start = CountDownLatch(1)
            try {
                val futures =
                    listOf(first, second).map { action ->
                        pool.submit(
                            Callable {
                                ready.countDown()
                                check(start.await(10, TimeUnit.SECONDS))
                                action()
                            },
                        )
                    }
                check(ready.await(10, TimeUnit.SECONDS))
                start.countDown()
                return futures.map { it.get(20, TimeUnit.SECONDS) }
            } finally {
                pool.shutdownNow()
            }
        }

        private fun createRoom(): Long {
            val result = request(HttpMethod.POST, "/api/rooms", """{"name":"101","capacity":20,"description":"Проектор"}""")
            assertEquals(HttpStatus.CREATED, result.statusCode, result.body)
            assertNotNull(result.headers.location)
            return body(result)["id"].asLong()
        }

        private fun input(roomId: Long) =
            BookingInput(
                roomId,
                "Алексей",
                "Семинар",
                LocalDateTime.parse("2030-01-02T10:00:00"),
                LocalDateTime.parse("2030-01-02T11:00:00"),
            )

        private fun createBooking(input: BookingInput) = request(HttpMethod.POST, "/api/bookings", json.writeValueAsString(input))

        private fun bookingCount(): Int? = jdbc.queryForObject("SELECT count(*) FROM bookings", Int::class.java)

        private fun get(path: String): ResponseEntity<String> = http.getForEntity(path, String::class.java)

        private fun delete(path: String) = http.exchange(path, HttpMethod.DELETE, HttpEntity.EMPTY, String::class.java)

        private fun request(
            method: HttpMethod,
            path: String,
            body: String,
        ): ResponseEntity<String> {
            val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
            return http.exchange(path, method, HttpEntity(body, headers), String::class.java)
        }

        private fun body(response: ResponseEntity<String>): JsonNode = json.readTree(response.body)

        companion object {
            @Container
            @JvmField
            val bookingDb = PostgreSQLContainer<Nothing>("postgres:17-alpine")

            @Container
            @JvmField
            val roomDb = PostgreSQLContainer<Nothing>("postgres:17-alpine")

            @Container
            @JvmField
            val kafka = KafkaContainer("apache/kafka:3.9.2")

            private lateinit var roomContext: ConfigurableApplicationContext

            @DynamicPropertySource
            @JvmStatic
            fun services(registry: DynamicPropertyRegistry) {
                roomContext =
                    SpringApplicationBuilder(RoomApplication::class.java)
                        .properties("spring.config.name=room-application")
                        .run(
                            "--spring.datasource.url=${roomDb.jdbcUrl}",
                            "--spring.datasource.username=${roomDb.username}",
                            "--spring.datasource.password=${roomDb.password}",
                            "--app.grpc.port=0",
                        )
                registry.add("spring.datasource.url", bookingDb::getJdbcUrl)
                registry.add("spring.datasource.username", bookingDb::getUsername)
                registry.add("spring.datasource.password", bookingDb::getPassword)
                registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers)
                registry.add("app.rooms.port") { roomContext.getBean(RoomGrpcServer::class.java).port }
            }

            @AfterAll
            @JvmStatic
            fun closeRoomContext() {
                if (::roomContext.isInitialized) roomContext.close()
            }
        }
    }
