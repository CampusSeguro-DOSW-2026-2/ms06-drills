# ---- Build stage: compiles the project and creates the executable jar ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy only pom.xml first so the dependency layer is cached between builds.
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
COPY database ./database
# Tests already ran in the CI job "backend-quality", so they are skipped here.
RUN mvn -B -ntp clean package -DskipTests

# ---- Runtime stage: small JRE image, runs as a non-root user ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/target/*.jar app.jar
USER app

EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
