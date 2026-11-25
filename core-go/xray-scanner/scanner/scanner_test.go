package scanner

import (
	"bytes"
	"net"
	"net/netip"
	"strings"
	"testing"
	"time"
)

func TestSampleFromCIDRsCloudflareAndAkamai(t *testing.T) {
	cf := SampleFromCIDRs(DefaultCIDRsFor(ProviderCloudflare), 25)
	if len(cf) == 0 {
		t.Fatal("expected sampled Cloudflare IPs")
	}
	ak := SampleFromCIDRs(DefaultCIDRsFor(ProviderAkamai), 25)
	if len(ak) == 0 {
		t.Fatal("expected sampled Akamai IPs")
	}
	for _, ip := range ak {
		if !ip.Is4() {
			t.Fatalf("expected IPv4, got %v", ip)
		}
	}
}

func TestParseTargetsAndNeighbors(t *testing.T) {
	raw := "104.16.1.1\n188.114.96.1-188.114.96.5\n23.64.0.0/30\n# comment\nbad-domain-invalid.invalid"
	addrs, warnings := ParseTargets(raw, "", 16)
	if len(addrs) < 6 {
		t.Fatalf("expected >= 6 parsed addrs, got %d", len(addrs))
	}
	if len(warnings) == 0 {
		t.Fatal("expected warning for unresolvable domain")
	}

	neighbors := NeighborIPs(netip.MustParseAddr("104.16.1.10"), 3)
	if len(neighbors) != 6 {
		t.Fatalf("expected 6 neighbors, got %d", len(neighbors))
	}
}

func TestRewriteAndExport(t *testing.T) {
	base := "vless://11111111-1111-1111-1111-111111111111@worker.example.com:443?type=ws&security=tls&path=%2Fws&fp=unsafe#Orig"
	results := []Result{
		{IP: "104.17.1.2", Port: 443, Provider: "cloudflare", LatencyMs: 42, Colo: "FRA", Healthy: true},
		{IP: "23.32.10.20", Port: 443, Provider: "akamai", LatencyMs: 55, Healthy: true},
	}

	linksOut, err := Export(results, "links", base)
	if err != nil {
		t.Fatalf("Export links failed: %v", err)
	}
	if !strings.Contains(linksOut, "104.17.1.2:443") || !strings.Contains(linksOut, "sni=worker.example.com") {
		t.Fatalf("unexpected rewritten links:\n%s", linksOut)
	}

	clashOut, err := Export(results, "clash", base)
	if err != nil || !strings.Contains(clashOut, "type: vless") {
		t.Fatalf("unexpected clash export: %v %s", err, clashOut)
	}
}

type mockConn struct {
	net.Conn
	writes [][]byte
}

func (m *mockConn) Write(p []byte) (int, error) {
	cp := append([]byte(nil), p...)
	m.writes = append(m.writes, cp)
	return len(p), nil
}
func (m *mockConn) Read(b []byte) (int, error)         { return 0, nil }
func (m *mockConn) Close() error                       { return nil }
func (m *mockConn) LocalAddr() net.Addr                { return nil }
func (m *mockConn) RemoteAddr() net.Addr               { return nil }
func (m *mockConn) SetDeadline(t time.Time) error      { return nil }
func (m *mockConn) SetReadDeadline(t time.Time) error  { return nil }
func (m *mockConn) SetWriteDeadline(t time.Time) error { return nil }

func TestFragmentConnTLSRecordSplit(t *testing.T) {
	mc := &mockConn{}
	fc := NewFragmentConn(mc, AntiDPIConfig{
		Enabled:  true,
		Lengths:  []int{4, 10},
		DelayMs:  0,
		MaxSplit: 10,
	})
	// Fake TLS ClientHello header + 24 bytes payload
	payload := bytes.Repeat([]byte("A"), 24)
	pkt := append([]byte{0x16, 0x03, 0x01, 0x00, 24}, payload...)
	if _, err := fc.Write(pkt); err != nil {
		t.Fatalf("Write error: %v", err)
	}
	if len(mc.writes) < 3 {
		t.Fatalf("expected fragmented writes (>=3), got %d", len(mc.writes))
	}
	for _, w := range mc.writes {
		if w[0] != 0x16 || w[1] != 0x03 {
			t.Fatalf("expected valid TLS record header on fragment, got %x", w[:5])
		}
	}
}
