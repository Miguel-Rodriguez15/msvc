# ====== ETAPA 1: BUILD ======
FROM amazoncorretto:17-alpine-jdk AS builder

WORKDIR /app/msvc-cursos

COPY ./pom.xml /app/
COPY ./msvc-cursos/.mvn ./.mvn
COPY ./msvc-cursos/mvnw .
COPY ./msvc-cursos/pom.xml .

RUN ./mvnw clean package -Dmaven.test.skip \
    -Dmaven.main.skip -Dspring-boot.repackage.skip

COPY ./msvc-cursos/src ./src

RUN ./mvnw clean package -DskipTests

# ====== ETAPA 2: RUNTIME ======
FROM amazoncorretto:17-alpine-jdk

WORKDIR /app

# Buena práctica: Crear usuario no-root
RUN addgroup -S spring && adduser -S spring -G spring

# Copiar JAR desde la etapa de build
COPY --from=builder /app/msvc-cursos/target/msvc-cursos-0.0.1-SNAPSHOT.jar .

EXPOSE 8002

# Buena práctica: Ejecutar como usuario no-root
USER spring

ENTRYPOINT ["java", "-jar", "msvc-cursos-0.0.1-SNAPSHOT.jar"]