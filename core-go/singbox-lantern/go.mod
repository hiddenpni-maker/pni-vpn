module ir.pni/singbox

go 1.26

require (
	github.com/sagernet/sing-box v1.11.4
	golang.getoutline.org/sdk/x v0.3.0-lantern.3
)

// Match Lantern v10's hardened sing-box-minimal & outline-sdk forks
replace github.com/sagernet/sing-box => github.com/getlantern/sing-box-minimal v1.14.2-lantern.3

replace github.com/sagernet/sing-shadowsocks => github.com/getlantern/sing-shadowsocks v0.2.8-lantern.3

replace github.com/sagernet/sing-tun => github.com/getlantern/sing-tun v0.0.0-20261110195139-053600b0af9b

replace github.com/sagernet/gvisor => github.com/sagernet/gvisor v0.0.0-20260727.0-sing-box-mod.1

replace golang.getoutline.org/sdk/x => github.com/getlantern/outline-sdk/x v0.3.0-lantern.3

replace golang.getoutline.org/sdk => github.com/getlantern/outline-sdk v0.1.0-lantern.1
