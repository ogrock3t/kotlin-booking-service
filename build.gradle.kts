import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    base
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlin.jpa) apply false
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.protobuf) apply false
    alias(libs.plugins.ktlint)
}

allprojects {
    repositories {
        mavenCentral()
    }
}

val ktlintPluginId = libs.plugins.ktlint.get().pluginId
val kotlinJvmPluginId = libs.plugins.kotlin.jvm.get().pluginId

subprojects {
    group = "ru.booking"
    version = "0.0.1-SNAPSHOT"

    apply(plugin = ktlintPluginId)
    configure<KtlintExtension> {
        filter {
            exclude { it.file.invariantSeparatorsPath.contains("/build/generated/") }
        }
    }

    plugins.withId(kotlinJvmPluginId) {
        configure<KotlinJvmProjectExtension> {
            jvmToolchain(21)
            compilerOptions {
                freeCompilerArgs.add("-Xjsr305=strict")
            }
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}

tasks.check {
    dependsOn(subprojects.map { "${it.path}:check" })
}
