FROM eclipse-temurin:25-jre-jammy

WORKDIR /app

RUN mkdir -p /opt/aipub/agents
COPY dockerizer-backend-server/build/libs/*.jar /app/app.jar

ENV USE_SYSTEM_CA_CERTS=1

CMD ["java", "-jar", "/app/app.jar"]
