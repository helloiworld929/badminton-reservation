# ===== 运行阶段（JAR 本地打包上传） =====
FROM openjdk:11.0-jre-buster
WORKDIR /app

RUN ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

COPY target/badminton.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
    "-Xms256m", "-Xmx512m", \
    "-XX:+UseG1GC", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
