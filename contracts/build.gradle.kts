plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.protobuf)
}

dependencies {
    api(platform(libs.grpc.bom))
    api(libs.grpc.protobuf)
    api(libs.grpc.stub)
    api(libs.grpc.kotlin.stub)
    api(libs.protobuf.java)
    compileOnly(libs.javax.annotation)
}

protobuf {
    protoc {
        artifact = libs.protobuf.compiler.get().toString()
    }
    plugins {
        create("grpc") {
            artifact = libs.grpc.generator.java.get().toString()
        }
        create("grpckt") {
            artifact = "${libs.grpc.generator.kotlin.get()}:jdk8@jar"
        }
    }
    generateProtoTasks {
        ofSourceSet("main").forEach { task ->
            task.plugins {
                create("grpc")
                create("grpckt")
            }
        }
    }
}
