FROM amazoncorretto:17-alpine-jdk AS builder
ARG MSVC_NAME=msvc-gateway
WORKDIR /app/$MSVC_NAME

COPY ./pom.xml /app
COPY ./$MSVC_NAME/.mvn ./.mvn
COPY ./$MSVC_NAME/mvnw .
COPY ./$MSVC_NAME/pom.xml .

RUN ./mvnw clean package -Dmaven.test.skip  -Dmaven.main.skip -Dspring-boot.repackage.skip

COPY ./msvc-gateway/src ./src

RUN ./mvnw clean package -DskipTests

FROM amazoncorretto:17-alpine-jdk

WORKDIR /app
ARG MSVC_NAME=msvc-gateway
ARG TARGET_FOLDER=/app/$MSVC_NAME/target
COPY --from=builder $TARGET_FOLDER/msvc-gateway-0.0.1-SNAPSHOT.jar .
ARG PORT_APP=8090
ENV PORT $PORT_APP
EXPOSE $PORT

CMD ["java", "-jar", "msvc-gateway-0.0.1-SNAPSHOT.jar"]