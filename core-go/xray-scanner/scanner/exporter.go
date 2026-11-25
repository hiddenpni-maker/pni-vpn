package scanner

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"net"
	"net/url"
	"strconv"
	"strings"
)

// Export converts healthy scanned results into portable client formats:
// "endpoints", "links", "base64", "singbox", or "clash".
func Export(results []Result, format string, baseShareLink string) (string, error) {
	var healthy []Result
	for _, r := range results {
		if r.Healthy {
			healthy = append(healthy, r)
		}
	}
	if len(healthy) == 0 {
		return "", fmt.Errorf("no healthy endpoints to export")
	}

	switch strings.ToLower(strings.TrimSpace(format)) {
	case "endpoints", "ip":
		lines := make([]string, 0, len(healthy))
		for _, r := range healthy {
			lines = append(lines, net.JoinHostPort(r.IP, strconv.Itoa(r.Port)))
		}
		return strings.Join(lines, "\n"), nil

	case "links":
		links := rewriteShareLinks(healthy, baseShareLink)
		return strings.Join(links, "\n"), nil

	case "base64", "sub":
		links := rewriteShareLinks(healthy, baseShareLink)
		return base64.StdEncoding.EncodeToString([]byte(strings.Join(links, "\n"))), nil

	case "singbox":
		return exportSingBox(healthy, baseShareLink)

	case "clash":
		return exportClash(healthy, baseShareLink)

	default:
		return "", fmt.Errorf("unknown export format %q", format)
	}
}

func rewriteShareLinks(results []Result, baseShareLink string) []string {
	link := strings.TrimSpace(baseShareLink)
	if link == "" {
		out := make([]string, 0, len(results))
		for _, r := range results {
			out = append(out, net.JoinHostPort(r.IP, strconv.Itoa(r.Port)))
		}
		return out
	}

	out := make([]string, 0, len(results))
	for i, r := range results {
		tag := fmt.Sprintf("PNI-%s-%d-%s", strings.ToUpper(r.Provider), i+1, r.Colo)
		if r.Colo == "" {
			tag = fmt.Sprintf("PNI-%s-%d-%dms", strings.ToUpper(r.Provider), i+1, r.LatencyMs)
		}
		if rewritten, err := RewriteSingleShareLink(link, r.IP, r.Port, tag); err == nil {
			out = append(out, rewritten)
		}
	}
	return out
}

// RewriteSingleShareLink updates server host & port in a vless://, trojan://, or vmess:// link
// while preserving original TLS SNI, Host header, UUID/password, and PattN params.
func RewriteSingleShareLink(link, newIP string, newPort int, remark string) (string, error) {
	link = strings.TrimSpace(link)
	switch {
	case strings.HasPrefix(link, "vless://"), strings.HasPrefix(link, "trojan://"):
		u, err := url.Parse(link)
		if err != nil {
			return "", err
		}
		origHost := u.Hostname()
		q := u.Query()
		if q.Get("sni") == "" && origHost != "" && net.ParseIP(origHost) == nil {
			q.Set("sni", origHost)
		}
		if q.Get("host") == "" && origHost != "" && net.ParseIP(origHost) == nil {
			q.Set("host", origHost)
		}
		u.RawQuery = q.Encode()
		u.Host = net.JoinHostPort(newIP, strconv.Itoa(newPort))
		if remark != "" {
			u.Fragment = remark
		}
		return u.String(), nil

	case strings.HasPrefix(link, "vmess://"):
		rawB64 := strings.TrimPrefix(link, "vmess://")
		decoded, err := base64.StdEncoding.DecodeString(padB64(rawB64))
		if err != nil {
			return "", err
		}
		var vm map[string]any
		if err := json.Unmarshal(decoded, &vm); err != nil {
			return "", err
		}
		origAdd, _ := vm["add"].(string)
		if sni, _ := vm["sni"].(string); sni == "" && origAdd != "" && net.ParseIP(origAdd) == nil {
			vm["sni"] = origAdd
		}
		if h, _ := vm["host"].(string); h == "" && origAdd != "" && net.ParseIP(origAdd) == nil {
			vm["host"] = origAdd
		}
		vm["add"] = newIP
		vm["port"] = newPort
		if remark != "" {
			vm["ps"] = remark
		}
		enc, _ := json.Marshal(vm)
		return "vmess://" + base64.StdEncoding.EncodeToString(enc), nil

	default:
		return "", fmt.Errorf("unsupported share link scheme")
	}
}

func exportSingBox(results []Result, baseShareLink string) (string, error) {
	u, err := url.Parse(strings.TrimSpace(baseShareLink))
	if err != nil || (u.Scheme != "vless" && u.Scheme != "trojan") {
		return "", fmt.Errorf("sing-box export requires a vless:// or trojan:// share link")
	}
	q := u.Query()
	sni := q.Get("sni")
	if sni == "" {
		sni = u.Hostname()
	}
	host := q.Get("host")
	if host == "" {
		host = sni
	}
	path := q.Get("path")
	if path == "" {
		path = "/"
	}

	var outbounds []map[string]any
	for i, r := range results {
		ob := map[string]any{
			"type":        u.Scheme,
			"tag":         fmt.Sprintf("pni-%d", i+1),
			"server":      r.IP,
			"server_port": r.Port,
			"tls": map[string]any{
				"enabled":     true,
				"server_name": sni,
				"insecure":    q.Get("fp") == "unsafe" || q.Get("allowInsecure") == "1",
			},
			"transport": map[string]any{
				"type": "ws",
				"path": path,
				"headers": map[string]any{
					"Host": host,
				},
			},
		}
		if u.Scheme == "vless" {
			ob["uuid"] = u.User.Username()
		} else {
			ob["password"] = u.User.Username()
		}
		outbounds = append(outbounds, ob)
	}
	raw, _ := json.MarshalIndent(map[string]any{"outbounds": outbounds}, "", "  ")
	return string(raw), nil
}

func exportClash(results []Result, baseShareLink string) (string, error) {
	u, err := url.Parse(strings.TrimSpace(baseShareLink))
	if err != nil || (u.Scheme != "vless" && u.Scheme != "trojan") {
		return "", fmt.Errorf("clash export requires a vless:// or trojan:// share link")
	}
	q := u.Query()
	sni := q.Get("sni")
	if sni == "" {
		sni = u.Hostname()
	}
	host := q.Get("host")
	if host == "" {
		host = sni
	}
	path := q.Get("path")
	if path == "" {
		path = "/"
	}

	var sb strings.Builder
	sb.WriteString("proxies:\n")
	for i, r := range results {
		sb.WriteString(fmt.Sprintf("  - name: \"PNI-%s-%d\"\n", r.Provider, i+1))
		sb.WriteString(fmt.Sprintf("    type: %s\n", u.Scheme))
		sb.WriteString(fmt.Sprintf("    server: %s\n", r.IP))
		sb.WriteString(fmt.Sprintf("    port: %d\n", r.Port))
		if u.Scheme == "vless" {
			sb.WriteString(fmt.Sprintf("    uuid: %s\n", u.User.Username()))
		} else {
			sb.WriteString(fmt.Sprintf("    password: %s\n", u.User.Username()))
		}
		sb.WriteString("    tls: true\n")
		sb.WriteString(fmt.Sprintf("    servername: %s\n", sni))
		sb.WriteString("    network: ws\n")
		sb.WriteString("    ws-opts:\n")
		sb.WriteString(fmt.Sprintf("      path: %s\n", path))
		sb.WriteString("      headers:\n")
		sb.WriteString(fmt.Sprintf("        Host: %s\n", host))
	}
	return sb.String(), nil
}
