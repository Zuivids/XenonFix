# The backend for the server. Built by compose.prod.yaml; run the tests before building.

FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /build
# Dependencies first, so they are downloaded again only when pom.xml changes
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests && cp target/xenonfix-*.jar app.jar

FROM eclipse-temurin:21-jre-noble
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --no-create-home xenonfix \
    && mkdir -p /data/uploads \
    && chown xenonfix /data/uploads
WORKDIR /app
COPY --from=build /build/app.jar app.jar
USER xenonfix
ENV SPRING_PROFILES_ACTIVE=prod \
    APP_UPLOADS_PATH=/data/uploads \
    SERVER_PORT=8090
EXPOSE 8090
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
