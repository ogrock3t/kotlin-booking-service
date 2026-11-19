package ru.booking.shared

import io.grpc.Status
import jakarta.validation.ConstraintViolationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.reset
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.booking.booking.BookingController
import ru.booking.booking.BookingService
import ru.booking.booking.room.RoomController
import ru.booking.booking.room.RoomService
import ru.booking.booking.room.RoomServiceException

@WebMvcTest(controllers = [RoomController::class, BookingController::class])
@Import(ApiExceptionHandlerTest.MockServices::class)
class ApiExceptionHandlerTest
    @Autowired
    constructor(
        private val mvc: MockMvc,
        private val roomService: RoomService,
        private val bookingService: BookingService,
    ) {
        @BeforeEach
        fun resetMocks() {
            reset(roomService, bookingService)
        }

        @Test
        fun `domain exceptions are converted to their API statuses and messages`() {
            val cases =
                listOf(
                    NotFoundException("Room not found") to 404,
                    ConflictException("Room is busy") to 409,
                    ValidationException("Room is invalid") to 400,
                )

            cases.forEach { (error, expectedStatus) ->
                runBlocking { doThrow(error).`when`(bookingService).findById(1) }

                performAsyncGet("/api/bookings/1")
                    .andExpect(status().`is`(expectedStatus))
                    .andExpect(jsonPath("$.message").value(error.message))

                reset(bookingService)
            }
        }

        @Test
        fun `invalid request body returns field validation errors`() {
            mvc.perform(
                post("/api/rooms")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":" ","capacity":0}"""),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("name:")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("capacity:")))
        }

        @Test
        fun `malformed JSON type mismatch missing parameter and constraint violation return 400`() {
            val genericMessage = "Invalid request parameters or body"

            mvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value(genericMessage))

            mvc.perform(get("/api/rooms/not-a-number"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value(genericMessage))

            mvc.perform(get("/api/rooms/1/bookings"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value(genericMessage))

            runBlocking { doThrow(ConstraintViolationException(emptySet())).`when`(bookingService).findById(1) }
            performAsyncGet("/api/bookings/1")
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value(genericMessage))
        }

        @Test
        fun `database exception returns safe 500 response`() {
            runBlocking {
                doThrow(DataAccessResourceFailureException("connection details")).`when`(bookingService).findById(1)
            }

            performAsyncGet("/api/bookings/1")
                .andExpect(status().isInternalServerError)
                .andExpect(jsonPath("$.message").value("Database operation failed"))
        }

        @Test
        fun `gRPC infrastructure errors map to safe HTTP responses`() {
            val cases = mapOf(Status.UNAVAILABLE to 503, Status.DEADLINE_EXCEEDED to 504, Status.INTERNAL to 502)
            for ((grpc, httpStatus) in cases) {
                val error = RoomServiceException(grpc.code, grpc.asException())
                runBlocking { doThrow(error).`when`(roomService).findById(1) }

                performAsyncGet("/api/rooms/1").andExpect(status().`is`(httpStatus))
                reset(roomService)
            }
        }

        private fun performAsyncGet(path: String): ResultActions {
            val result = mvc.perform(get(path)).andExpect(request().asyncStarted()).andReturn()
            return mvc.perform(asyncDispatch(result))
        }

        @TestConfiguration
        class MockServices {
            @Bean
            fun roomService(): RoomService = mock(RoomService::class.java)

            @Bean
            fun bookingService(): BookingService = mock(BookingService::class.java)
        }
    }
