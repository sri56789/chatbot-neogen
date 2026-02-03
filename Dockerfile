# Multi-stage build for single Spring Boot app with embedded frontend

# Stage 1: Build Next.js frontend
FROM node:18-alpine AS frontend-build
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# Stage 2: Build Spring Boot backend
FROM maven:3.9-eclipse-temurin-17 AS backend-build
WORKDIR /app

# Copy backend source first (creates directory structure)
COPY backend/pom.xml .
COPY backend/src ./src

# Copy frontend build to backend resources (overlays on existing structure)
COPY --from=frontend-build /app/frontend/out src/main/resources/static

# Build the Spring Boot application
RUN mvn clean package -DskipTests

# Stage 3: Runtime
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Install Python for FAISS services
RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 python3-venv python3-pip \
    && rm -rf /var/lib/apt/lists/*

# Create pdfs directory and copy PDFs from local pdfs folder
RUN mkdir -p /app/pdfs
# Copy PDFs into the container (PDFs must be in pdfs/ folder before building)
COPY pdfs/ /app/pdfs/

# Copy catalog pipeline and install Python deps
COPY catalog_pipeline/ /app/catalog_pipeline/
RUN python3 -m pip install --no-cache-dir -r /app/catalog_pipeline/requirements.txt

# Copy the built JAR
COPY --from=backend-build /app/target/*.jar app.jar

# Start script for services + app
COPY start.sh /app/start.sh
RUN chmod +x /app/start.sh

# Expose port
EXPOSE 8080

# Set memory limits
ENV JAVA_OPTS="-Xmx1g -Xms512m"

# Run the application and vector services
ENTRYPOINT ["/app/start.sh"]
