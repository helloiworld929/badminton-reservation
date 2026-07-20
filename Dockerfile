# ===== 构建阶段：在镜像内完成 Maven 打包 =====
FROM maven:3.9-eclipse-temurin-11 AS builder
WORKDIR /build

COPY pom.xml .
COPY src ./src
RUN mvn -q package

# ===== 运行阶段 =====
FROM eclipse-temurin:11-jre-jammy
WORKDIR /app

RUN ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

COPY --from=builder /build/target/badminton.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
    "-Xms256m", "-Xmx512m", \
    "-XX:+UseG1GC", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
