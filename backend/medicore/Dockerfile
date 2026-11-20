FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

COPY src src
RUN ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre

RUN addgroup --system spring \
    && adduser --system --ingroup spring --home /app spring \
    && mkdir -p /app/storage/reports \
    && chown -R spring:spring /app

WORKDIR /app

COPY --from=build --chown=spring:spring /workspace/target/medicore-*.jar app.jar

ENV PORT=8080 \
    MEDICORE_REPORT_STORAGE_DIR=/app/storage/reports \
    MEDICORE_DEMO_SEED_ENABLED=false \
    MEDICORE_COOKIE_SECURE=true

VOLUME ["/app/storage/reports"]

EXPOSE 8080

USER spring

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]
