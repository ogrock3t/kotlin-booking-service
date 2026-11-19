plugins {
    `java-library`
    kotlin("jvm")
    id("com.google.protobuf")
}

val grpcVersion = "1.72.0"
val grpcKotlinVersion = "1.4.3"
val protobufVersion = "3.25.8"

dependencies {
    api(platform("io.grpc:grpc-bom:$grpcVersion"))
    api("io.grpc:grpc-protobuf")
    api("io.grpc:grpc-stub")
    api("io.grpc:grpc-kotlin-stub:$grpcKotlinVersion")
    api("com.google.protobuf:protobuf-java:$protobufVersion")
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
        }
        create("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:$grpcKotlinVersion:jdk8@jar"
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
