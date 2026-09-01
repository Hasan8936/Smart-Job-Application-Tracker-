FROM maven:3.9.5-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml ./
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy
# PDFBox (resume PDF export) initializes AWT's font subsystem even for its built-in
# standard-14 fonts; the jre-jammy base has no fontconfig/fonts, which makes that
# initialization throw. Without this, every PDF export fails with a 500.
RUN apt-get update && apt-get install -y --no-install-recommends fontconfig fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
