package scanner

import (
	"context"
	"crypto/tls"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/netip"
	"net/url"
	"sort"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	xnet "github.com/xtls/xray-core/common/net"
	"github.com/xtls/xray-core/core"
	"github.com/xtls/xray-core/infra/conf/serial"
	"golang.org/x/time/rate"
)

type Config struct {
	Provider      Provider      `json:"provider"`      // cloudflare, akamai, fastly, gcore, warp, custom
	CIDRs         []string      `json:"cidrs"`         // optional CIDR list loaded from Android assets
	TargetsText   string        `json:"targetsText"`   // optional pasted IPs/CIDRs/ranges/domains
	Count         int           `json:"count"`         // max candidate IPs to sample
	Ports         []int         `json:"ports"`         // e.g. [443, 8443, 2053]
	Workers       int           `json:"workers"`       // concurrent workers
	TimeoutMs     int           `json:"timeoutMs"`     // per-probe timeout
	Attempts      int           `json:"attempts"`      // pings per IP for loss calculation
	SNI           string        `json:"sni"`           // SNI / Host header used for TLS/HTTP probe
	RequireWS     bool          `json:"requireWs"`     // test WebSocket handshake upgrade
	WSPath        string        `json:"wsPath"`        // e.g. "/"
	NeighborScan  bool          `json:"neighborScan"`  // explore /24 neighbors around healthy IPs
	Gentle        bool          `json:"gentle"`        // rate-limited gentle profile for strict Iran ISPs
	SkipPhase1    bool          `json:"skipPhase1"`    // jump directly to Phase-2 Xray check
	AntiDPI       AntiDPIConfig `json:"antiDpi"`       // ClientHello fragmentation & custom TLS
	ShareLink     string        `json:"shareLink"`     // optional vless/trojan/vmess link for Phase-2 validation
	Phase2TopN    int           `json:"phase2TopN"`    // top N healthy IPs to validate via Xray
	SpeedTestURL  string        `json:"speedTestUrl"`  // download test URL through proxy
	DoHResolver   string        `json:"dohResolver"`   // DoH JSON endpoint for resolving pasted domains
	ResumeIPsDone []string      `json:"resumeIpsDone"` // already probed IPs to skip when resuming
}

type Result struct {
	IP           string  `json:"ip"`
	Port         int     `json:"port"`
	Provider     string  `json:"provider"`
	LatencyMs    int     `json:"latencyMs"`
	LossPct      float64 `json:"lossPct"`
	Colo         string  `json:"colo"`
	Country      string  `json:"country"`
	CDNHeader    string  `json:"cdnHeader"`
	Healthy      bool    `json:"healthy"`
	WSOk         bool    `json:"wsOk"`
	Phase2Tested bool    `json:"phase2Tested"`
	Phase2Ok     bool    `json:"phase2Ok"`
	TTFBMs       int     `json:"ttfbMs"`
	DownloadMbps float64 `json:"downloadMbps"`
	AntiDPIUsed  bool    `json:"antiDpiUsed"`
	Score        float64 `json:"score"`
}

type Summary struct {
	TotalTested  int      `json:"totalTested"`
	TotalHealthy int      `json:"totalHealthy"`
	Phase2Passed int      `json:"phase2Passed"`
	DurationMs   int64    `json:"durationMs"`
	Results      []Result `json:"results"`
}

type Hooks struct {
	OnProgress func(tested, healthy, failed, inFlight, phase int)
	OnResult   func(Result)
	OnInfo     func(string)
}

func (c *Config) normalize() {
	if c.Provider == "" {
		c.Provider = ProviderCloudflare
	}
	if c.Count <= 0 {
		c.Count = 200
	}
	if len(c.Ports) == 0 {
		c.Ports = []int{443}
	}
	if c.Workers <= 0 {
		c.Workers = 24
	}
	if c.TimeoutMs <= 0 {
		c.TimeoutMs = 3500
	}
	if c.Attempts <= 0 {
		c.Attempts = 2
	}
	if c.Phase2TopN <= 0 {
		c.Phase2TopN = 15
	}
	if strings.TrimSpace(c.WSPath) == "" {
		c.WSPath = "/"
	}
	if strings.TrimSpace(c.SpeedTestURL) == "" {
		c.SpeedTestURL = "https://speed.cloudflare.com/__down?bytes=1500000"
	}
	if strings.TrimSpace(c.SNI) == "" {
		switch c.Provider {
		case ProviderAkamai:
			c.SNI = "www.akamai.com"
		case ProviderFastly:
			c.SNI = "www.fastly.com"
		case ProviderGcore:
			c.SNI = "gcore.com"
		case ProviderWarp:
			c.SNI = "consumer-masque.cloudflareclient.com"
		default:
			c.SNI = "speed.cloudflare.com"
		}
	}
	// Gentle mode protects users on strict Iranian ISPs (MCI / Irancell / Mokhaberat) from connection drops
	if c.Gentle {
		if c.Workers > 20 {
			c.Workers = 20
		}
		if c.TimeoutMs < 6000 {
			c.TimeoutMs = 6000
		}
	}
}

func Run(ctx context.Context, cfg Config, hooks Hooks) (Summary, error) {
	start := time.Now()
	cfg.normalize()

	pool, warnings := buildCandidatePool(cfg)
	for _, w := range warnings {
		if hooks.OnInfo != nil {
			hooks.OnInfo(w)
		}
	}
	if len(pool) == 0 {
		return Summary{}, fmt.Errorf("no candidate IPs available for provider %s", cfg.Provider)
	}

	skipSet := make(map[string]struct{}, len(cfg.ResumeIPsDone))
	for _, s := range cfg.ResumeIPsDone {
		skipSet[s] = struct{}{}
	}

	type target struct {
		ip   netip.Addr
		port int
	}

	var queue []target
	for _, ip := range pool {
		for _, port := range cfg.Ports {
			key := fmt.Sprintf("%s:%d", ip.String(), port)
			if _, seen := skipSet[key]; !seen {
				queue = append(queue, target{ip: ip, port: port})
			}
		}
	}

	if len(queue) == 0 {
		return Summary{DurationMs: time.Since(start).Milliseconds()}, nil
	}

	var limiter *rate.Limiter
	if cfg.Gentle {
		limiter = rate.NewLimiter(rate.Limit(35), 10)
	}

	var (
		testedCnt   atomic.Int64
		healthyCnt  atomic.Int64
		failedCnt   atomic.Int64
		inFlightCnt atomic.Int64

		resMu   sync.Mutex
		results []Result

		queuedMu sync.Mutex
		queued   = make(map[string]struct{}, len(queue))
	)
	for _, t := range queue {
		queued[fmt.Sprintf("%s:%d", t.ip.String(), t.port)] = struct{}{}
	}

	if cfg.SkipPhase1 {
		for _, t := range queue {
			results = append(results, Result{
				IP:          t.ip.String(),
				Port:        t.port,
				Provider:    string(cfg.Provider),
				Healthy:     true,
				AntiDPIUsed: cfg.AntiDPI.Enabled,
			})
		}
	} else {
		jobs := make(chan target, len(queue)+64)
		for _, t := range queue {
			jobs <- t
		}

		var wg sync.WaitGroup
		var pendingNeighbors atomic.Int64
		var closeOnce sync.Once
		maybeClose := func() {
			if pendingNeighbors.Load() == 0 && len(jobs) == 0 {
				closeOnce.Do(func() { close(jobs) })
			}
		}

		// Progress ticker
		progDone := make(chan struct{})
		go func() {
			t := time.NewTicker(350 * time.Millisecond)
			defer t.Stop()
			for {
				select {
				case <-progDone:
					return
				case <-ctx.Done():
					return
				case <-t.C:
					if hooks.OnProgress != nil {
						hooks.OnProgress(
							int(testedCnt.Load()),
							int(healthyCnt.Load()),
							int(failedCnt.Load()),
							int(inFlightCnt.Load()),
							1,
						)
					}
					if len(jobs) == 0 && inFlightCnt.Load() == 0 {
						maybeClose()
					}
				}
			}
		}()

		workerCount := cfg.Workers
		if workerCount > len(queue) && !cfg.NeighborScan {
			workerCount = len(queue)
		}

		// Use job-stealing loop with explicit waitgroup over initial queue + optional neighbors
		var activeWorkers sync.WaitGroup
		activeWorkers.Add(workerCount)

		// Enqueue initial queue and manage completion cleanly
		jobSliceMu := sync.Mutex{}
		jobSlice := queue
		idx := 0

		nextJob := func() (target, bool) {
			jobSliceMu.Lock()
			defer jobSliceMu.Unlock()
			if idx >= len(jobSlice) {
				return target{}, false
			}
			t := jobSlice[idx]
			idx++
			return t, true
		}

		enqueueNeighbors := func(ip netip.Addr, port int) {
			if !cfg.NeighborScan {
				return
			}
			neighbors := NeighborIPs(ip, 4)
			queuedMu.Lock()
			var extra []target
			for _, n := range neighbors {
				k := fmt.Sprintf("%s:%d", n.String(), port)
				if _, ok := queued[k]; !ok {
					queued[k] = struct{}{}
					extra = append(extra, target{ip: n, port: port})
				}
			}
			queuedMu.Unlock()
			if len(extra) > 0 {
				jobSliceMu.Lock()
				jobSlice = append(jobSlice, extra...)
				jobSliceMu.Unlock()
			}
		}
		_ = wg
		_ = jobs

		for w := 0; w < workerCount; w++ {
			go func() {
				defer activeWorkers.Done()
				for {
					if ctx.Err() != nil {
						return
					}
					t, ok := nextJob()
					if !ok {
						return
					}
					if limiter != nil {
						if err := limiter.Wait(ctx); err != nil {
							return
						}
					}
					inFlightCnt.Add(1)
					r := probeEndpoint(ctx, cfg, t.ip, t.port)
					inFlightCnt.Add(-1)
					testedCnt.Add(1)
					if r.Healthy {
						healthyCnt.Add(1)
						enqueueNeighbors(t.ip, t.port)
					} else {
						failedCnt.Add(1)
					}
					resMu.Lock()
					results = append(results, r)
					resMu.Unlock()
					if hooks.OnResult != nil {
						hooks.OnResult(r)
					}
				}
			}()
		}

		activeWorkers.Wait()
		close(progDone)
	}

	// Rank Phase-1 results (healthy first, lowest latency + loss)
	sortResults(results)

	// Optional Phase-2 real proxy validation via embedded Xray-core
	phase2Passed := 0
	if strings.TrimSpace(cfg.ShareLink) != "" && ctx.Err() == nil {
		var shortlist []*Result
		for i := range results {
			if results[i].Healthy && len(shortlist) < cfg.Phase2TopN {
				shortlist = append(shortlist, &results[i])
			}
		}
		for i, r := range shortlist {
			if ctx.Err() != nil {
				break
			}
			if hooks.OnProgress != nil {
				hooks.OnProgress(i+1, phase2Passed, 0, 1, 2)
			}
			ok, ttfb, mbps := validateWithXray(ctx, cfg, r.IP, r.Port)
			r.Phase2Tested = true
			r.Phase2Ok = ok
			r.TTFBMs = ttfb
			r.DownloadMbps = mbps
			if ok {
				phase2Passed++
				r.Score = computeScore(*r)
			}
			if hooks.OnResult != nil {
				hooks.OnResult(*r)
			}
		}
		sortResults(results)
	}

	if hooks.OnProgress != nil {
		hooks.OnProgress(len(results), int(healthyCnt.Load()), int(failedCnt.Load()), 0, 0)
	}

	return Summary{
		TotalTested:  len(results),
		TotalHealthy: int(healthyCnt.Load()),
		Phase2Passed: phase2Passed,
		DurationMs:   time.Since(start).Milliseconds(),
		Results:      results,
	}, ctx.Err()
}

func buildCandidatePool(cfg Config) ([]netip.Addr, []string) {
	if strings.TrimSpace(cfg.TargetsText) != "" {
		return ParseTargets(cfg.TargetsText, cfg.DoHResolver, 512)
	}
	cidrs := cfg.CIDRs
	if len(cidrs) == 0 {
		cidrs = DefaultCIDRsFor(cfg.Provider)
	}
	return SampleFromCIDRs(cidrs, cfg.Count), nil
}

func probeEndpoint(ctx context.Context, cfg Config, ip netip.Addr, port int) Result {
	res := Result{
		IP:          ip.String(),
		Port:        port,
		Provider:    string(cfg.Provider),
		AntiDPIUsed: cfg.AntiDPI.Enabled,
	}

	timeout := time.Duration(cfg.TimeoutMs) * time.Millisecond
	addr := net.JoinHostPort(ip.String(), strconv.Itoa(port))

	var totalMs int64
	var okCount int

	for attempt := 0; attempt < cfg.Attempts; attempt++ {
		if ctx.Err() != nil {
			break
		}
		pCtx, cancel := context.WithTimeout(ctx, timeout)
		ms, colo, country, cdnHdr, wsOk, err := probeSingleAttempt(pCtx, cfg, addr)
		cancel()
		if err == nil {
			okCount++
			totalMs += ms
			if colo != "" {
				res.Colo = colo
			}
			if country != "" {
				res.Country = country
			}
			if cdnHdr != "" {
				res.CDNHeader = cdnHdr
			}
			if wsOk {
				res.WSOk = true
			}
		}
	}

	if okCount == 0 {
		res.LossPct = 100
		res.Healthy = false
		return res
	}

	res.LatencyMs = int(totalMs / int64(okCount))
	res.LossPct = float64(cfg.Attempts-okCount) / float64(cfg.Attempts) * 100.0
	res.Healthy = !cfg.RequireWS || res.WSOk
	res.Score = computeScore(res)
	return res
}

func probeSingleAttempt(ctx context.Context, cfg Config, addr string) (int64, string, string, string, bool, error) {
	start := time.Now()
	dialer := &net.Dialer{Timeout: time.Duration(cfg.TimeoutMs) * time.Millisecond}

	rawConn, err := dialer.DialContext(ctx, "tcp", addr)
	if err != nil {
		return 0, "", "", "", false, err
	}
	if cfg.AntiDPI.Enabled {
		rawConn = NewFragmentConn(rawConn, cfg.AntiDPI)
	}

	tlsCfg := BuildProbeTLSConfig(cfg.SNI, cfg.AntiDPI, true)
	tlsConn := tls.Client(rawConn, tlsCfg)
	defer tlsConn.Close()

	if deadline, ok := ctx.Deadline(); ok {
		_ = tlsConn.SetDeadline(deadline)
	}
	if err := tlsConn.HandshakeContext(ctx); err != nil {
		return 0, "", "", "", false, err
	}

	// Build targeted CDN HTTP probe
	path := "/"
	if cfg.Provider == ProviderCloudflare {
		path = "/cdn-cgi/trace"
	}

	var reqBuf strings.Builder
	reqBuf.WriteString("GET " + path + " HTTP/1.1\r\n")
	reqBuf.WriteString("Host: " + cfg.SNI + "\r\n")
	reqBuf.WriteString("User-Agent: Mozilla/5.0 (Linux; Android 14) PNI-Scanner/1.0\r\n")
	if cfg.RequireWS {
		reqBuf.WriteString("Connection: Upgrade\r\n")
		reqBuf.WriteString("Upgrade: websocket\r\n")
		reqBuf.WriteString("Sec-WebSocket-Version: 13\r\n")
		reqBuf.WriteString("Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n")
		if cfg.WSPath != "" && cfg.Provider != ProviderCloudflare {
			// Already queried path above; WS uses WSPath when not checking trace
		}
	} else {
		reqBuf.WriteString("Connection: close\r\n")
	}
	reqBuf.WriteString("\r\n")

	if _, err := tlsConn.Write([]byte(reqBuf.String())); err != nil {
		return 0, "", "", "", false, err
	}

	buf := make([]byte, 4096)
	n, err := tlsConn.Read(buf)
	if err != nil && n == 0 {
		return 0, "", "", "", false, err
	}
	respText := string(buf[:n])
	ms := time.Since(start).Milliseconds()

	// Must look like genuine HTTP response (not TCP RST / empty / ISP captive HTML)
	if !strings.HasPrefix(respText, "HTTP/1.") && !strings.HasPrefix(respText, "HTTP/2") {
		return 0, "", "", "", false, fmt.Errorf("non-HTTP response")
	}

	colo, country, cdnHdr := parseCDNResponse(cfg.Provider, respText)
	wsOk := false
	if cfg.RequireWS {
		wsOk = testWebSocketUpgrade(ctx, cfg, addr)
	}
	return ms, colo, country, cdnHdr, wsOk, nil
}

func testWebSocketUpgrade(ctx context.Context, cfg Config, addr string) bool {
	dialer := &net.Dialer{Timeout: time.Duration(cfg.TimeoutMs) * time.Millisecond}
	rawConn, err := dialer.DialContext(ctx, "tcp", addr)
	if err != nil {
		return false
	}
	if cfg.AntiDPI.Enabled {
		rawConn = NewFragmentConn(rawConn, cfg.AntiDPI)
	}
	tlsConn := tls.Client(rawConn, BuildProbeTLSConfig(cfg.SNI, cfg.AntiDPI, true))
	defer tlsConn.Close()
	if deadline, ok := ctx.Deadline(); ok {
		_ = tlsConn.SetDeadline(deadline)
	}
	if err := tlsConn.HandshakeContext(ctx); err != nil {
		return false
	}

	wsPath := cfg.WSPath
	if !strings.HasPrefix(wsPath, "/") {
		wsPath = "/" + wsPath
	}
	req := fmt.Sprintf("GET %s HTTP/1.1\r\nHost: %s\r\nConnection: Upgrade\r\nUpgrade: websocket\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n\r\n", wsPath, cfg.SNI)
	if _, err := tlsConn.Write([]byte(req)); err != nil {
		return false
	}
	buf := make([]byte, 1024)
	n, _ := tlsConn.Read(buf)
	head := string(buf[:n])
	// 101 Switching Protocols or CDN worker 400/404 WS upgrade rejection proves path reached edge proxy
	return strings.Contains(head, "101 ") || strings.Contains(head, "cloudflare") || strings.Contains(head, "Akamai")
}

func parseCDNResponse(provider Provider, resp string) (colo, country, cdnHdr string) {
	lower := strings.ToLower(resp)
	for _, line := range strings.Split(resp, "\n") {
		line = strings.TrimSpace(line)
		if strings.HasPrefix(line, "colo=") {
			colo = strings.TrimPrefix(line, "colo=")
		} else if strings.HasPrefix(line, "loc=") {
			country = strings.TrimPrefix(line, "loc=")
		} else if strings.HasPrefix(strings.ToLower(line), "cf-ray:") {
			parts := strings.Split(line, "-")
			if len(parts) > 1 && colo == "" {
				colo = strings.TrimSpace(parts[len(parts)-1])
			}
			cdnHdr = "cloudflare"
		} else if strings.HasPrefix(strings.ToLower(line), "server:") {
			val := strings.TrimSpace(line[len("server:"):])
			if strings.Contains(strings.ToLower(val), "akamai") || strings.Contains(strings.ToLower(val), "ghost") {
				cdnHdr = val
			} else if cdnHdr == "" {
				cdnHdr = val
			}
		}
	}
	if cdnHdr == "" {
		switch {
		case strings.Contains(lower, "akamaighost") || strings.Contains(lower, "x-akamai"):
			cdnHdr = "AkamaiGHost"
		case strings.Contains(lower, "x-served-by") || strings.Contains(lower, "fastly"):
			cdnHdr = "Fastly"
		case strings.Contains(lower, "gcore"):
			cdnHdr = "Gcore"
		case provider == ProviderCloudflare && colo != "":
			cdnHdr = "Cloudflare"
		}
	}
	return colo, country, cdnHdr
}

func validateWithXray(ctx context.Context, cfg Config, ip string, port int) (bool, int, float64) {
	xrayJSON, err := buildEphemeralXrayConfig(cfg.ShareLink, ip, port, cfg.AntiDPI)
	if err != nil {
		return false, 0, 0
	}
	pbConfig, err := serial.LoadJSONConfig(strings.NewReader(xrayJSON))
	if err != nil {
		return false, 0, 0
	}
	inst, err := core.New(pbConfig)
	if err != nil {
		return false, 0, 0
	}
	if err := inst.Start(); err != nil {
		_ = inst.Close()
		return false, 0, 0
	}
	defer inst.Close()

	timeout := 12 * time.Second
	c, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()

	client := &http.Client{
		Timeout: timeout,
		Transport: &http.Transport{
			DisableKeepAlives: true,
			DialContext: func(dCtx context.Context, network, addr string) (net.Conn, error) {
				dest, err := xnet.ParseDestination(network + ":" + addr)
				if err != nil {
					return nil, err
				}
				return core.Dial(dCtx, inst, dest)
			},
		},
	}

	req, err := http.NewRequestWithContext(c, http.MethodGet, cfg.SpeedTestURL, nil)
	if err != nil {
		return false, 0, 0
	}
	t0 := time.Now()
	resp, err := client.Do(req)
	if err != nil {
		return false, 0, 0
	}
	defer resp.Body.Close()
	ttfb := int(time.Since(t0).Milliseconds())

	if resp.StatusCode >= 400 {
		return false, ttfb, 0
	}
	dlStart := time.Now()
	n, err := io.Copy(io.Discard, io.LimitReader(resp.Body, 4*1024*1024))
	dlDur := time.Since(dlStart).Seconds()
	if err != nil && n == 0 {
		return false, ttfb, 0
	}
	if dlDur <= 0.001 {
		dlDur = 0.001
	}
	mbps := (float64(n) * 8.0) / (dlDur * 1_000_000.0)
	return true, ttfb, mbps
}

// buildEphemeralXrayConfig parses a vless://, trojan://, or vmess:// share link
// and swaps the server address/port to the candidate IP:port for Phase-2 verification.
func buildEphemeralXrayConfig(shareLink, ip string, port int, antiDPI AntiDPIConfig) (string, error) {
	link := strings.TrimSpace(shareLink)
	var outbound map[string]any

	switch {
	case strings.HasPrefix(link, "vless://"):
		u, err := url.Parse(link)
		if err != nil {
			return "", err
		}
		q := u.Query()
		host := q.Get("host")
		if host == "" {
			host = u.Hostname()
		}
		sni := q.Get("sni")
		if sni == "" {
			sni = host
		}
		outbound = buildXrayOutbound("vless", ip, port, u.User.Username(), "", q.Get("security"), sni, q.Get("fp"), q.Get("type"), host, q.Get("path"), q.Get("alpn"), antiDPI)
	case strings.HasPrefix(link, "trojan://"):
		u, err := url.Parse(link)
		if err != nil {
			return "", err
		}
		q := u.Query()
		host := q.Get("host")
		if host == "" {
			host = u.Hostname()
		}
		sni := q.Get("sni")
		if sni == "" {
			sni = host
		}
		outbound = buildXrayOutbound("trojan", ip, port, "", u.User.Username(), q.Get("security"), sni, q.Get("fp"), q.Get("type"), host, q.Get("path"), q.Get("alpn"), antiDPI)
	case strings.HasPrefix(link, "vmess://"):
		rawB64 := strings.TrimPrefix(link, "vmess://")
		decoded, err := base64.StdEncoding.DecodeString(padB64(rawB64))
		if err != nil {
			decoded, err = base64.RawStdEncoding.DecodeString(rawB64)
			if err != nil {
				return "", err
			}
		}
		var vm map[string]any
		if err := json.Unmarshal(decoded, &vm); err != nil {
			return "", err
		}
		uuid, _ := vm["id"].(string)
		sec, _ := vm["tls"].(string)
		sni, _ := vm["sni"].(string)
		host, _ := vm["host"].(string)
		if sni == "" {
			sni = host
		}
		netType, _ := vm["net"].(string)
		path, _ := vm["path"].(string)
		fp, _ := vm["fp"].(string)
		alpn, _ := vm["alpn"].(string)
		outbound = buildXrayOutbound("vmess", ip, port, uuid, "", sec, sni, fp, netType, host, path, alpn, antiDPI)
	default:
		return "", fmt.Errorf("unsupported share link for Phase-2 validation")
	}

	root := map[string]any{
		"log":       map[string]any{"loglevel": "none"},
		"outbounds": []any{outbound},
	}
	if antiDPI.Enabled {
		root["outbounds"] = []any{outbound, map[string]any{
			"tag":      "fragment",
			"protocol": "freedom",
			"settings": map[string]any{
				"fragment": map[string]any{
					"packets":  "tlshello",
					"length":   "1-40",
					"interval": fmt.Sprintf("%d-%d", maxInt(1, antiDPI.DelayMs), maxInt(2, antiDPI.DelayMs+2)),
				},
			},
		}}
	}
	raw, err := json.Marshal(root)
	return string(raw), err
}

func buildXrayOutbound(proto, ip string, port int, uuid, pass, security, sni, fp, netType, host, path, alpn string, antiDPI AntiDPIConfig) map[string]any {
	if netType == "" {
		netType = "ws"
	}
	if security == "" {
		security = "tls"
	}
	if path == "" {
		path = "/"
	}
	stream := map[string]any{
		"network":  netType,
		"security": security,
	}
	if security == "tls" {
		tlsSet := map[string]any{
			"serverName":    sni,
			"allowInsecure": antiDPI.UnsafeFP,
		}
		if fp != "" {
			tlsSet["fingerprint"] = fp
		} else if antiDPI.UnsafeFP {
			tlsSet["fingerprint"] = "unsafe"
		}
		if alpn != "" {
			tlsSet["alpn"] = strings.Split(alpn, ",")
		}
		stream["tlsSettings"] = tlsSet
	}
	if netType == "ws" {
		ws := map[string]any{"path": path}
		if host != "" {
			ws["headers"] = map[string]any{"Host": host}
		}
		stream["wsSettings"] = ws
	} else if netType == "grpc" {
		stream["grpcSettings"] = map[string]any{"serviceName": strings.TrimPrefix(path, "/")}
	}
	if antiDPI.Enabled {
		stream["sockopt"] = map[string]any{"dialerProxy": "fragment"}
	}

	var settings map[string]any
	switch proto {
	case "vless":
		settings = map[string]any{
			"vnext": []any{map[string]any{
				"address": ip,
				"port":    port,
				"users":   []any{map[string]any{"id": uuid, "encryption": "none"}},
			}},
		}
	case "vmess":
		settings = map[string]any{
			"vnext": []any{map[string]any{
				"address": ip,
				"port":    port,
				"users":   []any{map[string]any{"id": uuid, "alterId": 0, "security": "auto"}},
			}},
		}
	case "trojan":
		settings = map[string]any{
			"servers": []any{map[string]any{
				"address":  ip,
				"port":     port,
				"password": pass,
			}},
		}
	}

	return map[string]any{
		"tag":            "proxy",
		"protocol":       proto,
		"settings":       settings,
		"streamSettings": stream,
	}
}

func computeScore(r Result) float64 {
	if !r.Healthy {
		return -1
	}
	score := 1000.0 - float64(r.LatencyMs) - r.LossPct*5.0
	if r.Phase2Tested {
		if !r.Phase2Ok {
			score -= 500.0
		} else {
			score += r.DownloadMbps*40.0 - float64(r.TTFBMs)*0.2
		}
	}
	return score
}

func sortResults(results []Result) {
	sort.SliceStable(results, func(i, j int) bool {
		if results[i].Healthy != results[j].Healthy {
			return results[i].Healthy
		}
		if results[i].Phase2Ok != results[j].Phase2Ok {
			return results[i].Phase2Ok
		}
		return results[i].Score > results[j].Score
	})
}

func padB64(s string) string {
	if m := len(s) % 4; m != 0 {
		s += strings.Repeat("=", 4-m)
	}
	return s
}

func maxInt(a, b int) int {
	if a > b {
		return a
	}
	return b
}
