FROM debian:trixie-slim AS build
RUN apt-get update \
    && apt-get install -y --no-install-recommends openjdk-21-jdk-headless ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew
COPY src ./src
RUN ./gradlew --no-daemon bootJar

FROM debian:trixie-slim AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends openjdk-21-jre-headless ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
RUN groupadd --system booking && useradd --system --gid booking booking
COPY --from=build /app/build/libs/*.jar app.jar
USER booking
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
