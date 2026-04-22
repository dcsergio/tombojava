# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

COPY gradlew gradlew.bat build.gradle settings.gradle gradle.properties ./
COPY gradle ./gradle
COPY src ./src

RUN chmod +x ./gradlew && ./gradlew --no-daemon clean bootJar

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

ENV JAVA_OPTS="" \
    PORT=8080 \
    TOMBOJAVA_OUTPUT_DIR=/tmp/tombojava/out

COPY --from=build /workspace/build/libs/*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "mkdir -p \"${TOMBOJAVA_OUTPUT_DIR}\" && exec java ${JAVA_OPTS} -Dserver.port=${PORT:-8080} -Dtombojava.output-dir=${TOMBOJAVA_OUTPUT_DIR} -jar /app/app.jar"]

