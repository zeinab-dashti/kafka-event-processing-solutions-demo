# Dockerfile
###############################################################################
# Stage 0: Maven builder — builds your fat-jar inside Docker
###############################################################################
FROM maven:3.8.4-openjdk-17 AS builder
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

###############################################################################
# Stage 1: Kafka Connect with JDBC Sink
###############################################################################
FROM confluentinc/cp-kafka-connect:7.4.0 AS connect-base
RUN confluent-hub install --no-prompt confluentinc/kafka-connect-jdbc:latest
ENV CONNECT_PLUGIN_PATH=/usr/share/java,/usr/share/confluent-hub-components

###############################################################################
# Stage 2: Your Spring-Boot app
###############################################################################
FROM eclipse-temurin:17-jdk-jammy AS app-base
COPY --from=builder /build/target/*.jar /app/app.jar
WORKDIR /app
ENTRYPOINT ["java","-jar","app.jar"]
