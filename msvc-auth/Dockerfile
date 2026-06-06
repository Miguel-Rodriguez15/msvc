FROM amazoncorretto:17-alpine-jdk AS builder

WORKDIR /app/msvc-auth

COPY ./pom.xml /app/
COPY ./msvc-auth/.mvn ./.mvn
COPY ./msvc-auth/mvnw .
COPY ./msvc-auth/pom.xml .

RUN ./mvnw clean package -Dmaven.test.skip -Dmaven.main.skip -Dspring-boot.repackage.skip

COPY ./msvc-auth/src ./src

RUN ./mvnw clean package -DskipTests

FROM amazoncorretto:17-alpine-jdk

WORKDIR /app

COPY --from=builder /app/msvc-auth/target/msvc-auth-0.0.1-SNAPSHOT.jar .

EXPOSE 9000

CMD ["java", "-jar", "msvc-auth-0.0.1-SNAPSHOT.jar"]