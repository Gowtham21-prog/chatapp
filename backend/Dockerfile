# Multi-stage Dockerfile for building and running the Spring Boot backend
# Build stage uses Eclipse Temurin JDK 21 and installs Maven to build the project
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

# Copy only pom first to leverage Docker layer caching for dependencies
COPY pom.xml .
COPY src ./src

# Install maven and build the application (skip tests to speed up CI builds)
RUN apt-get update \
    && apt-get install -y maven \
    && mvn -B clean package -DskipTests

# Runtime stage uses a smaller JRE image
FROM eclipse-temurin:21-jre

WORKDIR /app

# Allow overriding the JAR name at build time if needed
ARG JAR_FILE=target/*.jar

# Copy the built jar from the build stage
COPY --from=build /app/target/*.jar app.jar

# Expose the default Spring Boot port (change if your app uses another port)
EXPOSE 8080

# Allow optional extra JVM options via the JAVA_OPTS env var
ENV JAVA_OPTS="-Xms256m -Xmx512m"

# Use sh -c to allow expanding JAVA_OPTS
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
