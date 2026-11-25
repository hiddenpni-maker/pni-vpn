module ir.pni/core

go 1.27

require (
	github.com/xtls/xray-core v1.260327.1-0.20261109052709-676eaf66f8f0
	golang.org/x/mobile v0.0.0-20261011034528-197770444867
	golang.org/x/time v0.15.0
)

// Use patterniha/xray-core fork for PattN features:
// unencrypted VLESS/Trojan on public addresses, unsafe TLS fingerprint, custom cipherSuites
replace github.com/xtls/xray-core => github.com/patterniha/xray-core v0.0.0-20261124210119-7131abd488e0
