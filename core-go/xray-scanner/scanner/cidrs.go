package scanner

import (
	"crypto/rand"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math/big"
	"net"
	"net/http"
	"net/netip"
	"net/url"
	"strconv"
	"strings"
	"time"
)

type Provider string

const (
	ProviderCloudflare Provider = "cloudflare"
	ProviderAkamai     Provider = "akamai"
	ProviderFastly     Provider = "fastly"
	ProviderGcore      Provider = "gcore"
	ProviderWarp       Provider = "warp"
	ProviderCustom     Provider = "custom"
)

var defaultCloudflareCIDRs = []string{
	"173.245.48.0/20", "103.21.244.0/22", "103.22.200.0/22", "103.31.4.0/22",
	"141.101.64.0/18", "108.162.192.0/18", "190.93.240.0/20", "188.114.96.0/20",
	"197.234.240.0/22", "198.41.128.0/17", "162.158.0.0/15", "104.16.0.0/13",
	"104.24.0.0/14", "172.64.0.0/13", "131.0.72.0/22",
}

var defaultAkamaiCIDRs = []string{
	"23.0.0.0/12", "23.32.0.0/11", "23.192.0.0/11", "95.100.0.0/15",
	"184.24.0.0/13", "2.16.0.0/13", "104.64.0.0/10",
}

var defaultFastlyCIDRs = []string{
	"23.235.32.0/20", "43.249.72.0/22", "103.244.50.0/24",
	"146.75.0.0/16", "151.101.0.0/16", "199.232.0.0/16",
}

var defaultGcoreCIDRs = []string{
	"92.223.64.0/18", "92.38.128.0/18", "5.188.0.0/16",
}

var defaultWarpCIDRs = []string{
	"162.159.192.0/24", "162.159.193.0/24", "162.159.195.0/24",
	"162.159.198.0/24", "162.159.204.0/24",
	"188.114.96.0/24", "188.114.97.0/24", "188.114.98.0/24", "188.114.99.0/24",
	"8.6.112.0/24", "8.34.70.0/24", "8.34.146.0/24", "8.35.211.0/24",
	"8.39.125.0/24", "8.39.204.0/24", "8.39.214.0/24", "8.47.69.0/24",
}

func DefaultCIDRsFor(p Provider) []string {
	switch p {
	case ProviderAkamai:
		return defaultAkamaiCIDRs
	case ProviderFastly:
		return defaultFastlyCIDRs
	case ProviderGcore:
		return defaultGcoreCIDRs
	case ProviderWarp:
		return defaultWarpCIDRs
	default:
		return defaultCloudflareCIDRs
	}
}

// ParseTargets parses IPs, CIDRs, IP ranges (1.2.3.1-1.2.3.40), and domains
// from user-pasted text or bundled CIDR assets, returning resolved candidate IPs
// and any malformed/unresolvable line notices.
func ParseTargets(raw string, dohURL string, maxExpandPerCIDR int) ([]netip.Addr, []string) {
	if maxExpandPerCIDR <= 0 {
		maxExpandPerCIDR = 512
	}
	var addrs []netip.Addr
	var warnings []string
	seen := make(map[netip.Addr]struct{})

	addAddr := func(a netip.Addr) {
		a = a.Unmap()
		if !a.IsValid() {
			return
		}
		if _, ok := seen[a]; !ok {
			seen[a] = struct{}{}
			addrs = append(addrs, a)
		}
	}

	lines := strings.FieldsFunc(raw, func(r rune) bool {
		return r == '\n' || r == '\r' || r == ',' || r == ';'
	})

	for _, line := range lines {
		token := strings.TrimSpace(line)
		if token == "" || strings.HasPrefix(token, "#") {
			continue
		}
		// Strip inline comments
		if idx := strings.Index(token, "#"); idx > 0 {
			token = strings.TrimSpace(token[:idx])
		}

		if ip, err := netip.ParseAddr(token); err == nil {
			addAddr(ip)
			continue
		}

		if pfx, err := netip.ParsePrefix(token); err == nil {
			for _, a := range samplePrefix(pfx, maxExpandPerCIDR) {
				addAddr(a)
			}
			continue
		}

		if strings.Contains(token, "-") {
			parts := strings.SplitN(token, "-", 2)
			a1, e1 := netip.ParseAddr(strings.TrimSpace(parts[0]))
			a2, e2 := netip.ParseAddr(strings.TrimSpace(parts[1]))
			if e1 == nil && e2 == nil && a1.Is4() && a2.Is4() {
				for _, a := range expandRangeV4(a1, a2, maxExpandPerCIDR) {
					addAddr(a)
				}
				continue
			}
		}

		// Domain name -> resolve via DoH or system DNS
		resolved, err := LookupDomainIPs(token, dohURL)
		if err != nil || len(resolved) == 0 {
			warnings = append(warnings, fmt.Sprintf("skipped target %q", token))
			continue
		}
		for _, r := range resolved {
			if ip, err := netip.ParseAddr(r); err == nil {
				addAddr(ip)
			}
		}
	}

	return addrs, warnings
}

// SampleFromCIDRs selects up to count random unique IPv4 addresses weighted by prefix size.
func SampleFromCIDRs(cidrLines []string, count int) []netip.Addr {
	if count <= 0 {
		count = 200
	}
	var prefixes []netip.Prefix
	var weights []uint64
	var totalWeight uint64

	for _, line := range cidrLines {
		line = strings.TrimSpace(line)
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		pfx, err := netip.ParsePrefix(line)
		if err != nil || !pfx.Addr().Is4() {
			continue
		}
		bits := pfx.Bits()
		// Cap per-prefix weight at /14 so huge Akamai /10-/11 blocks don't starve smaller blocks
		effHostBits := 32 - bits
		if effHostBits > 18 {
			effHostBits = 18
		}
		w := uint64(1) << effHostBits
		prefixes = append(prefixes, pfx)
		weights = append(weights, w)
		totalWeight += w
	}

	if len(prefixes) == 0 || totalWeight == 0 {
		return nil
	}

	seen := make(map[netip.Addr]struct{}, count)
	out := make([]netip.Addr, 0, count)
	maxAttempts := count * 6

	for attempts := 0; len(out) < count && attempts < maxAttempts; attempts++ {
		pick := randUint64(totalWeight)
		var acc uint64
		var chosen netip.Prefix
		for i, w := range weights {
			acc += w
			if pick < acc {
				chosen = prefixes[i]
				break
			}
		}
		ip := randomAddrInPrefix(chosen)
		if !ip.IsValid() {
			continue
		}
		if _, ok := seen[ip]; !ok {
			seen[ip] = struct{}{}
			out = append(out, ip)
		}
	}
	return out
}

// NeighborIPs returns nearby /24 IPv4 addresses around a healthy endpoint
// (used when NeighborScan is enabled, matching SenPaiScanner).
func NeighborIPs(ip netip.Addr, radius int) []netip.Addr {
	if !ip.Is4() || radius <= 0 {
		return nil
	}
	b := ip.As4()
	host := int(b[3])
	out := make([]netip.Addr, 0, radius*2)
	for delta := -radius; delta <= radius; delta++ {
		if delta == 0 {
			continue
		}
		cand := host + delta
		if cand < 1 || cand > 254 {
			continue
		}
		out = append(out, netip.AddrFrom4([4]byte{b[0], b[1], b[2], byte(cand)}))
	}
	return out
}

// LookupDomainIPs resolves A and AAAA records for domain via DoH JSON API (if dohURL given)
// or falls back to system net.LookupIP.
func LookupDomainIPs(domain string, dohURL string) ([]string, error) {
	domain = strings.TrimSpace(domain)
	if domain == "" {
		return nil, errors.New("empty domain")
	}

	if strings.TrimSpace(dohURL) != "" {
		if ips, err := lookupViaDoH(domain, dohURL); err == nil && len(ips) > 0 {
			return ips, nil
		}
	}

	rawIPs, err := net.LookupIP(domain)
	if err != nil {
		return nil, err
	}
	seen := make(map[string]struct{})
	out := make([]string, 0, len(rawIPs))
	for _, ip := range rawIPs {
		s := ip.String()
		if _, ok := seen[s]; !ok {
			seen[s] = struct{}{}
			out = append(out, s)
		}
	}
	return out, nil
}

func lookupViaDoH(domain string, dohURL string) ([]string, error) {
	client := &http.Client{Timeout: 5 * time.Second}
	var out []string
	seen := make(map[string]struct{})

	for _, rrType := range []string{"A", "AAAA"} {
		u := fmt.Sprintf("%s?name=%s&type=%s", strings.TrimRight(dohURL, "?"), url.QueryEscape(domain), rrType)
		req, err := http.NewRequest(http.MethodGet, u, nil)
		if err != nil {
			continue
		}
		req.Header.Set("Accept", "application/dns-json")
		resp, err := client.Do(req)
		if err != nil {
			continue
		}
		body, _ := io.ReadAll(io.LimitReader(resp.Body, 65536))
		_ = resp.Body.Close()

		var parsed struct {
			Answer []struct {
				Type int    `json:"type"`
				Data string `json:"data"`
			} `json:"Answer"`
		}
		if err := json.Unmarshal(body, &parsed); err != nil {
			continue
		}
		for _, ans := range parsed.Answer {
			if (ans.Type == 1 || ans.Type == 28) && ans.Data != "" {
				if _, ok := seen[ans.Data]; !ok {
					seen[ans.Data] = struct{}{}
					out = append(out, ans.Data)
				}
			}
		}
	}
	if len(out) == 0 {
		return nil, fmt.Errorf("no DoH records for %s", domain)
	}
	return out, nil
}

func samplePrefix(pfx netip.Prefix, limit int) []netip.Addr {
	if !pfx.Addr().Is4() {
		return []netip.Addr{pfx.Addr()}
	}
	bits := 32 - pfx.Bits()
	if bits <= 9 && (1<<bits) <= limit {
		total := 1 << bits
		base := pfx.Masked().Addr().As4()
		baseU32 := uint32(base[0])<<24 | uint32(base[1])<<16 | uint32(base[2])<<8 | uint32(base[3])
		out := make([]netip.Addr, 0, total)
		for i := 0; i < total; i++ {
			v := baseU32 + uint32(i)
			ip := netip.AddrFrom4([4]byte{byte(v >> 24), byte(v >> 16), byte(v >> 8), byte(v)})
			if total > 2 && (i == 0 || i == total-1) {
				continue
			}
			out = append(out, ip)
		}
		return out
	}
	seen := make(map[netip.Addr]struct{}, limit)
	out := make([]netip.Addr, 0, limit)
	for i := 0; i < limit*3 && len(out) < limit; i++ {
		ip := randomAddrInPrefix(pfx)
		if _, ok := seen[ip]; !ok {
			seen[ip] = struct{}{}
			out = append(out, ip)
		}
	}
	return out
}

func expandRangeV4(a1, a2 netip.Addr, limit int) []netip.Addr {
	b1, b2 := a1.As4(), a2.As4()
	u1 := uint32(b1[0])<<24 | uint32(b1[1])<<16 | uint32(b1[2])<<8 | uint32(b1[3])
	u2 := uint32(b2[0])<<24 | uint32(b2[1])<<16 | uint32(b2[2])<<8 | uint32(b2[3])
	if u2 < u1 {
		u1, u2 = u2, u1
	}
	var out []netip.Addr
	for v := u1; v <= u2 && len(out) < limit; v++ {
		out = append(out, netip.AddrFrom4([4]byte{byte(v >> 24), byte(v >> 16), byte(v >> 8), byte(v)}))
	}
	return out
}

func randomAddrInPrefix(pfx netip.Prefix) netip.Addr {
	if !pfx.Addr().Is4() {
		return pfx.Addr()
	}
	base := pfx.Masked().Addr().As4()
	baseU32 := uint32(base[0])<<24 | uint32(base[1])<<16 | uint32(base[2])<<8 | uint32(base[3])
	hostBits := 32 - pfx.Bits()
	if hostBits <= 0 {
		return pfx.Addr()
	}
	mask := uint32((uint64(1) << hostBits) - 1)
	rnd := uint32(randUint64(uint64(mask) + 1))
	if hostBits >= 2 {
		// Avoid .0 network and .255 broadcast on typical /24-/16 subnets
		if rnd == 0 {
			rnd = 1
		} else if rnd == mask {
			rnd = mask - 1
		}
	}
	v := baseU32 | rnd
	return netip.AddrFrom4([4]byte{byte(v >> 24), byte(v >> 16), byte(v >> 8), byte(v)})
}

func randUint64(max uint64) uint64 {
	if max <= 1 {
		return 0
	}
	n, err := rand.Int(rand.Reader, new(big.Int).SetUint64(max))
	if err != nil {
		return uint64(time.Now().UnixNano()) % max
	}
	return n.Uint64()
}

func ParsePortList(raw string) []int {
	if strings.TrimSpace(raw) == "" {
		return []int{443}
	}
	var ports []int
	seen := make(map[int]struct{})
	for _, p := range strings.FieldsFunc(raw, func(r rune) bool { return r == ',' || r == ' ' || r == ';' }) {
		v, err := strconv.Atoi(strings.TrimSpace(p))
		if err == nil && v >= 1 && v <= 65535 {
			if _, ok := seen[v]; !ok {
				seen[v] = struct{}{}
				ports = append(ports, v)
			}
		}
	}
	if len(ports) == 0 {
		return []int{443}
	}
	return ports
}
