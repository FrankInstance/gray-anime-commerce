FROM eclipse-temurin:21-jre

ARG SERVICE_NAME
WORKDIR /app
COPY backend/${SERVICE_NAME}/target/${SERVICE_NAME}-0.1.0-SNAPSHOT.jar /app/app.jar

ENV JAVA_OPTS="-Xms128m -Xmx256m"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "if [ -n \"$NACOS_ADDR\" ]; then echo \"Waiting for Nacos at $NACOS_ADDR\"; until wget -q -O - \"http://$NACOS_ADDR/nacos/v1/console/health/readiness\" | grep -q OK; do sleep 3; done; fi; java $JAVA_OPTS -jar /app/app.jar"]
