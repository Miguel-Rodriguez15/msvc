# ====== ETAPA 1: BUILD ======
FROM amazoncorretto:17-alpine-jdk AS builder

WORKDIR /app/msvc-usuarios

COPY ./pom.xml /app
COPY ./msvc-usuarios/.mvn ./.mvn
COPY ./msvc-usuarios/mvnw .
COPY ./msvc-usuarios/pom.xml .

RUN ./mvnw clean package -Dmaven.test.skip \
    -Dmaven.main.skip -Dspring-boot.repackage.skip

COPY ./msvc-usuarios/src ./src

RUN ./mvnw clean package -DskipTests

# ====== ETAPA 2: RUNTIME ======
FROM amazoncorretto:17-alpine-jdk

WORKDIR /app

# Buena práctica: Crear usuario no-root
RUN addgroup -S spring && adduser -S spring -G spring

# Copiar JAR desde la etapa de build
COPY --from=builder /app/msvc-usuarios/target/msvc-usuarios-0.0.1-SNAPSHOT.jar .

EXPOSE 8001

# Buena práctica: Ejecutar como usuario no-root
USER spring

ENTRYPOINT ["java", "-jar", "msvc-usuarios-0.0.1-SNAPSHOT.jar"]