FROM maven:3.10-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY backend/pom.xml ./
COPY backend/src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/target/shoe-commerce-2.0.0-SNAPSHOT.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
