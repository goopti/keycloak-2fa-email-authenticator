FROM maven:3.9-eclipse-temurin-17 as builder
WORKDIR /app

ADD . .

RUN mvn package

FROM scratch as release

WORKDIR /app

COPY --from=builder /app/target/keycloak-2fa-email-authenticator-1.0.0.0-SNAPSHOT.jar /app/keycloak-2fa-email-authenticator.jar
COPY --from=builder /app/src/main/resources/theme/email-code-theme /app/themes/base/
