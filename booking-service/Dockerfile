# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /app
COPY --chmod=755 gradlew ./
COPY settings.gradle.kts build.gradle.kts .editorconfig ./
COPY gradle ./gradle
COPY contracts/build.gradle.kts ./contracts/
COPY room-service/build.gradle.kts ./room-service/
COPY booking-service/build.gradle.kts ./booking-service/
COPY contracts/src ./contracts/src
COPY booking-service/src ./booking-service/src
RUN --mount=type=cache,id=kotlin-booking-gradle,target=/root/.gradle,sharing=locked \
    ./gradlew --no-daemon :booking-service:bootJar

FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app
RUN addgroup -S booking && adduser -S -G booking booking
COPY --from=build --chown=booking:booking /app/booking-service/build/libs/app.jar app.jar
USER booking
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
