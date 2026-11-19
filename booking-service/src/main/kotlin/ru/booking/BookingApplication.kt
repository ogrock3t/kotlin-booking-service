package ru.booking

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.servers.Server
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import java.time.Clock
import java.time.ZoneId

@SpringBootApplication(
    scanBasePackages = ["ru.booking.booking", "ru.booking.shared"],
    exclude = [R2dbcAutoConfiguration::class],
)
@AutoConfigurationPackage(basePackages = ["ru.booking.booking"])
@OpenAPIDefinition(
    info = Info(title = "Бронирование помещений", version = "1.0"),
    servers = [Server(url = "/")],
)
class BookingApplication {
    @Bean
    fun clock(
        @Value("\${app.time-zone}") timeZone: String,
    ): Clock = Clock.system(ZoneId.of(timeZone))
}

fun main(args: Array<String>) {
    runApplication<BookingApplication>(*args)
}
