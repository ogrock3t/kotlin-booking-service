FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew
COPY src ./src
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app
RUN addgroup -S booking && adduser -S -G booking booking
COPY --from=build --chown=booking:booking /app/build/libs/*.jar app.jar
USER booking
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
