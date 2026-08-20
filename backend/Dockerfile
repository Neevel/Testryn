FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S testryn && adduser -S testryn -G testryn
COPY --from=build /build/target/testryn-backend.jar app.jar
RUN mkdir -p /data/reports && chown -R testryn:testryn /data/reports
USER testryn
ENV TESTRYN_STORAGE_BASE_PATH=/data/reports
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
