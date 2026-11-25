#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ASSETS="$ROOT_DIR/app/src/main/assets"
mkdir -p "$ASSETS/geo" "$ASSETS/tor" "$ASSETS/scanner"

echo "[1/3] Fetching Iran GeoIP & GeoSite databases..."
curl -fsSL -o "$ASSETS/geo/geoip.dat" "https://github.com/Chocolate4U/Iran-v2ray-rules/releases/latest/download/geoip.dat" || true
curl -fsSL -o "$ASSETS/geo/geosite.dat" "https://github.com/Chocolate4U/Iran-v2ray-rules/releases/latest/download/geosite.dat" || true

echo "[2/3] Fetching Iran Tor Moat bridges..."
response=$(curl -fsSL --retry 2 -X POST \
  -H 'Content-Type: application/vnd.api+json' \
  -d '{"country":"ir","transports":["webtunnel","obfs4","snowflake"]}' \
  https://bridges.torproject.org/moat/circumvention/settings || true)

if [ -n "$response" ]; then
  mapfile -t bridges < <(grep -oE '"(webtunnel|obfs4|snowflake) [^"]+ [0-9A-F]{40}[^"]*"' <<<"$response" | tr -d '"' | sort -u || true)
  if [ "${#bridges[@]}" -gt 0 ]; then
    {
      echo '{'
      echo '  "country": "ir",'
      echo '  "bridges": ['
      for i in "${!bridges[@]}"; do
        sep=','; [ "$i" -eq $((${#bridges[@]} - 1)) ] && sep=''
        echo "    \"${bridges[$i]}\"$sep"
      done
      echo '  ]'
      echo '}'
    } > "$ASSETS/tor/bridges.json"
    echo "Fetched ${#bridges[@]} Tor bridges."
  fi
fi

echo "[3/3] Refreshing CDN CIDR lists (Cloudflare / Akamai / WARP)..."
curl -fsSL -o "$ASSETS/scanner/cf_v4.txt" "https://raw.githubusercontent.com/MatinSenPai/SenPaiScanner/main/internal/ipsrc/ranges_v4.txt" || true
curl -fsSL -o "$ASSETS/scanner/akamai_v4.txt" "https://raw.githubusercontent.com/SecOps-Institute/Akamai-ASN-and-IPs-List/master/akamai_ip_cidr_blocks.lst" || true
curl -fsSL -o "$ASSETS/scanner/warp_cidrs.txt" "https://raw.githubusercontent.com/bia-pain-bache/BPB-VPN/main/assets/scanner/warp-cidrs.txt" || true
