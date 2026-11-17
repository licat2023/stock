# Builds and tests the API, then runs the packaged service on Java 21.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp package

FROM eclipse-temurin:21-jre-noble
WORKDIR /app
COPY --from=build /build/target/stock-backend.jar ./stock-backend.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/stock-backend.jar"]
