package scanner

import (
	"crypto/tls"
	"encoding/binary"
	"net"
	"strconv"
	"strings"
	"sync"
	"time"
)

// AntiDPIConfig mirrors SenPaiScanner & PattN defaults for ClientHello fragmentation
// and custom TLS cipher suites against SNI-inspecting DPI boxes.
type AntiDPIConfig struct {
	Enabled      bool     `json:"enabled"`
	Lengths      []int    `json:"lengths"`      // e.g. [1, 40, 1] or [104, 1]
	DelayMs      int      `json:"delayMs"`      // e.g. 1..5 ms between fragments
	MaxSplit     int      `json:"maxSplit"`     // e.g. 11
	UnsafeFP     bool     `json:"unsafeFp"`     // skip strict uTLS mimicry / allow custom cipher list
	ALPN         []string `json:"alpn"`         // e.g. ["http/1.1"]
	CipherSuites []uint16 `json:"cipherSuites"` // optional TLS 1.2 cipher suite IDs
}

func DefaultAntiDPIConfig(enabled bool) AntiDPIConfig {
	return AntiDPIConfig{
		Enabled:  enabled,
		Lengths:  []int{1, 104, 1},
		DelayMs:  2,
		MaxSplit: 11,
		UnsafeFP: true,
		ALPN:     []string{"http/1.1"},
	}
}

// ParseLengthsSpec parses formats like "0/104/1" or "104-120,1-2" into positive chunk lengths.
func ParseLengthsSpec(spec string) []int {
	parts := strings.FieldsFunc(spec, func(r rune) bool {
		return r == '/' || r == ',' || r == ' '
	})
	var out []int
	for _, p := range parts {
		p = strings.TrimSpace(p)
		if p == "" {
			continue
		}
		if idx := strings.Index(p, "-"); idx > 0 {
			p = p[:idx]
		}
		if v, err := strconv.Atoi(p); err == nil && v > 0 {
			out = append(out, v)
		}
	}
	if len(out) == 0 {
		return []int{1, 40}
	}
	return out
}

// FragmentConn wraps a TCP net.Conn and splits the initial TLS ClientHello write
// into small TLS records / TCP writes to bypass stateless DPI SNI extraction.
type FragmentConn struct {
	net.Conn
	cfg      AntiDPIConfig
	mu       sync.Mutex
	didFirst bool
}

func NewFragmentConn(inner net.Conn, cfg AntiDPIConfig) net.Conn {
	if !cfg.Enabled {
		return inner
	}
	if tcp, ok := inner.(*net.TCPConn); ok {
		_ = tcp.SetNoDelay(true)
	}
	return &FragmentConn{Conn: inner, cfg: cfg}
}

func (f *FragmentConn) Write(p []byte) (int, error) {
	f.mu.Lock()
	defer f.mu.Unlock()

	if f.didFirst || !f.cfg.Enabled || len(p) <= 10 {
		return f.Conn.Write(p)
	}
	f.didFirst = true

	lengths := f.cfg.Lengths
	if len(lengths) == 0 {
		lengths = []int{1, 40}
	}
	maxSplit := f.cfg.MaxSplit
	if maxSplit <= 0 {
		maxSplit = 11
	}
	delay := time.Duration(f.cfg.DelayMs) * time.Millisecond

	// When payload is a TLS Handshake record (0x16 0x03 ...), emit valid split TLS records
	if len(p) > 5 && p[0] == 0x16 && p[1] == 0x03 {
		payload := p[5:]
		verMajor, verMinor := p[1], p[2]
		offset := 0
		splits := 0

		for offset < len(payload) {
			chunkLen := lengths[splits%len(lengths)]
			if chunkLen <= 0 {
				chunkLen = 40
			}
			if splits >= maxSplit-1 {
				chunkLen = len(payload) - offset
			}
			end := offset + chunkLen
			if end > len(payload) {
				end = len(payload)
			}
			sub := payload[offset:end]
			rec := make([]byte, 5+len(sub))
			rec[0] = 0x16
			rec[1] = verMajor
			rec[2] = verMinor
			binary.BigEndian.PutUint16(rec[3:5], uint16(len(sub)))
			copy(rec[5:], sub)
			if _, err := f.Conn.Write(rec); err != nil {
				return offset, err
			}
			offset = end
			splits++
			if delay > 0 && offset < len(payload) {
				time.Sleep(delay)
			}
		}
		return len(p), nil
	}

	// Fallback raw TCP fragmentation
	offset := 0
	splits := 0
	for offset < len(p) {
		chunkLen := lengths[splits%len(lengths)]
		if chunkLen <= 0 {
			chunkLen = 40
		}
		if splits >= maxSplit-1 {
			chunkLen = len(p) - offset
		}
		end := offset + chunkLen
		if end > len(p) {
			end = len(p)
		}
		if _, err := f.Conn.Write(p[offset:end]); err != nil {
			return offset, err
		}
		offset = end
		splits++
		if delay > 0 && offset < len(p) {
			time.Sleep(delay)
		}
	}
	return len(p), nil
}

// BuildProbeTLSConfig constructs a TLS configuration honoring SNI, ALPN, and optional custom cipher suites.
func BuildProbeTLSConfig(sni string, cfg AntiDPIConfig, insecure bool) *tls.Config {
	alpn := cfg.ALPN
	if len(alpn) == 0 {
		alpn = []string{"http/1.1"}
	}
	tc := &tls.Config{
		ServerName:         sni,
		NextProtos:         alpn,
		InsecureSkipVerify: insecure || cfg.UnsafeFP,
		MinVersion:         tls.VersionTLS12,
	}
	if len(cfg.CipherSuites) > 0 {
		tc.CipherSuites = cfg.CipherSuites
		// Go only honors CipherSuites for TLS 1.2; cap MaxVersion when custom suites requested
		tc.MaxVersion = tls.VersionTLS12
	}
	return tc
}
