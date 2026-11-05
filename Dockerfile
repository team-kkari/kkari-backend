# syntax=docker/dockerfile:1

# ---- build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 의존성 레이어를 먼저 만들어 소스만 바뀔 때 캐시를 재사용한다
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon dependencies > /dev/null

COPY src src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon bootJar -x test \
 && cp "$(ls build/libs/*.jar | grep -v -- '-plain.jar' | head -n 1)" app.jar

# ---- runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --uid 1001 --no-create-home kkari
COPY --from=build /workspace/app.jar app.jar
USER kkari

EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "app.jar"]
