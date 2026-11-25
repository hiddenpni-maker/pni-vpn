// Package spoof implements Soldier-Client style local SNI Spoof tunneling
// (listening on 127.0.0.1:40443 by default) with ClientHello fragmentation
// and optional TLS SNI extension rewriting for restricted networks.
package spoof

import (
	"bytes"
	"encoding/binary"
	"errors"
	"io"
	"net"
	"strings"
	"sync"
	"time"
)

type Config struct {
	ListenAddr  string
	TargetAddr  string
	FakeSNI     string
	Method      string // "fragment", "sni-replace", "fragment+sni"
	FragLen     int
	FragDelayMs int
}

var (
	mu       sync.Mutex
	listener net.Listener
	running  bool
	active   Config
)

func Start(cfg Config) error {
	Stop()

	if strings.TrimSpace(cfg.ListenAddr) == "" {
		cfg.ListenAddr = "127.0.0.1:40443"
	}
	if cfg.FragLen <= 0 {
		cfg.FragLen = 40
	}
	if cfg.FragDelayMs < 0 {
		cfg.FragDelayMs = 2
	}
	if strings.TrimSpace(cfg.Method) == "" {
		cfg.Method = "fragment"
	}

	ln, err := net.Listen("tcp", cfg.ListenAddr)
	if err != nil {
		return err
	}

	mu.Lock()
	listener = ln
	running = true
	active = cfg
	mu.Unlock()

	go acceptLoop(ln, cfg)
	return nil
}

func Stop() {
	mu.Lock()
	ln := listener
	listener = nil
	running = false
	mu.Unlock()
	if ln != nil {
		_ = ln.Close()
	}
}

func IsRunning() bool {
	mu.Lock()
	defer mu.Unlock()
	return running
}

func acceptLoop(ln net.Listener, cfg Config) {
	for {
		conn, err := ln.Accept()
		if err != nil {
			if errors.Is(err, net.ErrClosed) {
				return
			}
			mu.Lock()
			stillRunning := running
			mu.Unlock()
			if !stillRunning {
				return
			}
			continue
		}
		go handleConn(conn, cfg)
	}
}

func handleConn(client net.Conn, cfg Config) {
	defer client.Close()

	if strings.TrimSpace(cfg.TargetAddr) == "" {
		return
	}

	remote, err := net.DialTimeout("tcp", cfg.TargetAddr, 10*time.Second)
	if err != nil {
		return
	}
	defer remote.Close()

	if tcpConn, ok := remote.(*net.TCPConn); ok {
		_ = tcpConn.SetNoDelay(true)
	}

	_ = client.SetReadDeadline(time.Now().Add(8 * time.Second))
	buf := make([]byte, 16384)
	n, err := client.Read(buf)
	_ = client.SetReadDeadline(time.Time{})
	if err != nil || n <= 0 {
		return
	}

	firstPacket := buf[:n]
	if (cfg.Method == "sni-replace" || cfg.Method == "fragment+sni") && strings.TrimSpace(cfg.FakeSNI) != "" {
		firstPacket = rewriteClientHelloSNI(firstPacket, cfg.FakeSNI)
	}

	if cfg.Method == "sni-replace" {
		if _, err := remote.Write(firstPacket); err != nil {
			return
		}
	} else {
		if err := writeFragmented(remote, firstPacket, cfg.FragLen, time.Duration(cfg.FragDelayMs)*time.Millisecond); err != nil {
			return
		}
	}

	errCh := make(chan error, 2)
	go func() {
		_, e := io.Copy(remote, client)
		errCh <- e
	}()
	go func() {
		_, e := io.Copy(client, remote)
		errCh <- e
	}()
	<-errCh
}

func writeFragmented(dst net.Conn, data []byte, fragLen int, delay time.Duration) error {
	if fragLen <= 0 || fragLen >= len(data) {
		_, err := dst.Write(data)
		return err
	}
	// If this looks like a TLS record (0x16 0x03 ...), slice payload into multiple valid TLS records
	if len(data) > 5 && data[0] == 0x16 && data[1] == 0x03 {
		payload := data[5:]
		verMajor, verMinor := data[1], data[2]
		for offset := 0; offset < len(payload); offset += fragLen {
			end := offset + fragLen
			if end > len(payload) {
				end = len(payload)
			}
			chunk := payload[offset:end]
			rec := make([]byte, 5+len(chunk))
			rec[0] = 0x16
			rec[1] = verMajor
			rec[2] = verMinor
			binary.BigEndian.PutUint16(rec[3:5], uint16(len(chunk)))
			copy(rec[5:], chunk)
			if _, err := dst.Write(rec); err != nil {
				return err
			}
			if delay > 0 && end < len(payload) {
				time.Sleep(delay)
			}
		}
		return nil
	}

	for offset := 0; offset < len(data); offset += fragLen {
		end := offset + fragLen
		if end > len(data) {
			end = len(data)
		}
		if _, err := dst.Write(data[offset:end]); err != nil {
			return err
		}
		if delay > 0 && end < len(data) {
			time.Sleep(delay)
		}
	}
	return nil
}

// rewriteClientHelloSNI replaces the ASCII SNI hostname inside a TLS ClientHello
// when the replacement length matches or pads/rebuilds length prefixes safely.
func rewriteClientHelloSNI(pkt []byte, newSNI string) []byte {
	// Basic TLS Handshake(0x16) + ClientHello(0x01) sanity check
	if len(pkt) < 43 || pkt[0] != 0x16 || pkt[5] != 0x01 {
		return pkt
	}
	// Locate SNI extension (0x0000) conservatively by parsing ClientHello structure
	pos := 5 + 4 + 2 + 32 // record(5) + hs(4) + ver(2) + random(32)
	if pos >= len(pkt) {
		return pkt
	}
	sidLen := int(pkt[pos])
	pos += 1 + sidLen
	if pos+2 > len(pkt) {
		return pkt
	}
	csLen := int(binary.BigEndian.Uint16(pkt[pos : pos+2]))
	pos += 2 + csLen
	if pos+1 > len(pkt) {
		return pkt
	}
	compLen := int(pkt[pos])
	pos += 1 + compLen
	if pos+2 > len(pkt) {
		return pkt
	}
	extTotalLen := int(binary.BigEndian.Uint16(pkt[pos : pos+2]))
	extStart := pos + 2
	if extStart+extTotalLen > len(pkt) {
		return pkt
	}

	cur := extStart
	endExt := extStart + extTotalLen
	for cur+4 <= endExt {
		extType := binary.BigEndian.Uint16(pkt[cur : cur+2])
		extLen := int(binary.BigEndian.Uint16(pkt[cur+2 : cur+4]))
		if cur+4+extLen > endExt {
			break
		}
		if extType == 0x0000 && extLen >= 5 {
			nameType := pkt[cur+6]
			nameLen := int(binary.BigEndian.Uint16(pkt[cur+7 : cur+9]))
			if nameType == 0x00 && cur+9+nameLen <= endExt {
				oldStart := cur + 9
				oldEnd := oldStart + nameLen
				newBytes := []byte(newSNI)
				delta := len(newBytes) - nameLen

				var out bytes.Buffer
				out.Write(pkt[:oldStart])
				out.Write(newBytes)
				out.Write(pkt[oldEnd:])
				res := out.Bytes()

				// Patch length fields: record len, handshake len, extensions len, SNI ext len, list len, name len
				recLen := int(binary.BigEndian.Uint16(res[3:5])) + delta
				hsLen := (int(res[6])<<16 | int(res[7])<<8 | int(res[8])) + delta
				if recLen <= 0 || hsLen <= 0 {
					return pkt
				}
				binary.BigEndian.PutUint16(res[3:5], uint16(recLen))
				res[6] = byte(hsLen >> 16)
				res[7] = byte(hsLen >> 8)
				res[8] = byte(hsLen)

				binary.BigEndian.PutUint16(res[pos:pos+2], uint16(extTotalLen+delta))
				binary.BigEndian.PutUint16(res[cur+2:cur+4], uint16(extLen+delta))
				listLen := int(binary.BigEndian.Uint16(res[cur+4:cur+6])) + delta
				binary.BigEndian.PutUint16(res[cur+4:cur+6], uint16(listLen))
				binary.BigEndian.PutUint16(res[cur+7:cur+9], uint16(len(newBytes)))
				return res
			}
		}
		cur += 4 + extLen
	}
	return pkt
}
