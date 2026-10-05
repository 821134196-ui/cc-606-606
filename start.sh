#!/usr/bin/env bash
# 社区药箱过期药回收与分类交接系统 —— 一条命令启动
# 自动：定位 Java/Maven → 构建 Svelte 前端 → 打包 Quarkus → 初始化 SQLite → 启动服务
set -euo pipefail
cd "$(dirname "$0")"
ROOT="$(pwd)"

# ---------- 1. 定位 JDK 17+ ----------
if ! command -v java >/dev/null 2>&1; then
  if [ -z "${JAVA_HOME:-}" ]; then
    JDK_DIR="$(ls -d "$HOME"/tools/jdk-* 2>/dev/null | head -1 || true)"
    [ -n "$JDK_DIR" ] && export JAVA_HOME="$JDK_DIR"
  fi
  [ -n "${JAVA_HOME:-}" ] && export PATH="$JAVA_HOME/bin:$PATH"
fi
if ! command -v java >/dev/null 2>&1; then
  echo "未找到 Java 17+，请安装 JDK 17/21 或设置 JAVA_HOME"; exit 1
fi
echo "[1/4] Java: $(java -version 2>&1 | head -1)"

# ---------- 2. 定位 Maven ----------
if ! command -v mvn >/dev/null 2>&1; then
  MVN_DIR="$(ls -d "$HOME"/tools/apache-maven-* 2>/dev/null | head -1 || true)"
  [ -n "$MVN_DIR" ] && export PATH="$MVN_DIR/bin:$PATH"
fi
if ! command -v mvn >/dev/null 2>&1; then
  echo "未找到 Maven，请安装 Maven 3.9+"; exit 1
fi

# ---------- 3. 构建 Svelte 前端（产物输出到 Quarkus 静态资源目录） ----------
echo "[2/4] 构建前端（Svelte/Vite）"
cd "$ROOT/web"
[ -d node_modules ] || npm install
npm run build
cd "$ROOT"

# ---------- 4. 打包并启动 ----------
echo "[3/4] 打包后端（Quarkus/Hibernate/SQLite）"
mvn -q -B -DskipTests package
mkdir -p "$ROOT/data"

PORT="${PORT:-8080}"
echo "[4/4] 启动服务：http://localhost:${PORT}"
echo "  工作人员现场页：http://localhost:${PORT}/"
echo "  居民凭证查询：  http://localhost:${PORT}/  （页签切换，演示 A-DEMO-0001 / RC1001）"
exec java -Dquarkus.http.port="$PORT" -jar target/quarkus-app/quarkus-run.jar
