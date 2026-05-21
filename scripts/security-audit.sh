#!/usr/bin/env bash
#
# Hify 推送前安全审计脚本
# ========================
# 在 git push 前自动扫描即将推送的变更，检测敏感信息泄露。
#
# 安装方式：
#   chmod +x scripts/security-audit.sh
#   cp scripts/security-audit.sh .git/hooks/pre-push
#   chmod +x .git/hooks/pre-push
#
# 或者运行：
#   ./scripts/install-security-hook.sh
#
# 退出码：
#   0 = 通过审计，允许推送
#   1 = 发现高危风险，阻断推送
#   2 = 仅发现中低危警告，需人工确认（可通过 --force 绕过）

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# 颜色定义
RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# 计数器
HIGH_RISK_COUNT=0
MEDIUM_RISK_COUNT=0
LOW_RISK_COUNT=0

# 即将推送的文件列表（相对于 remote 的增量）
FILES_TO_PUSH=""

# ============================================================
# 获取即将推送的 commits 中的文件
# ============================================================
get_files_to_push() {
    local remote="${1:-origin}"
    local remote_url="${2:-}"

    # 如果是 pre-push hook，参数是 $1=remote_name $2=remote_url
    # 否则手动获取与 origin/master 的差异
    if git rev-parse --verify "${remote}/master" >/dev/null 2>&1; then
        FILES_TO_PUSH=$(git diff --name-only "${remote}/master"..HEAD 2>/dev/null || true)
    elif git rev-parse --verify "${remote}/main" >/dev/null 2>&1; then
        FILES_TO_PUSH=$(git diff --name-only "${remote}/main"..HEAD 2>/dev/null || true)
    else
        # 如果没有远程跟踪分支，检查所有未推送的 commits
        FILES_TO_PUSH=$(git diff --name-only @{u}..HEAD 2>/dev/null || git diff --name-only HEAD~1..HEAD || true)
    fi

    # 如果还是空的（比如新仓库），检查暂存区和工作区
    if [[ -z "${FILES_TO_PUSH}" ]]; then
        FILES_TO_PUSH=$(git diff --cached --name-only)
        FILES_TO_PUSH="${FILES_TO_PUSH}$(git diff --name-only)"
    fi

    # 去重
    FILES_TO_PUSH=$(echo "${FILES_TO_PUSH}" | sort -u | grep -v '^$' || true)
}

# ============================================================
# 检查高危模式（阻断）
# ============================================================
check_high_risk() {
    local file="$1"
    local content="$2"
    local rel_path="$3"
    local findings=""

    # 1. 真实的 LLM API Key（OpenAI / DeepSeek / Claude 等）
    # 排除测试文件中的假 key
    if [[ ! "$rel_path" =~ (test|spec|mock|fixture) ]]; then
        findings=$(echo "$content" | grep -nE 'sk-[a-zA-Z0-9]{40,}' || true)
        if [[ -n "$findings" ]]; then
            echo -e "${RED}[高危] 发现疑似 LLM API Key${NC} → ${CYAN}${rel_path}${NC}"
            echo "$findings" | head -5 | sed 's/^/    /'
            ((HIGH_RISK_COUNT++)) || true
        fi
    fi

    # 2. AWS Access Key
    findings=$(echo "$content" | grep -nE 'AKIA[0-9A-Z]{16}' || true)
    if [[ -n "$findings" ]]; then
        echo -e "${RED}[高危] 发现疑似 AWS Access Key${NC} → ${CYAN}${rel_path}${NC}"
        echo "$findings" | head -5 | sed 's/^/    /'
        ((HIGH_RISK_COUNT++)) || true
    fi

    # 3. 私钥文件内容
    if echo "$content" | grep -qE 'BEGIN (RSA |EC |DSA |OPENSSH )?PRIVATE KEY'; then
        echo -e "${RED}[高危] 发现私钥文件内容${NC} → ${CYAN}${rel_path}${NC}"
        ((HIGH_RISK_COUNT++)) || true
    fi

    # 4. GitHub Personal Access Token
    findings=$(echo "$content" | grep -nE 'ghp_[a-zA-Z0-9]{36,}' || true)
    if [[ -n "$findings" ]]; then
        echo -e "${RED}[高危] 发现疑似 GitHub PAT${NC} → ${CYAN}${rel_path}${NC}"
        echo "$findings" | head -5 | sed 's/^/    /'
        ((HIGH_RISK_COUNT++)) || true
    fi

    # 5. 数据库连接字符串含真实密码（非 localhost / 非占位符）
    if echo "$content" | grep -qiE 'jdbc:mysql://[^/]+/[a-zA-Z0-9_]+.*password=[^${}]+'; then
        if ! echo "$content" | grep -qiE 'password=\$\{|password=.*(placeholder|your_|example|test|root)'; then
            echo -e "${RED}[高危] JDBC URL 中包含硬编码密码${NC} → ${CYAN}${rel_path}${NC}"
            ((HIGH_RISK_COUNT++)) || true
        fi
    fi
}

# ============================================================
# 检查中危模式（警告）
# ============================================================
check_medium_risk() {
    local file="$1"
    local content="$2"
    local rel_path="$3"
    local findings=""

    # 1. 配置文件中的密码字段（非占位符、非环境变量引用）
    if [[ "$rel_path" =~ \.(yml|yaml|properties)$ ]]; then
        findings=$(echo "$content" | grep -inE '^\s*(password|secret|token):\s*[^${}\s#]' || true)
        if [[ -n "$findings" ]]; then
            # 排除明显的占位符和测试环境
            local filtered=""
            while IFS= read -r line; do
                if echo "$line" | grep -qiE '(placeholder|your_|example|test|root|admin|123456|null|empty)'; then
                    continue
                fi
                filtered="${filtered}${line}\n"
            done <<< "$findings"

            if [[ -n "$filtered" ]]; then
                echo -e "${YELLOW}[中危] 配置文件含硬编码凭据${NC} → ${CYAN}${rel_path}${NC}"
                echo -e "$filtered" | head -5 | sed 's/^/    /'
                ((MEDIUM_RISK_COUNT++)) || true
            fi
        fi
    fi

    # 2.  Bearer Token 硬编码
    findings=$(echo "$content" | grep -inE 'Bearer\s+[a-zA-Z0-9_-]{20,}' || true)
    if [[ -n "$findings" && ! "$rel_path" =~ (test|spec|mock) ]]; then
        echo -e "${YELLOW}[中危] 发现硬编码 Bearer Token${NC} → ${CYAN}${rel_path}${NC}"
        echo "$findings" | head -3 | sed 's/^/    /'
        ((MEDIUM_RISK_COUNT++)) || true
    fi

    # 3. 测试文件中的 API Key（提醒确认是否为假数据）
    if [[ "$rel_path" =~ (test|spec) ]]; then
        findings=$(echo "$content" | grep -nE 'sk-[a-zA-Z0-9]{10,}' || true)
        if [[ -n "$findings" ]]; then
            # 检查是否是明显的假 key
            if ! echo "$findings" | grep -qiE '(test|dummy|mock|fake|example)'; then
                echo -e "${YELLOW}[中危] 测试文件含疑似真实 API Key${NC} → ${CYAN}${rel_path}${NC}"
                echo "$findings" | head -3 | sed 's/^/    /'
                ((MEDIUM_RISK_COUNT++)) || true
            fi
        fi
    fi
}

# ============================================================
# 检查文件类型风险
# ============================================================
check_file_type_risk() {
    local rel_path="$1"

    # 1. .env 文件（不应提交）
    if [[ "$rel_path" =~ \.env($|\.) ]]; then
        echo -e "${RED}[高危] 提交 .env 文件${NC} → ${CYAN}${rel_path}${NC}"
        echo "    建议：添加到 .gitignore，使用 .env.example 作为模板"
        ((HIGH_RISK_COUNT++)) || true
    fi

    # 2. 证书/密钥文件
    if [[ "$rel_path" =~ \.(pem|key|p12|pfx|crt|cer|der)$ ]]; then
        echo -e "${RED}[高危] 提交证书/密钥文件${NC} → ${CYAN}${rel_path}${NC}"
        ((HIGH_RISK_COUNT++)) || true
    fi

    # 3. IDE 本地配置文件（不应提交）
    if [[ "$rel_path" =~ \.(idea|vscode)/.*\.(json|xml)$ || "$rel_path" =~ \.iml$ ]]; then
        echo -e "${YELLOW}[低危] 提交 IDE 本地配置文件${NC} → ${CYAN}${rel_path}${NC}"
        ((LOW_RISK_COUNT++)) || true
    fi
}

# ============================================================
# 主逻辑
# ============================================================
main() {
    echo -e "${CYAN}═══════════════════════════════════════════════════${NC}"
    echo -e "${CYAN}  Hify 推送前安全审计${NC}"
    echo -e "${CYAN}═══════════════════════════════════════════════════${NC}"
    echo ""

    get_files_to_push "$@"

    if [[ -z "$FILES_TO_PUSH" ]]; then
        echo -e "${GREEN}✓ 无待推送文件，跳过审计${NC}"
        exit 0
    fi

    echo -e "扫描文件数：$(echo "$FILES_TO_PUSH" | wc -l | tr -d ' ')"
    echo ""

    # 遍历每个文件
    while IFS= read -r rel_path; do
        [[ -z "$rel_path" ]] && continue
        [[ ! -f "$rel_path" ]] && continue  # 可能是删除的文件

        local content=""
        # 对二进制文件跳过内容扫描
        if file -b "$rel_path" | grep -qi 'text'; then
            content=$(cat "$rel_path" 2>/dev/null || true)
        fi

        check_file_type_risk "$rel_path"

        if [[ -n "$content" ]]; then
            check_high_risk "$rel_path" "$content" "$rel_path"
            check_medium_risk "$rel_path" "$content" "$rel_path"
        fi
    done <<< "$FILES_TO_PUSH"

    echo ""
    echo -e "${CYAN}═══════════════════════════════════════════════════${NC}"
    echo -e "审计结果汇总："
    echo -e "  ${RED}高危：${HIGH_RISK_COUNT}${NC}"
    echo -e "  ${YELLOW}中危：${MEDIUM_RISK_COUNT}${NC}"
    echo -e "  低危：${LOW_RISK_COUNT}"
    echo -e "${CYAN}═══════════════════════════════════════════════════${NC}"

    if [[ $HIGH_RISK_COUNT -gt 0 ]]; then
        echo ""
        echo -e "${RED}⛔ 发现高危风险，推送已被阻断！${NC}"
        echo ""
        echo "修复建议："
        echo "  1. API Key 改用环境变量或外部配置注入"
        echo "  2. 密码改用 \${VAR} 占位符或 Spring 配置中心"
        echo "  3. 私钥/证书文件添加到 .gitignore"
        echo "  4. .env 文件替换为 .env.example（去敏模板）"
        echo ""
        echo "如确认是误报，可临时绕过："
        echo "  git push --no-verify  （跳过 pre-push hook）"
        echo ""
        exit 1
    fi

    if [[ $MEDIUM_RISK_COUNT -gt 0 ]]; then
        echo ""
        echo -e "${YELLOW}⚠️  发现中危警告，请人工确认是否为误报${NC}"
        echo ""
        echo "如确认无风险，继续推送："
        echo "  git push --no-verify"
        echo ""
        exit 2
    fi

    echo ""
    echo -e "${GREEN}✓ 安全审计通过，未发现敏感信息泄露${NC}"
    echo ""
    exit 0
}

main "$@"
