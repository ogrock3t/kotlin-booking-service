FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew
COPY src ./src
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --system booking && useradd --system --gid booking booking
COPY --from=build /app/build/libs/*.jar app.jar
USER booking
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

