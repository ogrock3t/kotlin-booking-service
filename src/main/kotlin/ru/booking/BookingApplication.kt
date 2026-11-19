package ru.booking

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import java.time.Clock
import java.time.ZoneId
import org.springframework.beans.factory.annotation.Value

@SpringBootApplication
class BookingApplication {
    @Bean
    fun clock(@Value("\${app.time-zone}") timeZone: String): Clock =
        Clock.system(ZoneId.of(timeZone))
}

fun main(args: Array<String>) {
    runApplication<BookingApplication>(*args)
}

