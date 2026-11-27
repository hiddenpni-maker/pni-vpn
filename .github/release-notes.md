<div align="center">
  <h2>PNIFOX v2.3.0 — ویندوز و اندروید</h2>
  <p>📢 <strong>کانال تلگرام رسمی / Official Telegram:</strong> <a href="https://t.me/otaqe_pni">https://t.me/otaqe_pni</a> (<code>@otaqe_pni</code>)</p>
</div>

---

### ⬇️ دانلود مستقیم نسخه اندروید (Android APKs) و ویندوز (Windows x64)

| پلتفرم / معماری | توضیح انتخاب فایل | فایل |
| :--- | :--- | :--- |
| **ویندوز نصب‌کننده (Setup EXE)** | نصبی استاندارد برای ویندوز ۶۴ بیتی | `pnifox-windows-x64-setup.exe` |
| **ویندوز پرتابل (Portable ZIP)** | بدون نیاز به نصب — اکسترکت و اجرای مستقیم `pnifox.exe` | `pnifox-windows-x64-portable.zip` |
| **`arm64-v8a`** *(اندروید پیشنهادی)* | مناسب اکثر گوشی‌های اندروید جدید (۶۴ بیتی) | `PNI-arm64-v8a.apk` |
| **`armeabi-v7a`** *(اندروید قدیمی‌تر)* | مناسب گوشی‌ها و تبلت‌های ۳۲ بیتی | `PNI-armeabi-v7a.apk` |
| **`x86_64`** *(شبیه‌ساز / تبلت)* | مناسب شبیه‌سازها و دستگاه‌های اینتل/AMD | `PNI-x86_64.apk` |
| **`universal`** *(همگانی)* | شامل تمام معماری‌های اندروید در یک فایل | `PNI-universal.apk` |

---

### ✨ تغییرات نسخه v2.3.0
* **رفع مشکل اتصال Akamai CDN و جداسازی از رنج‌های WARP/کشور:** در حالت `Akamai / Psiphon CDN (مستقل)` دیگر پرچم `--psiphon-region` مربوط به WARP/سایفون (که باعث تایم‌اوت هنگام انتخاب کشورهایی مثل `CA` می‌شد) ارسال نمی‌شود و بخش اکامای کاملاً مجزا از WARP است.
* **تفکیک کامل پروتکل، نوع انتقال و زنجیره سایفون/تور:** انتخاب مستقل `Protocol` (`MASQUE`, `WireGuard`, `WG over MASQUE`, ...) و `Transport` (`HTTP/2`, `HTTP/3`) و فعال‌سازی اختیاری زنجیره سایفون یا تور روی آن بدون تداخل گزینه‌ها.
* **رابط کاربری جمع‌وجور با منوهای کشویی و دکمه «توضیحات»:** جایگزینی لیست‌های شلوغ چیپ‌ها (کشورها، پروتکل، اسکن، گیمینگ، DNS) با منوهای کشویی مرتب، مخفی‌کردن توضیحات طولانی در پنجره بازشوی «توضیحات»، حذف بخش اتصال هوشمند، و رفع بیرون‌زدگی متن‌ها در ویندوز و اندروید.
* **پاک‌سازی راحت آی‌پی‌های اعمال‌شده + رنج‌های بیشتر اسکنر:** افزودن دکمه یک‌ضرب «پاک کردن IP» در صفحه اصلی و اسکنر هر دو نسخه، همراه با گسترش رنج‌های پیش‌فرض IPv4 برای اسکن WARP/Cloudflare و Fastly.

### 🇬🇧 Highlights (v2.3.0)
* **Fixed Akamai / Psiphon CDN Handshake & Clean Separation:** Standalone Akamai CDN mode is separated cleanly from WARP and no longer passes incompatible `--psiphon-region` overrides (like `CA`) that blocked valid `GB`/`US` CDN egress nodes.
* **Independent Protocol + Transport + Exit Chain:** Choose `Protocol` (`MASQUE`, `WireGuard`, `WG over MASQUE`, ...) and `Transport` (`HTTP/2`, `HTTP/3`) freely and chain Psiphon/Tor on top without preset overrides fighting your selection.
* **Compact Dropdown Menus & On-Demand Info Popups:** Consolidated scattered chip grids across Desktop & Android into compact dropdown menus, moved verbose guides into on-demand **Info / توضیحات** dialogs, removed Smart Connect, and fixed button/label clipping.
* **One-Tap Clear Applied IPs & Expanded Default Scanner Ranges:** Added quick clear buttons for saved Endpoint & Akamai CDN IPs and expanded default WARP/Cloudflare and Fastly CIDR scan ranges.
