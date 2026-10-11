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
# Sized for a 512 MB, 0.1 CPU instance (Render's free plan):
# - a 40% heap with the serial collector leaves room for classes, code and thread stacks;
# - MALLOC_ARENA_MAX=2 stops glibc giving every thread its own memory pool, which otherwise grows
#   the process well past the heap and gets it killed a few minutes after start;
# - C1-only compilation (TieredStopAtLevel=1) needs less code cache and far less CPU to warm up;
# - on an OutOfMemoryError the JVM exits at once, so the host restarts it cleanly.
ENV MALLOC_ARENA_MAX=2
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=40", "-XX:+UseSerialGC", "-Xss512k", "-XX:TieredStopAtLevel=1", "-XX:ReservedCodeCacheSize=32m", "-XX:MaxMetaspaceSize=160m", "-XX:MaxDirectMemorySize=32m", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
