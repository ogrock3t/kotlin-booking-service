package ru.booking.room

import io.grpc.Server
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class RoomGrpcServer(
    @Value("\${app.grpc.port}") configuredPort: Int,
    private val endpoint: RoomGrpcEndpoint,
) : SmartLifecycle {
    private val log = LoggerFactory.getLogger(javaClass)
    private var server: Server? = null

    private var listenPort = configuredPort

    val port: Int
        get() = listenPort

    override fun start() {
        val started = NettyServerBuilder.forPort(port).addService(endpoint).build().start()
        server = started
        listenPort = started.port
        log.info("Room gRPC server listening on port {}", port)
    }

    override fun isRunning(): Boolean = server != null

    fun awaitTermination() {
        checkNotNull(server).awaitTermination()
    }

    override fun stop() {
        val runningServer = server ?: return
        runningServer.shutdown()
        try {
            if (!runningServer.awaitTermination(10, TimeUnit.SECONDS)) runningServer.shutdownNow()
        } catch (_: InterruptedException) {
            runningServer.shutdownNow()
            Thread.currentThread().interrupt()
        } finally {
            server = null
        }
    }
}
