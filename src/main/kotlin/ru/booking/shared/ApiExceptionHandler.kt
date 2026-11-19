package ru.booking.shared

import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class ApiExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(NotFoundException::class)
    fun notFound(error: NotFoundException) = response(HttpStatus.NOT_FOUND, error.message!!)

    @ExceptionHandler(ConflictException::class)
    fun conflict(error: ConflictException) = response(HttpStatus.CONFLICT, error.message!!)

    @ExceptionHandler(ValidationException::class)
    fun invalid(error: ValidationException) = response(HttpStatus.BAD_REQUEST, error.message!!)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun invalidBody(error: MethodArgumentNotValidException): ResponseEntity<ApiError> {
        val message = error.bindingResult.fieldErrors.joinToString("; ") {
            "${it.field}: ${it.defaultMessage}"
        }
        return response(HttpStatus.BAD_REQUEST, message)
    }

    @ExceptionHandler(
        HttpMessageNotReadableException::class,
        MethodArgumentTypeMismatchException::class,
        MissingServletRequestParameterException::class,
        ConstraintViolationException::class,
    )
    fun malformedRequest() = response(HttpStatus.BAD_REQUEST, "Invalid request parameters or body")

    @ExceptionHandler(org.springframework.dao.DataAccessException::class)
    fun databaseError(error: org.springframework.dao.DataAccessException): ResponseEntity<ApiError> {
        log.error("Database operation failed", error)
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Database operation failed")
    }

    private fun response(status: HttpStatus, message: String) =
        ResponseEntity.status(status).body(ApiError(message))
}

