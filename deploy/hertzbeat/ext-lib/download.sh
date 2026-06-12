#!/usr/bin/env bash
# 下载 MySQL JDBC 驱动到本目录，供 HertzBeat 连接 MySQL 使用。
# HertzBeat 官方镜像默认不内置 MySQL 驱动，需放到 /opt/hertzbeat/ext-lib（本目录已挂载到该路径）。
set -e

MYSQL_DRIVER_VERSION="${MYSQL_DRIVER_VERSION:-8.3.0}"
JAR="mysql-connector-j-${MYSQL_DRIVER_VERSION}.jar"
URL="https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/${MYSQL_DRIVER_VERSION}/${JAR}"

cd "$(dirname "$0")"
if [ -f "$JAR" ]; then
  echo "已存在: $JAR"
  exit 0
fi

echo "下载 $URL"
if command -v curl >/dev/null 2>&1; then
  curl -fSL -o "$JAR" "$URL"
else
  wget -O "$JAR" "$URL"
fi
echo "完成: $(pwd)/$JAR"
