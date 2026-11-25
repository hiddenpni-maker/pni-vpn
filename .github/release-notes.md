<div align="center">
  <h2>PNI v1.0.0 — دسترسی آزاد و هوشمند برای همه شبکه‌های ایران</h2>
  <p>📢 <strong>کانال تلگرام رسمی / Official Telegram:</strong> <a href="https://t.me/otaqe_pni">https://t.me/otaqe_pni</a> (<code>@otaqe_pni</code>)</p>
</div>

---

### ⬇️ دانلود مستقیم فایل APK بر اساس پردازنده گوشی (Direct APK Downloads)

| معماری (ABI) | توضیح انتخاب فایل | فایل |
| :--- | :--- | :--- |
| **`arm64-v8a`** *(پیشنهادی)* | مناسب اکثر گوشی‌های اندروید جدید (۶۴ بیتی) — سبک‌ترین نسخه | `PNI-arm64-v8a.apk` |
| **`armeabi-v7a`** | مناسب گوشی‌ها و تبلت‌های قدیمی‌تر (۳۲ بیتی) | `PNI-armeabi-v7a.apk` |
| **`x86_64`** | مناسب شبیه‌سازها و دستگاه‌های اینتل/AMD ۶۴ بیتی | `PNI-x86_64.apk` |
| **`universal`** | نسخه همگانی شامل تمام معماری‌ها | `PNI-universal.apk` |
| **`SHA256SUMS.txt`** | هش SHA-256 برای بررسی صحت فایل‌ها | `SHA256SUMS.txt` |

*(لینک‌های مستقیم دانلود هر فایل در بخش **Assets** همین صفحه Release به‌صورت جداگانه قرار دارند.)*

---

### 🇮🇷 ویژگی‌های این نسخه
* ترکیب هسته‌های **Xray-core (پچ PattN)**، **Lantern v10 (`sing-box-minimal` + `outline-sdk`)**، **Aether (WARP / MASQUE)**، **Psiphon** و **Tor + Lyrebird**.
* اضافه شدن **اتصال تونل DNS (DNSTT / SlowDNS)** مشابه برنامه **AM Tunnel** (روی UDP پورت ۵۳، DoH و DoT به همراه SSH / SOCKS5 / VLESS و ابزار تست ریسالور پورت ۵۳).
* **اسکنر آیپی چندشبکه‌ای** برای **Cloudflare**، **Akamai Edge**، **Fastly**، **Gcore**، **WARP** و رنج دلخواه با حالت **Gentle**، **Anti-DPI**، **Neighbor Scan** و تست سرعت مرحله دوم Xray.
* **ویزارد ساخت Worker کلودفلر (BPB)**، موتور محلی **SNI Spoof (`127.0.0.1:40443`)**، رمزنگاری کانفیگ (`pni://enc:...`) و **IP Lookup با DoH**.
* **پروفایل‌های آماده اپراتورها و مودم‌ها** (همراه اول، ایرانسل، رایتل، شاتل موبایل، TD-LTE، ADSL مخابرات و فیبر نوری) + **دکتر شبکه** + راهنمای جامع فارسی + آیکون اختصاصی روباه و اینترنت.

### 🇬🇧 Highlights
* Unified **Xray-core (PattN patches: `unsafe`, custom `cipherSuites`, unencrypted public VLESS/Trojan, Fragment, Noise)** + **Lantern `sing-box-minimal` & `outline-sdk`** (`Hysteria2`, `TUIC v5`, `SSH`, TCP split/disorder).
* **AM Tunnel-style DNS Tunneling (`dnstt`)** over UDP :53, DoH, and DoT with SSH/SOCKS/VLESS payloads and built-in Port 53 resolver checker.
* **Multi-CDN Scanner** (Cloudflare, Akamai, Fastly, Gcore, WARP, Custom CIDR/Domain) with Gentle ISP rate-limiting, Anti-DPI ClientHello fragmentation, and Xray speed validation.
* **BPB Cloudflare Worker Wizard**, **Local SNI Spoof (`127.0.0.1:40443`)**, **Config Encryption**, **Iran ISP Presets**, **Network Doctor**, and Persian Guides.
