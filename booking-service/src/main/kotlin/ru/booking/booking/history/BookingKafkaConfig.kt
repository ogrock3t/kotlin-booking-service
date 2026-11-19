package ru.booking.booking.history

import com.fasterxml.jackson.core.JsonProcessingException
import org.apache.kafka.clients.admin.NewTopic
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer
import org.springframework.boot.web.context.WebServerGracefulShutdownLifecycle
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.FixedBackOff

@Configuration
class BookingKafkaConfig {
    @Bean
    fun kafkaListenerContainerFactory(
        configurer: ConcurrentKafkaListenerContainerFactoryConfigurer,
        consumerFactory: ConsumerFactory<Any, Any>,
    ): ConcurrentKafkaListenerContainerFactory<Any, Any> =
        ConcurrentKafkaListenerContainerFactory<Any, Any>().apply {
            configurer.configure(this, consumerFactory)
            setPhase(WebServerGracefulShutdownLifecycle.SMART_LIFECYCLE_PHASE - 2)
            containerProperties.isStopImmediate = true
        }

    @Bean
    fun bookingEventsTopic(
        @Value("\${app.kafka.booking-events-topic}") topic: String,
    ): NewTopic = TopicBuilder.name(topic).partitions(1).replicas(1).build()

    @Bean
    fun bookingEventsDeadLetterTopic(
        @Value("\${app.kafka.booking-events-topic}") topic: String,
    ): NewTopic = TopicBuilder.name("$topic.DLT").partitions(1).replicas(1).build()

    @Bean
    fun bookingKafkaErrorHandler(kafka: KafkaTemplate<String, String>): DefaultErrorHandler {
        val deadLetters = DeadLetterPublishingRecoverer(kafka)
        deadLetters.setFailIfSendResultIsError(true)
        return DefaultErrorHandler(deadLetters, FixedBackOff(1_000, FixedBackOff.UNLIMITED_ATTEMPTS)).apply {
            addNotRetryableExceptions(JsonProcessingException::class.java)
        }
    }
}
