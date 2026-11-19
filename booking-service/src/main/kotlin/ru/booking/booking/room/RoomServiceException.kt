package ru.booking.booking.room

import io.grpc.Status
import io.grpc.StatusException

class RoomServiceException(
    val code: Status.Code,
    cause: StatusException,
) : RuntimeException("Room service call failed", cause)
