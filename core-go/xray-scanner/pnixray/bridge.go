// Package pnixray exposes Xray-core (with PattN patches), the local SNI Spoof
// engine, DNS/IP lookup helpers, and the Multi-CDN scanner to Android via gomobile.
package pnixray

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"os"
	"strings"
	"sync"
	"time"

	_ "golang.org/x/mobile/bind"

	xnet "github.com/xtls/xray-core/common/net"
	"github.com/xtls/xray-core/core"
	"github.com/xtls/xray-core/features/stats"
	"github.com/xtls/xray-core/infra/conf/serial"
	_ "github.com/xtls/xray-core/main/distro/all"

	"ir.pni/core/scanner"
	"ir.pni/core/spoof"
)

var (
	xrayMu       sync.Mutex
	xrayInstance *core.Instance
	xrayRunning  bool
)

// InitEnv configures the asset directory (geoip.dat, geosite.dat) for Xray-core.
func InitEnv(datDir string) {
	if strings.TrimSpace(datDir) != "" {
		_ = os.Setenv("xray.location.asset", datDir)
	}
}

// GetXrayVersion returns the embedded Xray-core build version string.
func GetXrayVersion() string {
	return core.Version()
}

// CheckConfig validates an Xray JSON configuration and returns an empty string
// when valid, or the detailed parser error string.
func CheckConfig(configJSON string) string {
	if _, err := serial.LoadJSONConfig(strings.NewReader(configJSON)); err != nil {
		return err.Error()
	}
	return ""
}

// StartXray starts a single Xray-core instance from configJSON.
// Any previously running instance is cleanly stopped first.
func StartXray(configJSON string) error {
	xrayMu.Lock()
	defer xrayMu.Unlock()

	if xrayInstance != nil {
		_ = xrayInstance.Close()
		xrayInstance = nil
		xrayRunning = false
	}

	pbConfig, err := serial.LoadJSONConfig(strings.NewReader(configJSON))
	if err != nil {
		return fmt.Errorf("parse xray config: %w", err)
	}

	inst, err := core.New(pbConfig)
	if err != nil {
		return fmt.Errorf("create xray instance: %w", err)
	}
	if err := inst.Start(); err != nil {
		_ = inst.Close()
		return fmt.Errorf("start xray instance: %w", err)
	}

	xrayInstance = inst
	xrayRunning = true
	return nil
}

// StopXray stops the currently running Xray-core instance if active.
func StopXray() error {
	xrayMu.Lock()
	defer xrayMu.Unlock()

	if xrayInstance != nil {
		err := xrayInstance.Close()
		xrayInstance = nil
		xrayRunning = false
		return err
	}
	xrayRunning = false
	return nil
}

// IsXrayRunning reports whether Xray-core is currently active.
func IsXrayRunning() bool {
	xrayMu.Lock()
	defer xrayMu.Unlock()
	return xrayRunning && xrayInstance != nil
}

// QueryStats returns cumulative traffic bytes for an Xray stats counter
// (e.g., tag="proxy", direct="uplink" or "downlink").
func QueryStats(tag string, direct string) int64 {
	xrayMu.Lock()
	inst := xrayInstance
	xrayMu.Unlock()

	if inst == nil {
		return 0
	}
	sm := inst.GetFeature(stats.ManagerType())
	if sm == nil {
		return 0
	}
	manager, ok := sm.(stats.Manager)
	if !ok {
		return 0
	}
	counter := manager.GetCounter(fmt.Sprintf("outbound>>>%s>>>traffic>>>%s", tag, direct))
	if counter == nil {
		return 0
	}
	return counter.Value()
}

// MeasureDelay measures end-to-end HTTP latency (in ms) through the running Xray instance.
// Returns -1 on failure.
func MeasureDelay(probeURL string, timeoutMs int32) int64 {
	xrayMu.Lock()
	inst := xrayInstance
	xrayMu.Unlock()

	if inst == nil {
		return -1
	}
	if strings.TrimSpace(probeURL) == "" {
		probeURL = "https://www.gstatic.com/generate_204"
	}
	if timeoutMs <= 0 {
		timeoutMs = 5000
	}

	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(timeoutMs)*time.Millisecond)
	defer cancel()

	client := &http.Client{
		Timeout: time.Duration(timeoutMs) * time.Millisecond,
		Transport: &http.Transport{
			DisableKeepAlives: true,
			DialContext: func(c context.Context, network, addr string) (net.Conn, error) {
				dest, err := xnet.ParseDestination(network + ":" + addr)
				if err != nil {
					return nil, err
				}
				return core.Dial(c, inst, dest)
			},
		},
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, probeURL, nil)
	if err != nil {
		return -1
	}
	start := time.Now()
	resp, err := client.Do(req)
	if err != nil {
		return -1
	}
	_, _ = io.Copy(io.Discard, io.LimitReader(resp.Body, 4096))
	_ = resp.Body.Close()

	if resp.StatusCode >= 500 {
		return -1
	}
	return time.Since(start).Milliseconds()
}

// StartSniSpoof starts the local SNI Spoof proxy on listenAddr (default 127.0.0.1:40443).
func StartSniSpoof(listenAddr, targetAddr, fakeSni, method string, fragLen, fragDelayMs int32) error {
	return spoof.Start(spoof.Config{
		ListenAddr:  listenAddr,
		TargetAddr:  targetAddr,
		FakeSNI:     fakeSni,
		Method:      method,
		FragLen:     int(fragLen),
		FragDelayMs: int(fragDelayMs),
	})
}

// StopSniSpoof stops the local SNI Spoof proxy.
func StopSniSpoof() {
	spoof.Stop()
}

// IsSniSpoofRunning reports whether the local SNI Spoof proxy is listening.
func IsSniSpoofRunning() bool {
	return spoof.IsRunning()
}

// LookupDomainIPs resolves domain A/AAAA records using either system DNS or DoH (JSON array).
func LookupDomainIPs(domain string, dohURL string) string {
	ips, err := scanner.LookupDomainIPs(domain, dohURL)
	payload := map[string]any{
		"domain": domain,
		"ips":    ips,
	}
	if err != nil {
		payload["error"] = err.Error()
	}
	raw, _ := json.Marshal(payload)
	return string(raw)
}

// ScanCallback receives streaming scan lifecycle updates on Android.
type ScanCallback interface {
	OnProgress(tested int64, healthy int64, failed int64, inFlight int64, phase int32)
	OnResult(resultJSON string)
	OnInfo(message string)
	OnFinished(summaryJSON string)
	OnError(err string)
}

var (
	scanMu     sync.Mutex
	scanCancel context.CancelFunc
	scanActive bool
)

// StartScan launches the Multi-CDN scanner asynchronously with JSON config and callback.
func StartScan(configJSON string, cb ScanCallback) error {
	var cfg scanner.Config
	if err := json.Unmarshal([]byte(configJSON), &cfg); err != nil {
		return fmt.Errorf("invalid scan config: %w", err)
	}

	scanMu.Lock()
	if scanActive {
		scanMu.Unlock()
		return errors.New("scan already running")
	}
	ctx, cancel := context.WithCancel(context.Background())
	scanCancel = cancel
	scanActive = true
	scanMu.Unlock()

	go func() {
		defer func() {
			scanMu.Lock()
			scanActive = false
			scanCancel = nil
			scanMu.Unlock()
		}()

		summary, err := scanner.Run(ctx, cfg, scanner.Hooks{
			OnProgress: func(tested, healthy, failed, inFlight, phase int) {
				if cb != nil {
					cb.OnProgress(int64(tested), int64(healthy), int64(failed), int64(inFlight), int32(phase))
				}
			},
			OnResult: func(res scanner.Result) {
				if cb != nil {
					raw, _ := json.Marshal(res)
					cb.OnResult(string(raw))
				}
			},
			OnInfo: func(msg string) {
				if cb != nil {
					cb.OnInfo(msg)
				}
			},
		})

		if err != nil && !errors.Is(err, context.Canceled) {
			if cb != nil {
				cb.OnError(err.Error())
			}
			return
		}
		if cb != nil {
			raw, _ := json.Marshal(summary)
			cb.OnFinished(string(raw))
		}
	}()

	return nil
}

// StopScan cancels an active scan while keeping already discovered endpoints.
func StopScan() {
	scanMu.Lock()
	cancel := scanCancel
	scanMu.Unlock()
	if cancel != nil {
		cancel()
	}
}

// IsScanning reports whether a Multi-CDN scan is currently in progress.
func IsScanning() bool {
	scanMu.Lock()
	defer scanMu.Unlock()
	return scanActive
}

// ExportScanResults formats scanned results JSON into endpoints, links, base64, singbox, or clash.
func ExportScanResults(resultsJSON string, format string, baseShareLink string) string {
	var results []scanner.Result
	if err := json.Unmarshal([]byte(resultsJSON), &results); err != nil {
		return "error: " + err.Error()
	}
	out, err := scanner.Export(results, format, baseShareLink)
	if err != nil {
		return "error: " + err.Error()
	}
	return out
}
