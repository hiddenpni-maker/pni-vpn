// Binary libpnisingbox.so provides Lantern v10's open-source stealth networking stack
// (sing-box-minimal for Hysteria2/TUIC/SSH/WireGuard/ShadowTLS + outline-sdk/x mobileproxy
// with TCP split/disorder/tlsfrag anti-DPI) as a standalone Android native daemon.
package main

import (
	"context"
	"flag"
	"fmt"
	"log"
	"os"
	"os/signal"
	"syscall"

	box "github.com/sagernet/sing-box"
	"github.com/sagernet/sing-box/include"
	"github.com/sagernet/sing-box/option"
	sjson "github.com/sagernet/sing/common/json"
	"golang.getoutline.org/sdk/x/mobileproxy"
)

func main() {
	mode := flag.String("mode", "singbox", "execution mode: singbox | outline")
	configPath := flag.String("config", "", "path to sing-box JSON config")
	listenAddr := flag.String("listen", "127.0.0.1:10808", "local proxy listen address for outline mode")
	transport := flag.String("transport", "split:3|tlsfrag:5", "outline-sdk transport spec (split/disorder/tlsfrag/shadowsocks)")
	flag.Parse()

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	switch *mode {
	case "outline":
		if err := runOutlineProxy(ctx, *listenAddr, *transport); err != nil {
			log.Fatalf("outline mode failed: %v", err)
		}
	case "singbox":
		if *configPath == "" {
			log.Fatal("--config is required in singbox mode")
		}
		if err := runSingBox(ctx, *configPath); err != nil {
			log.Fatalf("sing-box mode failed: %v", err)
		}
	default:
		log.Fatalf("unknown mode %q", *mode)
	}
}

func runSingBox(ctx context.Context, configPath string) error {
	raw, err := os.ReadFile(configPath)
	if err != nil {
		return fmt.Errorf("read config: %w", err)
	}
	ctx = include.Context(ctx)
	opts, err := sjson.UnmarshalExtendedContext[option.Options](ctx, raw)
	if err != nil {
		return fmt.Errorf("parse sing-box options: %w", err)
	}
	inst, err := box.New(box.Options{
		Context: ctx,
		Options: opts,
	})
	if err != nil {
		return fmt.Errorf("create sing-box: %w", err)
	}
	if err := inst.Start(); err != nil {
		_ = inst.Close()
		return fmt.Errorf("start sing-box: %w", err)
	}
	defer inst.Close()

	<-ctx.Done()
	return nil
}

func runOutlineProxy(ctx context.Context, listenAddr, transportSpec string) error {
	dialer, err := mobileproxy.NewStreamDialerFromConfig(transportSpec)
	if err != nil {
		return fmt.Errorf("outline dialer config: %w", err)
	}
	proxy, err := mobileproxy.RunProxy(listenAddr, dialer)
	if err != nil {
		return fmt.Errorf("start outline mobileproxy: %w", err)
	}
	defer proxy.Stop(5)
	log.Printf("PNI Outline stealth proxy listening on %s (%s)", proxy.Address(), transportSpec)
	<-ctx.Done()
	return nil
}
