# Builds and runs the HandyAI API. Used by hosts such as Render, Railway or Fly.io; Vercel only
# serves the frontend and cannot run a Java server.

FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B package -DskipTests && cp target/handy-ai-*.jar app.jar

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 10001 handyai
COPY --from=build /app/app.jar app.jar
USER handyai
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
# Sized for a 512 MB instance (Render's free plan). Classes, compiled code and thread stacks need
# about 200 MB outside the heap, so a 75% heap let the process grow past the limit under load and
# get killed. A 40% heap with the serial collector keeps the worst case near 400 MB.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=40", "-XX:+UseSerialGC", "-Xss512k", "-XX:ReservedCodeCacheSize=64m", "-XX:MaxMetaspaceSize=160m", "-jar", "app.jar"]
