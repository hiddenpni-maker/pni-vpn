<div align="center">
  <h2>PNIFOX v2.2.0 — ویندوز (Windows 10/11) و اندروید</h2>
  <p>📢 <strong>کانال تلگرام رسمی / Official Telegram:</strong> <a href="https://t.me/otaqe_pni">https://t.me/otaqe_pni</a> (<code>@otaqe_pni</code>)</p>
</div>

---

### ⬇️ دانلود مستقیم نسخه اندروید (Android APKs) و ویندوز (Windows x64)

| پلتفرم / معماری | توضیح انتخاب فایل | فایل |
| :--- | :--- | :--- |
| **ویندوز نصب‌کننده (Setup EXE)** | نصبی استاندارد برای Windows 10 و Windows 11 (۶۴ بیتی) | `pnifox-windows-x64-setup.exe` |
| **ویندوز پرتابل (Portable ZIP)** | بدون نیاز به نصب — اکسترکت و اجرای مستقیم `pnifox.exe` | `pnifox-windows-x64-portable.zip` |
| **`arm64-v8a`** *(اندروید پیشنهادی)* | مناسب اکثر گوشی‌های اندروید جدید (۶۴ بیتی) — سبک‌ترین نسخه | `PNI-arm64-v8a.apk` |
| **`armeabi-v7a`** *(اندروید قدیمی‌تر)* | مناسب گوشی‌ها و تبلت‌های ۳۲ بیتی | `PNI-armeabi-v7a.apk` |
| **`x86_64`** *(شبیه‌ساز / تبلت)* | مناسب شبیه‌سازها و دستگاه‌های اینتل/AMD | `PNI-x86_64.apk` |
| **`universal`** *(همگانی)* | شامل تمام معماری‌های اندروید در یک فایل | `PNI-universal.apk` |

*(تمام لینک‌های مستقیم دانلود در بخش **Assets** همین صفحه Release قرار دارند.)*

---

### ✨ قابلیت‌های کلیدی نسخه v2.2.0
* **روش شیر و خورشید — فرانتینگ Akamai / CDN در Psiphon:** پشتیبانی از `--psiphon-only --psiphon-mode cdn|direct|auto`، معکوس `Psiphon CDN → WARP` (`--psiphon-reverse`)، انتخاب پکیج‌های CDN (`psiphon-akamai`, `cloudflare`, `fastly`, ...), وارد کردن و تست رنج/آی‌پی‌های لبه Akamai و فهرست دلخواه `CDN SNI hostnames` + اعمال مستقیم از اسکنر Akamai.
* **اسکنر فوق‌سریع و متنوع چندزیرشبکه‌ای (Multi-Subnet):** اصلاح باگ قفل‌شدن اسکنر روی یک رنج و افزودن اسکن دوفازی ۶۴ رشته‌ای (`Turbo Fast 64x`، `Balanced TLS`، `Deep Multi-Port`) با پوشش تمام پیشوندهای CIDR (`/8` تا `/24`)؛ اندپوینت AETHER هم دیگر روی یک آی‌پی قفل نمی‌ماند و آزادانه رنج‌های متنوع را اسکن می‌کند.
* **اتصال مجدد و رفرش خودکار هنگام قطع و وصل اینترنت:** پایش تغییرات شبکه (Android `NetworkCallback` و Windows Adapter + Process + Health Watchdog) تا هنگام قطع و وصل اینترنت یا تغییر وای‌فای/دیتا، بدون نیاز به خاموش و روشن دستی کاربر، تونل رفرش و مجدداً متصل شود.
* **بخش گیم و حالت‌های آماده کاربردی (Gaming & Scenarios):** تغییر DNS گیمینگ ضدتحریم بدون افت پینگ (رادار، بگذر، ۴۰۳، شکن، الکترو)، ضدDPI محلی بدون نیاز به سرور، تونل UDP/QUIC v2 گیمینگ و سناریوهای آماده برای گیم، استریم، وب‌گردی و هوش مصنوعی (جمنای/ChatGPT).
* **بهینه‌سازی WinTUN و پیش‌فرض ECH:** رفع کامل حلقه DNS در ویندوز (بایپس پردازه‌های هسته و مسیریابی مستقیم DNS Changer)، خاموش کردن پیش‌فرض ECH آزمایشی، پشتیبانی از پروتکل‌های جدید `tuic://`، `anytls://`، `xhttp` و `ss2022` و لوگوی جدید پنجه در ویندوز و اندروید.

### 🇬🇧 Highlights (v2.2.0)
* **Shir-o-Khorshid Akamai / CDN Fronting Modes:** Standalone Psiphon CDN/Direct/Auto (`--psiphon-only`), Reverse WARP-over-Psiphon (`--psiphon-reverse`), CDN set selectors (`psiphon-akamai`, Cloudflare, Fastly, CloudFront, ...), custom Edge IP/CIDR ranking, custom `CDN SNI hostnames`, and direct export from the Akamai Scanner.
* **High-Diversity Two-Phase 64-Worker Scanner:** Fixed subnet lock-in across `/8`–`/24` CIDRs with shuffled multi-prefix sampling + `Turbo Fast (64x)`, `Balanced TLS`, and `Deep Multi-Port` scan profiles; AETHER auto-scan no longer silently pins a single IP.
* **Automatic Internet Drop Recovery:** Native Android `ConnectivityManager.NetworkCallback` + Windows adapter/process/health watchdog automatically refreshes and reconnects tunnels when internet drops and comes back.
* **Gaming Hub, Goal Scenarios, Modern Protocols & Fixes:** Anti-sanction Gaming DNS (0ms proxy overhead), Local DPI Desync, `tuic://`/`anytls://`/`xhttp`/`ss2022`, ECH off by default, clean WinTUN routing, and new pnifox paw icon.
