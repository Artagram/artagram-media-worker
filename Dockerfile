FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY spotbugs-exclude.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# La marca de agua dibuja texto con AWT, que necesita fuentes instaladas.
RUN apk add --no-cache fontconfig ttf-dejavu
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/target/artagram-media-worker-0.1.0.jar app.jar
RUN chown -R app:app /app
USER app
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "app.jar"]
