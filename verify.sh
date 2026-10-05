#!/usr/bin/env bash
# 交付前自动化验证：封袋不可改、拆袋双人、冷藏追查、居民权限、差异确认
set -euo pipefail
cd "$(dirname "$0")"
if ! command -v java >/dev/null 2>&1; then
  [ -z "${JAVA_HOME:-}" ] && export JAVA_HOME="$(ls -d "$HOME"/tools/jdk-* 2>/dev/null | head -1 || true)"
  [ -n "${JAVA_HOME:-}" ] && export PATH="$JAVA_HOME/bin:$PATH"
fi
if ! command -v mvn >/dev/null 2>&1; then
  MVN_DIR="$(ls -d "$HOME"/tools/apache-maven-* 2>/dev/null | head -1 || true)"
  [ -n "$MVN_DIR" ] && export PATH="$MVN_DIR/bin:$PATH"
fi
rm -f target/test-medbox.db*
mvn -B test
