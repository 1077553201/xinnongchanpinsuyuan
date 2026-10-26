#!/usr/bin/env bash
# 为本地开发环境建立 Cloudflare 命名隧道，把自定义域名指向本机 Vite (5173)。
#
# 前置条件
#   1. 域名已托管在 Cloudflare（NS 已指向 Cloudflare，dash 里能看到该 zone）
#   2. 已执行过账号授权，生成了 ~/.cloudflared/cert.pem：
#        cloudflared tunnel login
#
# 用法
#   ./setup-named-tunnel.sh <要绑定的域名> [隧道名]
#
# 示例
#   ./setup-named-tunnel.sh dev.example.com
#   ./setup-named-tunnel.sh dev.example.com agri-dev
#
# 环境变量
#   PORT  本地端口，默认 5173
#
# 说明
#   本机出站 UDP 7844 被封，QUIC 拨不通，所以写进 config.yml 的 protocol 固定为 http2。

set -euo pipefail

HOSTNAME="${1:-}"
TUNNEL="${2:-nongchanpin-dev}"
PORT="${PORT:-5173}"

CFG_DIR="$HOME/.cloudflared"
CERT="$CFG_DIR/cert.pem"
CONFIG="$CFG_DIR/config.yml"

if [ -z "$HOSTNAME" ]; then
    echo "用法: $0 <要绑定的域名> [隧道名]" >&2
    echo "示例: $0 dev.example.com" >&2
    exit 1
fi

if ! command -v cloudflared >/dev/null 2>&1; then
    echo "找不到 cloudflared，请确认已安装并在 PATH 中。" >&2
    exit 1
fi

if [ ! -f "$CERT" ]; then
    echo "未找到 $CERT" >&2
    echo "请先执行账号授权：cloudflared tunnel login" >&2
    exit 1
fi

mkdir -p "$CFG_DIR"

# ---- 1. 隧道不存在则创建 ----
TUNNEL_ID="$(cloudflared tunnel list 2>/dev/null | awk -v n="$TUNNEL" '$2 == n { print $1; exit }' || true)"

if [ -z "$TUNNEL_ID" ]; then
    echo "==> 创建隧道 $TUNNEL"
    cloudflared tunnel create "$TUNNEL"
    TUNNEL_ID="$(cloudflared tunnel list 2>/dev/null | awk -v n="$TUNNEL" '$2 == n { print $1; exit }')"
    if [ -z "$TUNNEL_ID" ]; then
        echo "隧道创建后仍未在列表中找到，请手动检查 cloudflared tunnel list" >&2
        exit 1
    fi
else
    echo "==> 隧道 $TUNNEL 已存在，ID: $TUNNEL_ID"
fi

CRED_FILE="$CFG_DIR/$TUNNEL_ID.json"
if [ ! -f "$CRED_FILE" ]; then
    echo "缺少凭据文件 $CRED_FILE，无法继续。" >&2
    echo "如需重新生成：cloudflared tunnel delete $TUNNEL 后重跑本脚本。" >&2
    exit 1
fi

# ---- 2. 写 config.yml（cloudflared 在 Windows 上同样接受正斜杠路径）----
echo "==> 写入 $CONFIG"
cat > "$CONFIG" <<EOF
# 由 deploy/cloudflare/setup-named-tunnel.sh 生成
tunnel: $TUNNEL_ID
credentials-file: $CFG_DIR/$TUNNEL_ID.json

# 本机 UDP 7844 出站被封，QUIC 连不上，固定走 HTTP/2
protocol: http2

ingress:
  - hostname: $HOSTNAME
    service: http://localhost:$PORT
    originRequest:
      noTLSVerify: true
  - service: http_status:404
EOF

# ---- 3. 绑定 DNS ----
echo "==> 绑定 $HOSTNAME -> 隧道 $TUNNEL"
if ! cloudflared tunnel route dns "$TUNNEL" "$HOSTNAME"; then
    echo "DNS 路由未新建（可能已存在）。若记录指向旧隧道，请到 dash 删除该 CNAME 后重试。" >&2
fi

# ---- 4. 本地校验 ----
echo "==> 校验配置"
cloudflared tunnel ingress validate

cat <<EOF

完成。常用命令：

  前台运行（看日志，Ctrl+C 停止）
    cloudflared tunnel run $TUNNEL

  后台常驻服务（开机自启）
    cloudflared service install
    # 卸载： cloudflared service uninstall

  查看隧道状态
    cloudflared tunnel info $TUNNEL

访问地址：https://$HOSTNAME
EOF
