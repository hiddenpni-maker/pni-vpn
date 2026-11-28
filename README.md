<div align="center">
  <img src="app/src/main/res/drawable-nodpi/pni_fox_logo.png" alt="PNI Fox Logo" width="148" />
  <h1>PNI — Persian Network Intelligence</h1>
  <p>
    <strong>کلاینت و جعبه ابزار جامع اندروید برای عبور از فیلترینگ ایران — ویژه تمام اپراتورها، مودم‌ها و شرایط شبکه</strong><br/>
    <em>All-in-One Android Censorship Circumvention Client, Multi-CDN Scanner &amp; DNS Tunnel Suite for Iran</em>
  </p>

  <p>
    <a href="#فارسی"><strong>🇮🇷 نسخه فارسی</strong></a> •
    <a href="#english"><strong>🇬🇧 English</strong></a> •
    <a href="#downloads"><strong>⬇️ دانلود APK</strong></a> •
    <a href="https://t.me/otaqe_pni"><strong>📢 کانال تلگرام: @otaqe_pni</strong></a>
  </p>

  <p>
    <a href="https://github.com/hiddenpni-maker/pni-vpn/actions/workflows/build-pni.yml">
      <img alt="Build PNI APK" src="https://github.com/hiddenpni-maker/pni-vpn/actions/workflows/build-pni.yml/badge.svg" />
    </a>
    <a href="https://github.com/hiddenpni-maker/pni-vpn/releases/latest">
      <img alt="Latest Release" src="https://img.shields.io/github/v/release/hiddenpni-maker/pni-vpn?style=flat-square&color=10b981" />
    </a>
    <a href="https://t.me/otaqe_pni">
      <img alt="Telegram Channel" src="https://img.shields.io/badge/Telegram-@otaqe__pni-26A5E4?style=flat-square&logo=telegram" />
    </a>
    <img alt="Android 7.0+" src="https://img.shields.io/badge/Android-7.0%2B%20(API%2024%2B)-34d399?style=flat-square&logo=android" />
    <img alt="License GPL-3.0" src="https://img.shields.io/badge/License-GPL--3.0-f59e0b?style=flat-square" />
  </p>
</div>

---

<a name="downloads"></a>
## ⬇️ لینک‌های دانلود مستقیم (Direct APK Downloads)

| نسخه / Architecture | مناسب برای / Best For | لینک مستقیم دانلود / Direct Download |
| :--- | :--- | :--- |
| **ARM64 (`arm64-v8a`)** *(پیشنهادی)* | اکثر گوشی‌های اندروید جدید (۶۴ بیتی) | [⬇️ Download `PNI-arm64-v8a.apk`](https://github.com/hiddenpni-maker/pni-vpn/releases/latest/download/PNI-arm64-v8a.apk) |
| **ARMv7 (`armeabi-v7a`)** | گوشی‌ها و تبلت‌های قدیمی‌تر (۳۲ بیتی) | [⬇️ Download `PNI-armeabi-v7a.apk`](https://github.com/hiddenpni-maker/pni-vpn/releases/latest/download/PNI-armeabi-v7a.apk) |
| **x86_64** | شبیه‌سازها و تبلت‌های اینتل/AMD ۶۴ بیتی | [⬇️ Download `PNI-x86_64.apk`](https://github.com/hiddenpni-maker/pni-vpn/releases/latest/download/PNI-x86_64.apk) |
| **Universal (همگانی)** | همه دستگاه‌ها (در صورت عدم اطمینان از مدل CPU) | [⬇️ Download `PNI-universal.apk`](https://github.com/hiddenpni-maker/pni-vpn/releases/latest/download/PNI-universal.apk) |
| **Checksums (`SHA256SUMS.txt`)** | بررسی اصالت و سلامت فایل‌ها | [🔐 `SHA256SUMS.txt`](https://github.com/hiddenpni-maker/pni-vpn/releases/latest/download/SHA256SUMS.txt) |

> 📢 **کانال تلگرام رسمی PNI (آپدیت‌ها، آیپی‌های تمیز، پل‌های Tor و آموزش‌ها):** [**https://t.me/otaqe_pni**](https://t.me/otaqe_pni) (`@otaqe_pni`)

---

<a name="فارسی"></a>
## 🇮🇷 معرفی کامل پروژه (فارسی)

**PNI** یک کلاینت نیتیو اندروید (Kotlin + Jetpack Compose Material 3) است که بهترین قابلیت‌های ۵ پروژه مطرح ضدسانسور را در یک اپلیکیشن واحد، سبک و فارسی‌محور ترکیب کرده است تا کاربران ایران روی **همه اپراتورها و مودم‌ها** پایدارترین مسیر اتصال را پیدا کنند.

### ✨ قابلیت‌ها و پروتکل‌های یکپارچه‌شده
* **هسته Xray-core + پچ‌های PattN / PattNG:**
  * پشتیبانی کامل از `VLESS`، `VMess`، `Trojan`، `Shadowsocks`، `WireGuard`، `Reality`، `XHTTP / SplitHTTP`، `WS`، `gRPC` و `HTTPUpgrade`.
  * پشتیبانی از **`cipherSuites` سفارشی**، فینگرپرینت **`unsafe`** و اتصال غیررمزنگاری‌شده VLESS/Trojan روی آدرس‌های عمومی (PattN).
  * تنظیمات کامل **TLS Fragment**، **UDP Noise** و **زنجیره پروکسی (Chain Proxy / `dialerProxy`)**.
* **هسته Lantern v10 (`sing-box-minimal` + `outline-sdk`):**
  * پشتیبانی از پروتکل‌های **Hysteria2** (همراه با obfs `salamander`)، **TUIC v5**، **SSH Tunnel** و حالت‌های ضد DPI (`split` / `tlsfrag`).
* **تونل DNS مشابه AM Tunnel (`DNSTT / SlowDNS`):**
  * اتصال از طریق درخواست‌های DNS روی **UDP پورت ۵۳**، **DoH** و **DoT** همراه با `uTLS`.
  * انتخاب تونل داخلی روی بستر DNS: **SSH روی DNS**، **SOCKS5 مستقیم**، **VLESS روی DNS** یا **Trojan روی DNS**.
  * ابزار داخلی **تست سریع پورت ۵۳ / TXT** برای پیدا کردن Resolver سازگار با اپراتور شما + پشتیبانی از لینک‌های `dnstt://`.
* **اسکنر آیپی چندشبکه‌ای (الهام‌گرفته از SenPai Scanner + BPB):**
  * پشتیبانی از ۶ حالت: **Cloudflare**، **Akamai Edge**، **Fastly**، **Gcore**، **WARP / MASQUE** و **لیست دلخواه** (IP، CIDR، رنج و دامنه).
  * **حالت ملایم (`Gentle Mode`)** برای جلوگیری از افت یا قطع آنتن اپراتورهای سخت‌گیر ایران.
  * **Anti-DPI حین اسکن** (تکه‌تکه کردن بسته‌های TLS ClientHello)، **Neighbor Scan** در رنج `/24` و **تست سرعت واقعی مرحله ۲ با Xray**.
  * خروجی یک‌لمسی به صورت `IP:Port`، لینک‌های بازنویسی‌شده، ساب Base64، Sing-box JSON و Clash YAML.
* **ابزارهای تخصصی Soldier-Client و BPB-VPN:**
  * **ویزارد ساخت Cloudflare Worker (BPB)** داخل برنامه (ساخت خودکار Worker + DoH + تولید لینک VLESS).
  * **موتور محلی SNI Spoof (`127.0.0.1:40443`)** و مبدل خودکار کانفیگ به Spoof.
  * **رمزنگاری / رمزگشایی امن کانفیگ (`pni://enc:...`)**، **IP Lookup با DoH** و اسکنر QR.
* **مسیرهای اضطراری بدون سرور:**
  * **Aether (WARP / MASQUE)**، **Psiphon Exit** و **Tor + Lyrebird** (`obfs4`, `snowflake`, `webtunnel`, `meek`) با دریافت خودکار پل‌های ایران.
* **بهینه‌سازی هوشمند اپراتورها و دکتر شبکه:**
  * پروفایل آماده برای **همراه اول**، **ایرانسل موبایل**، **رایتل**، **شاتل موبایل**، **مودم TD-LTE**، **ADSL/VDSL (مخابرات/شاتل/آسیاتک)** و **فیبر نوری (FTTH)**.
  * **دکتر شبکه (Network Doctor):** تشخیص مسمومیت DNS، تست هندشیک کلودفلر و آکمای و بررسی UDP/QUIC با پیشنهاد خودکار پروتکل مناسب + بخش **راهنمای تعاملی فارسی**.

---

<a name="english"></a>
## 🇬🇧 Project Overview (English)

**PNI (Persian Network Intelligence)** is a unified, native Android VPN & circumvention suite built with **Kotlin + Jetpack Compose (Material 3)** and modular Go/C/Rust native cores. It combines proven techniques from five open-source projects into a single resilient tool tailored for severe network filtering and deep packet inspection (DPI) in Iran across mobile carriers, TD-LTE, ADSL/VDSL modems, and FTTH fiber.

### ✨ Key Capabilities & Integrated Engines
* **Modified Xray-core (`patterniha/xray-core` + `gomobile`):**
  * Full support for `VLESS`, `VMess`, `Trojan`, `Shadowsocks`, `WireGuard`, `Reality`, `XHTTP/SplitHTTP`, `WebSocket`, `gRPC`, and `HTTPUpgrade`.
  * Includes **PattN/PattNG** extensions: custom TLS `cipherSuites`, `unsafe` TLS fingerprint, unencrypted public VLESS/Trojan support, **TLS Fragment**, **UDP Noise**, and **Proxy Chaining (`dialerProxy`)**.
* **Lantern v10 Open-Source Stack (`sing-box-minimal` + `outline-sdk`):**
  * Native `libpnisingbox.so` backend supporting **Hysteria2** (with Salamander obfs), **TUIC v5**, **SSH Tunnel**, and Outline TCP `split`/`tlsfrag` anti-DPI transports.
* **AM Tunnel-style DNS Tunneling (`DNSTT / SlowDNS`):**
  * Encapsulates traffic over DNS queries via **UDP Port 53**, **DoH**, or **DoT** (`uTLS` camouflaged).
  * Supports **SSH over DNS**, **Direct SOCKS5**, or **VLESS/Trojan over DNS**, plus a built-in **Port 53 TXT Resolver Tester** and `dnstt://` share link support.
* **Multi-CDN Endpoint Scanner (SenPai Scanner + BPB inspired):**
  * Built-in CIDR pools for **Cloudflare**, **Akamai Edge**, **Fastly**, **Gcore**, **WARP/MASQUE**, plus custom IPs, CIDRs, ranges, and domains.
  * Features **Gentle Mode** (rate-limited for strict ISPs), **Anti-DPI ClientHello fragmentation** during probes, **`/24` Neighbor Discovery**, **Phase-2 real Xray throughput testing**, and exports for `IP:Port`, share links, Base64, Sing-box JSON, and Clash YAML.
* **Cloudflare Worker Wizard, SNI Spoof & Crypto Utilities:**
  * One-tap **BPB Cloudflare Worker Deployer** directly inside the app.
  * Local **SNI Spoof proxy (`127.0.0.1:40443`)** with ClientHello fragmentation/rewriting + Spoof Config Converter.
  * AES-256-GCM **Config Encrypt/Decrypt (`pni://enc:...`)**, DoH-backed **Domain IP Lookup**, and QR scanner.
* **Serverless Emergency Exits & Network Doctor:**
  * Bundled **Aether (WARP / MASQUE)**, **Psiphon**, and **Tor + Lyrebird** (`obfs4`, `snowflake`, `webtunnel`, `meek`) with auto-fetched bridges.
  * **Network Doctor** probes DNS poisoning, Cloudflare/Akamai TLS reachability, and UDP/QUIC status, pairing with presets for **MCI**, **Irancell**, **Rightel**, **Shatel Mobile**, **TD-LTE**, **ADSL/VDSL**, and **FTTH**.

---

## 🏗️ Architecture & Local Build

```text
pni/
├── core-go/
│   ├── xray-scanner/      # Xray-core (PattN patch) + SNI Spoof + Multi-CDN Scanner -> pni-core.aar
│   └── singbox-lantern/   # Lantern sing-box-minimal + outline-sdk -> libpnisingbox.so
├── app/                   # Native Kotlin + Jetpack Compose Material 3 Android Client
├── scripts/               # Automated asset fetch, gomobile, NDK & binary build scripts
└── .github/workflows/     # Automated CI APK builder & GitHub Releases publisher
```

### Build on Linux / Git Bash + Android NDK
```bash
bash scripts/fetch-assets.sh
bash scripts/build-cores.sh
bash scripts/build-binaries.sh
./gradlew assembleRelease
```

---

Licensed under **GNU GPL v3.0** — see [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).

---
<div align="center">
  📢 <strong>Telegram Channel:</strong> <a href="https://t.me/otaqe_pni">https://t.me/otaqe_pni</a> (<code>@otaqe_pni</code>)
</div>

