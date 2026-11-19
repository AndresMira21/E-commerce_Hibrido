# Construir con el directorio de este microservicio como contexto.
FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN mvn -B -ntp verify

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S -g 10001 app && adduser -S -D -u 10001 -G app app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/target/billing-worker-0.0.1-SNAPSHOT.jar /app/app.jar
USER app:app
# Deja margen para metaspace, hilos, buffers y librerias nativas.
# MinRAMPercentage controla tambien el heap maximo con limites pequenos.
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=50.0 -XX:MinRAMPercentage=50.0 -XX:InitialRAMPercentage=15.0 -Xss512k"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
