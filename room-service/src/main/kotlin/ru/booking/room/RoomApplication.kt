package ru.booking.room

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder

@SpringBootApplication
class RoomApplication

fun main(args: Array<String>) {
    SpringApplicationBuilder(RoomApplication::class.java)
        .properties("spring.config.name=room-application")
        .run(*args)
        .use { context -> context.getBean(RoomGrpcServer::class.java).awaitTermination() }
}
