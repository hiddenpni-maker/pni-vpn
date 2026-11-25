# PNI — اپلیکیشن جامع عبور از فیلترینگ ایران

**PNI** یک کلاینت و جعبه ابزار کامل ضدسانسور برای اندروید است که با ترکیب قابلیت‌های کلیدی پروژه‌های زیر ساخته شده:
- **Soldier-Client** (`moorvye/Soldier-Client`): موتور محلی **SNI Spoof (`127.0.0.1:40443`)**، مبدل کانفیگ اسپوف، رمزنگاری امن کانفیگ (`pni://enc:...`) و IP Lookup.
- **PattN / PattNG** (`patterniha/PattN`, `PattNG`): پشتیبانی از `cipherSuites` سفارشی، فینگرپرینت `unsafe`، پچ `Xray-core` برای اتصال غیررمزنگاری‌شده VLESS/Trojan روی آدرس عمومی و `lyrebird` (`obfs4`, `snowflake`, `webtunnel`, `meek`).
- **BPB-VPN** (`bia-pain-bache/BPB-VPN`): ویزارد ساخت **Cloudflare Worker** داخل برنامه، Fragment و UDP Noise، زنجیره پروکسی (Chain Proxy)، Psiphon، Tor و پل‌ها.
- **Lantern v10** (`getlantern/lantern`): هسته `sing-box-minimal` + `outline-sdk` برای Hysteria2، TUIC v5، SSH و تاکتیک‌های ضد DPI.
- **SenPai Scanner** (`MatinSenPai/SenPaiScanner`): اسکنر چندشبکه‌ای (Cloudflare + **Akamai** + Fastly + Gcore + WARP) همراه با **Anti-DPI ClientHello Fragment**، حالت ملایم **Gentle** برای اپراتورهای ایران، **Neighbor Scan** و تست سرعت واقعی Xray مرحله ۲.

---

## قابلیت‌های کلیدی PNI
1. **پروفایل‌های آماده اپراتور و مودم‌های ایران**: همراه اول، ایرانسل موبایل، رایتل، شاتل موبایل، مودم TD-LTE (ایرانسل/مبین‌نت/زیتل)، ADSL/VDSL (مخابرات/شاتل/آسیاتک) و فیبر نوری (FTTH).
2. **اتصال تونل DNS (مشابه AM Tunnel / SlowDNS)**: پشتیبانی از `dnstt` روی UDP پورت ۵۳، DoH و DoT همراه با SSH / VLESS / Trojan / SOCKS روی بستر DNS و ابزار تست ریسالور پورت ۵۳.
3. **اسکنر آیپی چندشبکه‌ای**: کلودفلر (`cf_v4.txt`)، آکمای (`akamai_v4.txt`)، Fastly، Gcore، WARP و لیست دلخواه + خروجی لینک، Base64، Sing-box و Clash.
4. **دکتر شبکه و راهنمای تعاملی فارسی**: تشخیص مسمومیت DNS، تست هندشیک کلودفلر و Akamai و تست UDP/QUIC همراه با آموزش گام‌به‌گام فارسی.
5. **کانال تلگرام رسمی:** [https://t.me/otaqe_pni](https://t.me/otaqe_pni) (`@otaqe_pni`)
6. **آیکون اختصاصی روباه و اینترنت**: طراحی‌شده با نماد روباه و امواج شبکه در mipmap و آیکون تطبیقی اندروید.

---

## ساخت پروژه (Build)

### روش ۱: ساخت خودکار با GitHub Actions (پیشنهادی)
این مخزن دارای فایل [`.github/workflows/build-pni.yml`](.github/workflows/build-pni.yml) است که به‌صورت خودکار:
1. ابزارهای Go، Rust، Android SDK 35 و NDK 27 را نصب می‌کند؛
2. `pni-core.aar` (Xray + PattN + Scanner + SNI Spoof) و `libpnisingbox.so` را کامپایل می‌کند؛
3. باینری‌های `libhev-socks5-tunnel.so`، `libtor.so`، `liblyrebird.so`، `libpsiphon.so` و `libaether.so` را می‌سازد/دریافت می‌کند؛
4. تست‌ها و فایل APK را خروجی می‌دهد.

### روش ۲: ساخت محلی روی ویندوز
```powershell
.\scripts\setup-windows-tools.ps1
```
و سپس در Git Bash / WSL:
```bash
bash scripts/fetch-assets.sh
bash scripts/build-cores.sh
bash scripts/build-binaries.sh
./gradlew assembleDebug
```

## مجوز (License)
مطابق با پروژه‌های بالادستی (`Soldier-Client`, `PattN/PattNG`, `BPB-VPN`, `Lantern`) تحت مجوز **GNU GPL v3.0** و اسکنر الهام‌گرفته از `SenPaiScanner` (**MIT**).
