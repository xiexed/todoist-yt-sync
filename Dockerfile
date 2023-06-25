FROM openjdk:11-jre
ADD target/todoist-sync-0.1.0-SNAPSHOT-standalone.jar app.jar
EXPOSE 3000
ENTRYPOINT ["java","-jar","app.jar"]