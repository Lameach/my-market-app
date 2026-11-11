FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY openapi openapi
COPY my-market-app my-market-app
COPY payment-service/pom.xml payment-service/pom.xml
COPY auth-server/pom.xml auth-server/pom.xml
RUN chmod +x mvnw && ./mvnw -B -DskipTests -pl my-market-app -am package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/my-market-app/target/my-market-app-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
