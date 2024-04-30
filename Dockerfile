FROM eclipse-temurin:21
RUN mkdir /opt/app
COPY /target/TwitchClient-1.0-SNAPSHOT-FULL.jar /opt/app/TwitchBot.jar
CMD ["java", "-jar", "/opt/app/TwitchBot.jar"]