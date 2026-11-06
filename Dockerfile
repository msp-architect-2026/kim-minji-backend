# ---------- build stage ----------
FROM --platform=linux/arm64 eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace

# 의존성 레이어: 빌드 스크립트가 그대로면 gradle 배포판·의존성 다운로드를 kaniko 캐시에서 재사용한다
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x ./gradlew \
 && ./gradlew resolveDependencies --no-daemon -q

COPY src ./src
RUN ./gradlew bootJar -x test --no-daemon

# ---------- runtime stage ----------
FROM --platform=linux/arm64 eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN useradd -m appuser
USER appuser

COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]
