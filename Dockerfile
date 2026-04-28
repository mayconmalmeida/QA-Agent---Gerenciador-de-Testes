FROM eclipse-temurin:17-jdk-alpine

# Set working directory
WORKDIR /app

# Copy Maven wrapper and pom.xml
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download dependencies
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src ./src
COPY gui ./gui

# Build the project
RUN ./mvnw clean package -DskipTests

# Create data directory for persistence
RUN mkdir -p /app/data

# Expose port
EXPOSE 8080

# Set environment variables
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Run the application
CMD ["sh", "-c", "java -cp target/classes:target/dependency/* br.com.qasuite.server.GuiServer"]
