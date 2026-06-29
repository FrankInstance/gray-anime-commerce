FROM eclipse-temurin:17-jre

ARG SERVICE_NAME
WORKDIR /app
COPY backend/${SERVICE_NAME}/target/${SERVICE_NAME}-0.1.0-SNAPSHOT.jar /app/app.jar

ENV JAVA_OPTS="-Xms128m -Xmx256m"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
