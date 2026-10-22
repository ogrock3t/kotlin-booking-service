package ru.booking

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import ru.booking.booking.BookingInput
import java.time.LocalDateTime
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Tag("integration")
@Import(FixedClockConfig::class)
class BookingApiIntegrationTest {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var json: ObjectMapper
    @Autowired lateinit var jdbc: JdbcTemplate

    companion object {
        @Container
        @JvmField
        val postgres = PostgreSQLContainer<Nothing>("postgres:17-alpine")

        @DynamicPropertySource
        @JvmStatic
        fun database(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @BeforeEach
    fun cleanDatabase() {
        jdbc.execute("TRUNCATE TABLE bookings, rooms RESTART IDENTITY CASCADE")
    }

    @Test
    fun `full booking lifecycle with persistence and correct statuses`() {
        val roomId = createRoom()
        mvc.perform(get("/api/rooms")).andExpect(status().isOk).andExpect(jsonPath("$[0].id").value(roomId))
        mvc.perform(get("/api/rooms/$roomId")).andExpect(status().isOk)
        mvc.perform(put("/api/rooms/$roomId").contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"102","capacity":15,"description":"Доска"}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.capacity").value(15))
        mvc.perform(get("/api/rooms/$roomId")).andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("102"))
            .andExpect(jsonPath("$.capacity").value(15))
            .andExpect(jsonPath("$.description").value("Доска"))

        val response = mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(input(roomId))))
            .andExpect(status().isCreated).andExpect(header().exists("Location")).andReturn()
        val bookingId = json.readTree(response.response.contentAsString)["id"].asLong()
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM bookings", Int::class.java))
        mvc.perform(get("/api/bookings/$bookingId")).andExpect(status().isOk)
            .andExpect(jsonPath("$.roomId").value(roomId))
        mvc.perform(get("/api/rooms/$roomId/bookings").param("date", "2030-01-02"))
            .andExpect(status().isOk).andExpect(jsonPath("$.length()").value(1))
        mvc.perform(delete("/api/rooms/$roomId")).andExpect(status().isConflict)
        mvc.perform(delete("/api/bookings/$bookingId")).andExpect(status().isNoContent)
        mvc.perform(delete("/api/bookings/$bookingId")).andExpect(status().isNotFound)
        mvc.perform(get("/api/bookings/$bookingId")).andExpect(status().isNotFound)
        assertEquals(201, createBooking(input(roomId)))
        jdbc.update("DELETE FROM bookings")
        mvc.perform(delete("/api/rooms/$roomId")).andExpect(status().isNoContent)
        mvc.perform(get("/api/rooms/$roomId")).andExpect(status().isNotFound)
    }

    @Test
    fun `rejects all overlap shapes but allows adjacent bookings and other rooms`() {
        val room = createRoom()
        val original = input(room)
        assertEquals(201, createBooking(original))
        val overlaps = listOf(
            original,
            original.copy(startTime = original.startTime.plusMinutes(10), endTime = original.endTime.minusMinutes(10)),
            original.copy(startTime = original.startTime.minusHours(1), endTime = original.endTime.plusHours(1)),
            original.copy(startTime = original.startTime.minusMinutes(30), endTime = original.startTime.plusMinutes(30)),
            original.copy(startTime = original.endTime.minusMinutes(30), endTime = original.endTime.plusMinutes(30)),
        )
        overlaps.forEach { assertEquals(409, createBooking(it)) }
        assertEquals(201, createBooking(original.copy(startTime = original.endTime, endTime = original.endTime.plusHours(1))))
        assertEquals(201, createBooking(original.copy(startTime = original.startTime.minusHours(1), endTime = original.startTime)))
        assertEquals(201, createBooking(original.copy(roomId = createRoom())))
    }

    @Test
    fun `schedule includes overnight booking and excludes midnight boundary`() {
        val room = createRoom()
        val overnight = input(room).copy(
            startTime = LocalDateTime.parse("2030-01-02T23:00:00"),
            endTime = LocalDateTime.parse("2030-01-03T01:00:00"),
        )
        assertEquals(201, createBooking(overnight))
        assertEquals(201, createBooking(overnight.copy(
            startTime = LocalDateTime.parse("2030-01-02T22:00:00"), endTime = overnight.startTime)))
        mvc.perform(get("/api/rooms/$room/bookings").param("date", "2030-01-03"))
            .andExpect(status().isOk).andExpect(jsonPath("$.length()").value(1))
        mvc.perform(get("/api/rooms/$room/bookings").param("date", "2030-01-04"))
            .andExpect(status().isOk).andExpect(content().json("[]"))
    }

    @Test
    fun `invalid requests return 400 and missing resources return 404`() {
        val room = createRoom()
        val valid = input(room)
        listOf(
            valid.copy(endTime = valid.startTime),
            valid.copy(startTime = valid.startTime.minusYears(1), endTime = valid.endTime.minusYears(1)),
            valid.copy(bookedBy = " "), valid.copy(roomId = -1),
            valid.copy(startTime = valid.startTime.plusNanos(1)),
        ).forEach { assertEquals(400, createBooking(it)) }
        listOf("{}", "{", """{"roomId":null}""", """{"startTime":"bad-date"}""").forEach {
            mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(it))
                .andExpect(status().isBadRequest).andExpect(jsonPath("$.message").isNotEmpty)
        }
        mvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":" ","capacity":0}"""))
            .andExpect(status().isBadRequest)
        mvc.perform(get("/api/rooms/$room/bookings")).andExpect(status().isBadRequest)
        mvc.perform(get("/api/rooms/$room/bookings").param("date", "wrong"))
            .andExpect(status().isBadRequest)
        mvc.perform(get("/api/rooms/wrong")).andExpect(status().isBadRequest)
        assertEquals(404, createBooking(valid.copy(roomId = 999)))
        mvc.perform(get("/api/rooms/999/bookings").param("date", "2030-01-02"))
            .andExpect(status().isNotFound)
        mvc.perform(put("/api/rooms/999").contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"101","capacity":10}"""))
            .andExpect(status().isNotFound)
        mvc.perform(delete("/api/rooms/999")).andExpect(status().isNotFound)
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM bookings", Int::class.java))
    }

    @Test
    fun `two concurrent requests create only one booking`() {
        val booking = input(createRoom())
        val statuses = race({ createBooking(booking) }, { createBooking(booking) })
        assertEquals(listOf(201, 409), statuses.sorted())
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM bookings", Int::class.java))
    }

    @Test
    fun `room deletion racing with booking cannot produce orphan or server error`() {
        val room = createRoom()
        val statuses = race(
            { createBooking(input(room)) },
            { mvc.perform(delete("/api/rooms/$room")).andReturn().response.status },
        )
        assertTrue(statuses == listOf(201, 409) || statuses == listOf(404, 204), "Statuses: $statuses")
        assertEquals(0, jdbc.queryForObject("""
            SELECT count(*) FROM bookings b LEFT JOIN rooms r ON b.room_id = r.id WHERE r.id IS NULL
        """.trimIndent(), Int::class.java))
    }

    private fun race(first: () -> Int, second: () -> Int): List<Int> {
        val pool = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        try {
            val futures = listOf(first, second).map { action ->
                pool.submit(Callable {
                    ready.countDown()
                    check(start.await(10, TimeUnit.SECONDS))
                    action()
                })
            }
            check(ready.await(10, TimeUnit.SECONDS))
            start.countDown()
            return futures.map { it.get(20, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
    }

    private fun createRoom(): Long {
        val result = mvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"101","capacity":20,"description":"Проектор"}"""))
            .andExpect(status().isCreated).andExpect(header().exists("Location")).andReturn()
        return json.readTree(result.response.contentAsString)["id"].asLong()
    }

    private fun input(roomId: Long) = BookingInput(
        roomId, "Алексей", "Семинар",
        LocalDateTime.parse("2030-01-02T10:00:00"), LocalDateTime.parse("2030-01-02T11:00:00"),
    )

    private fun createBooking(input: BookingInput): Int =
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(input))).andReturn().response.status
}
