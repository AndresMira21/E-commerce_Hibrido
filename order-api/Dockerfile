# Construir con el directorio order-api como contexto.
FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN mvn -B -ntp verify

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S -g 10001 app && adduser -S -D -u 10001 -G app app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/target/order-api-0.0.1-SNAPSHOT.jar /app/app.jar
USER app:app
# Deja margen para metaspace, hilos, buffers y librerias nativas.
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=50.0 -XX:MinRAMPercentage=50.0 -XX:InitialRAMPercentage=15.0 -Xss512k"
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
