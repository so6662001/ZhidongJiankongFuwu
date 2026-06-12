# 多阶段构建：Maven 构建（含前端 frontend-maven-plugin 构建 Vue）-> JRE 运行
# 注意：构建上下文应为仓库根目录（docker-compose 已配置 context: .）
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY monitor-integration ./monitor-integration
COPY frontend ./frontend
WORKDIR /build/monitor-integration
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
ENV TZ=Asia/Shanghai
COPY --from=build /build/monitor-integration/target/monitor-integration.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
