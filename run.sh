#!/usr/bin/env bash
# ============================================================================
# 社区药箱 · 过期药回收与分类交接系统 —— 一条命令启动
#
# 用法：
#   ./run.sh           构建前端（若需要）并在 8080 端口启动全栈服务
#   ./run.sh test      运行交付前验证测试（封袋冻结 / 双人拆袋 / 冷链 / 居民权限）
#
# 启动后访问： http://localhost:8080
# ============================================================================
set -euo pipefail
cd "$(dirname "$0")"

# ---- 1. 定位 Java 17 与 Maven：优先使用系统，其次使用本地 tools 目录 -------
if ! command -v java >/dev/null 2>&1; then
  for d in "$HOME/tools"/jdk-17* "$HOME"/.jdks/temurin-17* /usr/lib/jvm/java-17*; do
    if [ -x "$d/bin/java" ]; then export JAVA_HOME="$d"; export PATH="$d/bin:$PATH"; break; fi
  done
fi
if ! command -v mvn >/dev/null 2>&1; then
  for d in "$HOME/tools"/apache-maven-* /opt/maven*; do
    if [ -x "$d/bin/mvn" ]; then export PATH="$d/bin:$PATH"; break; fi
  done
fi

if ! command -v java >/dev/null 2>&1 || ! command -v mvn >/dev/null 2>&1; then
  echo "[run.sh] 未找到 java/mvn。请安装 JDK 17+ 与 Maven，或把它们放入 \$HOME/tools。" >&2
  exit 1
fi
echo "[run.sh] Java: $(java -version 2>&1 | head -1)"
echo "[run.sh] Maven: $(mvn -v 2>&1 | head -1)"

# ---- 2. 构建 Svelte 前端（产物输出到 Quarkus 静态资源目录）------------------
STATIC="src/main/resources/META-INF/resources/index.html"
if [ ! -f "$STATIC" ] || [ "${REBUILD_WEB:-0}" = "1" ]; then
  if ! command -v npm >/dev/null 2>&1; then
    echo "[run.sh] 需要 Node.js/npm 来构建前端，未找到 npm。" >&2
    exit 1
  fi
  echo "[run.sh] 首次启动，构建 Svelte 前端..."
  (cd web && npm install --no-audit --no-fund && npm run build)
fi

# ---- 3. 运行测试或启动服务 --------------------------------------------------
if [ "${1:-run}" = "test" ]; then
  echo "[run.sh] 运行交付前验证测试..."
  exec mvn -B test
fi

echo "[run.sh] 启动服务： http://localhost:8080  （Ctrl+C 停止）"
exec mvn -B quarkus:dev -Dquarkus.http.host=0.0.0.0
