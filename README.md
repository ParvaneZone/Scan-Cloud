<p align="center">
  <img src="logo.png" width="130" alt="Scan-Cloud logo">
</p>

<h1 align="center">Scan-Cloud</h1>

<p align="center">
  اسکنر IP کلودفلر و Fastly و SNI برای اندروید — رایگان و متن‌باز<br>
  Cloudflare / Fastly IP and SNI scanner for Android — free and open source
</p>

<p align="center">
  <a href="https://github.com/ParvaneZone/Scan-Cloud/releases">دانلود آخرین نسخه / Download</a>
  &nbsp;|&nbsp;
  <a href="https://t.me/ParvaneZone">کانال تلگرام</a>
  &nbsp;|&nbsp;
  <a href="https://t.me/Parv49e">پیام به سازنده</a>
</p>

---

<div dir="rtl" align="right">

## معرفی

**Scan-Cloud** یک برنامهٔ اندروید است که با اینترنت خودِ گوشی شما رنج‌های IP شبکه‌های Cloudflare و Fastly را اسکن می‌کند و نشان می‌دهد کدام IP پینگ و سرعت بهتری دارد. همچنین می‌تواند بهترین SNI / تارگت (مثلاً برای Reality) را از بین حدود ۱۵۰ سایت خارجی پیدا کند.

نتیجه به اپراتور، زمان و شرایط شبکهٔ خودتان بستگی دارد. برای همین هر کس باید روی اینترنت خودش اسکن کند.

## امکانات

- اسکن IP کلودفلر و Fastly (لیست رنج‌ها از منبع رسمی دانلود می‌شود و اگر نشد از لیست داخلی استفاده می‌کند)
- انتخاب پورت از بین پورت‌های پشتیبانی‌شدهٔ کلودفلر (۴۴۳، ۲۰۵۳، ۲۰۸۳، ۲۰۸۷، ۲۰۹۶، ۸۴۴۳، ۸۰، ۸۰۸۰ و ...)
- سه حالت اسکن: سریع، معمولی، عمیق
- نمایش پینگ و سرعت دانلود هر IP
- ذخیرهٔ IPهای سالم در یک فایل متنی
- تست با کانفیگ خودتان (vless / trojan با TLS و ws، grpc یا xhttp): IP اسکن‌شده جای آدرس کانفیگ می‌نشیند و فقط IPهایی که واقعاً جواب بدهند می‌مانند. خروجی را می‌توان به صورت فایل «همهٔ کانفیگ‌ها» ذخیره کرد.
- اسکنر SNI / تارگت: پینگ، زمان پاسخ، سرعت، پشتیبانی از TLS 1.3 و h2
- تم تیره و روشن، زبان فارسی و انگلیسی
- بررسی آپدیت از همین صفحهٔ Releases (از منوی سه‌خط بالای صفحه)

## دانلود و نصب

1. به صفحهٔ [Releases](https://github.com/ParvaneZone/Scan-Cloud/releases) بروید.
2. آخرین نسخه را باز کنید و فایل `.apk` را از بخش **Assets** دانلود کنید.
3. فایل را باز و نصب کنید. اگر گوشی اجازهٔ «نصب از منبع ناشناس» را خواست، برای مرورگر یا فایل‌منیجر فعالش کنید.

## موقع نصب ارور می‌دهد؟ دلیل و راه‌حل

هنگام نصب ممکن است پنجره‌ای از **Google Play Protect** ببینید با این مضمون:

> App blocked to protect your device — Play Protect hasn't seen an app from this developer before. It may be unsafe.

**دلیلش چیست؟**
Play Protect به امضای دیجیتال برنامه نگاه می‌کند. این برنامه در فروشگاه‌ها منتشر نشده و توسط یک توسعه‌دهندهٔ مستقل با امضای تازه ساخته شده است. گوگل هنوز این امضا را روی تعداد زیادی گوشی ندیده، پس به صورت پیش‌فرض هشدار می‌دهد. این هشدار برای **هر برنامه‌ای** که بیرون از فروشگاه نصب شود و توسعه‌دهندهٔ ناشناس داشته باشد می‌آید و به معنی وجود کد مخرب نیست. با نصب بیشتر روی گوشی‌های مختلف، این هشدار معمولاً کم‌تر می‌شود.

**چطور رد شویم؟**

1. در پنجرهٔ هشدار، **نه** روی «Got it»، بلکه روی **More details** (جزئیات بیشتر) بزنید.
2. گزینهٔ **Install anyway** (به هر حال نصب کن) را بزنید.
3. اگر این گزینه نبود: Play Store ← آیکون پروفایل ← **Play Protect** ← چرخ‌دنده ← گزینهٔ «Scan apps with Play Protect» را موقتاً خاموش کنید، برنامه را نصب کنید و بعد دوباره روشنش کنید.
4. اگر پنجره‌ای گفت «Send app for scanning»، می‌توانید همان را بزنید.

**ارور «App not installed as package conflicts»؟**
یعنی نسخهٔ قدیمی‌تری با امضای دیگر روی گوشی هست. ابتدا نسخهٔ قبلی Scan-Cloud / Parvane Scanner را پاک کنید و بعد نصب کنید. از نسخهٔ 2.0 به بعد امضا ثابت است و نسخه‌ها روی هم نصب می‌شوند.

## متن‌باز و امنیت

- این پروژه **کاملاً رایگان و متن‌باز** است و همهٔ کد آن همین‌جا در همین مخزن قابل بررسی است.
- **هیچ کد مخرب، تبلیغاتی یا جاسوسی** داخل آن نیست. برنامه هیچ داده‌ای برای سازنده ارسال نمی‌کند و همهٔ تست‌ها روی گوشی خودتان انجام می‌شود.
- فایل APK هر نسخه را GitHub Actions مستقیماً از همین سورس می‌سازد (فایل ورکفلو در پوشهٔ `.github/workflows` قابل مشاهده است). می‌توانید خودتان هم همین مخزن را Fork و بیلد کنید.
- دسترسی‌های برنامه فقط دو مورد است: `INTERNET` برای اسکن، و `REQUEST_INSTALL_PACKAGES` برای نصب آپدیت.
- برنامه فقط به این مقصدها وصل می‌شود: IPهای کلودفلر و Fastly که خودتان اسکن می‌کنید، `speed.cloudflare.com` و `files.pythonhosted.org` برای تست سرعت، لیست رنج‌ها (`cloudflare.com` و `api.fastly.com`)، سایت‌های لیست SNI، `api.github.com` برای بررسی آپدیت، و در صورت تست با کانفیگ، سرور کانفیگ خودِ شما.
- هیچ‌کس حق فروش این برنامه یا نسخه‌های تغییریافتهٔ آن را ندارد. اگر برای آن پول پرداخته‌اید، فریب خورده‌اید.

## دربارهٔ نحوهٔ ساخت (وایب کدینگ)

این پروژه با **وایب کدینگ** (کدنویسی با کمک هوش مصنوعی) نوشته شده است. هدف این بود که کسانی که **به کامپیوتر یا سیستم دسترسی ندارند** و فقط گوشی دارند هم یک ابزار کاربردی داشته باشند. حتی ساخت APK هم کاملاً روی گوشی و با GitHub Actions انجام شده است.

به همین دلیل ممکن است جاهایی کد کامل نباشد یا باگ داشته باشد. اگر مشکلی دیدید یا پیشنهادی داشتید، در Issues بنویسید یا در تلگرام به سازنده پیام بدهید.

## حمایت و دونیت

این برنامه رایگان است. اگر به کارتان آمد و خواستید از ادامهٔ توسعه حمایت کنید:

**USDT (BEP20)**
```
0xDD5A888843aA409579303BC86234bFc89b5cC909
```

**GRAM (TON)**
```
UQAyNjSgu3J9Z-UgpcXcnKeaW5XaTY7SznbVYrvxlXW4EIQs
```

**TRON**
```
TVBj3Dod3SARhqpob44iKwXWoUVYVgY9YE
```

لطفاً هر ارز را فقط روی شبکهٔ خودش ارسال کنید، چون ارسال روی شبکهٔ اشتباه قابل برگشت نیست.

## ارتباط

- کانال تلگرام: [t.me/ParvaneZone](https://t.me/ParvaneZone)
- سازنده: [t.me/Parv49e](https://t.me/Parv49e)

</div>

---

## English summary

**Scan-Cloud** is a free, open-source Android app that scans Cloudflare and Fastly IP ranges using your own mobile connection, ranks IPs by ping and speed, can test them against your own vless/trojan config (ws, grpc, xhttp), and finds good SNI / Reality targets.

**Install error:** Google Play Protect may show "App blocked to protect your device — Play Protect hasn't seen an app from this developer before". This happens because the app is signed by an independent developer and is not distributed through a store, so Google has not seen its signing key before. It does not mean the app is malicious. Tap **More details**, then **Install anyway**. If that option is missing, temporarily turn off "Scan apps with Play Protect". If you see "package conflicts", uninstall the older version first.

**Open source:** all code is in this repository, contains no malicious code, and sends no data to the developer. APKs are built by GitHub Actions directly from this source.

**Vibe coded:** this project was written with AI assistance (vibe coding) so people who have no access to a computer can still have a useful tool.

**Donate:** USDT (BEP20) `0xDD5A888843aA409579303BC86234bFc89b5cC909` — GRAM (TON) `UQAyNjSgu3J9Z-UgpcXcnKeaW5XaTY7SznbVYrvxlXW4EIQs` — TRON `TVBj3Dod3SARhqpob44iKwXWoUVYVgY9YE`
