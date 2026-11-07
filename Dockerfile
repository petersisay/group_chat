# Multi-stage Docker build for high efficiency and minimal image size
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app
COPY . .
RUN javac *.java

# Lightweight JRE runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/*.class ./
COPY --from=builder /app/web ./web

EXPOSE 8080 5000 5001/udp 5002/udp

ENV PORT=8080
CMD ["java", "GroupChatServer"]
