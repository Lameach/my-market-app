FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY openapi openapi
COPY payment-service payment-service
COPY my-market-app/pom.xml my-market-app/pom.xml
RUN chmod +x mvnw && ./mvnw -B -DskipTests -pl payment-service -am package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/payment-service/target/payment-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
