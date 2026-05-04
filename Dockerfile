# ──────────────────────────────────────────────────────────────────────────────
# Backend (Spring Boot 4 / Java 17) — imagen de producción multi-stage.
#   Stage 1 (build): compila el fat-jar con el wrapper de Gradle (sin tests).
#   Stage 2 (run):   solo el JRE + el jar → imagen final pequeña.
# Contexto de build esperado: la carpeta Backend/ (donde vive este Dockerfile).
#   docker build -t tramites/backend -f Backend/Dockerfile Backend
# ──────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk AS build
WORKDIR /src

# 1) Solo los archivos de build primero → cachea la resolución de dependencias
#    mientras no cambien build.gradle / settings.gradle.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
# Normaliza el wrapper por si viene con CRLF de Windows y hazlo ejecutable.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# 2) Código y empaquetado. -x test: los tests ya se corren en CI/local, no en la
#    imagen (mantiene el build rápido y sin necesidad de Mongo en el contenedor).
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# ──────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre
WORKDIR /app

# Usuario no-root.
RUN useradd -r -u 1001 spring
# bootJar produce un único jar ejecutable en build/libs (no se corre la task `jar`,
# así que no hay *-plain.jar que colisione con el glob).
COPY --from=build /src/build/libs/*.jar app.jar
RUN chown spring:spring app.jar
USER spring

EXPOSE 8080

# MaxRAMPercentage permite que la JVM respete el límite de memoria del contenedor
# (clave en una EC2 chica). Ajusta SPRING_/APP_ por entorno con variables.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=65 -XX:+UseSerialGC"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
