FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY backend/ backend/
COPY contracts/ contracts/
WORKDIR /workspace/backend
RUN ./mvnw -B -DskipTests package
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/backend/target/doggycredit-api-*.jar app.jar
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
