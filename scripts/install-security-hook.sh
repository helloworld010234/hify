#!/usr/bin/env bash
#
# Hify 安全审计 Hook 安装脚本
# =============================
# 将项目自定义的 Git hooks 注册到当前仓库。
# 只需运行一次，后续所有 git push 都会自动触发安全审计。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
GITHOOKS_DIR="${PROJECT_ROOT}/.githooks"

echo "═══════════════════════════════════════════════════"
echo "  Hify 推送前安全审计 Hook 安装"
echo "═══════════════════════════════════════════════════"
echo ""

# 检查 .githooks 目录是否存在
if [[ ! -d "$GITHOOKS_DIR" ]]; then
    echo "错误：.githooks 目录不存在于 $GITHOOKS_DIR"
    exit 1
fi

# 设置 Git 使用项目自定义的 hooks 目录
cd "$PROJECT_ROOT"
git config core.hooksPath .githooks

# 确保 hooks 有执行权限
chmod +x "${GITHOOKS_DIR}/pre-push"
chmod +x "${SCRIPT_DIR}/security-audit.sh"

echo "✓ 已注册 pre-push hook：.githooks/pre-push"
echo "✓ 审计脚本路径：scripts/security-audit.sh"
echo ""
echo "此后每次 'git push' 都会自动扫描敏感信息："
echo "  • API Key（OpenAI、DeepSeek、AWS 等）"
echo "  • 硬编码密码 / Token"
echo "  • 私钥 / 证书文件"
echo "  • .env 文件误提交"
echo ""
echo "如需临时跳过审计（仅限紧急情况）："
echo "  git push --no-verify"
echo ""
echo "═══════════════════════════════════════════════════"
