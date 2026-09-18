FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY . .

RUN apt-get update && \
    apt-get install -y maven && \
    mvn clean package -DskipTests

CMD ["sh", "-c", "java -jar target/*.jar"]
