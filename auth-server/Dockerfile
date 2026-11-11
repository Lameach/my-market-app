FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY openapi openapi
COPY auth-server auth-server
COPY my-market-app/pom.xml my-market-app/pom.xml
COPY payment-service/pom.xml payment-service/pom.xml
RUN chmod +x mvnw && ./mvnw -B -DskipTests -pl auth-server -am package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/auth-server/target/auth-server-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 9000
ENTRYPOINT ["java", "-jar", "app.jar"]
