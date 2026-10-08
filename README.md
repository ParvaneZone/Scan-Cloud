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
  <a href="https://t.me/ParvaneZone">کانال تلگرام / Telegram channel</a>
  &nbsp;|&nbsp;
  <a href="https://t.me/Parv49e">پیام به سازنده / Contact the developer</a>
</p>

---

## معرفی | Introduction

<div dir="rtl" align="right">

**Scan-Cloud** یک برنامهٔ اندروید است که با اینترنت خودِ گوشی شما رنج‌های IP شبکه‌های Cloudflare و Fastly را اسکن می‌کند و نشان می‌دهد کدام IP پینگ و سرعت بهتری دارد. همچنین می‌تواند بهترین SNI / تارگت (مثلاً برای Reality) را از بین حدود ۱۵۰ سایت خارجی پیدا کند.

نتیجه به اپراتور، زم
این برنامه رایگان است. اگر به کارتان آمد و خواستید از ادامهٔ توسعه حمایت کنید، از آدرس‌های زیر استفاده کنید. لطفاً هر ارز را فقط روی شبکهٔ خودش ارسال کنید، چون ارسال روی شبکهٔ اشتباه قابل برگشت نیست.ان و شرایط شبکهٔ خودتان بستگی دارد. برای همین هر کس باید روی اینترنت خودش اسکن کند.

</div>

**Scan-Cloud** is an Android app that scans the IP ranges of Cloudflare and Fastly using your phone's own internet connection and shows which IPs have the best ping and speed. It can also find the best SNI / target (for example for Reality) among about 150 foreign websites.

Results depend on your operator, the time of day and your network conditions, so everyone should scan on their own connection.

## امکانات | Features

<div dir="rtl" align="right">

- اسکن IP کلودفلر و Fastly (لیست رنج‌ها از منبع رسمی دانلود می‌شود و اگر نشد از لیست داخلی استفاده می‌کند)
- پشتیبانی اختیاری از رنج‌های IPv6 و چند رنج اضافهٔ Fastly (غیررسمی؛ فقط IPهایی که جواب بدهند می‌مانند)
- انتخاب پورت از بین پورت‌های پشتیبانی‌شدهٔ کلودفلر (۴۴۳، ۲۰۵۳، ۲۰۸۳، ۲۰۸۷، ۲۰۹۶، ۸۴۴۳، ۸۰، ۸۰۸۰ و ...)
- سه حالت اسکن: سریع، معمولی، عمیق
- نمایش پینگ و سرعت دانلود هر IP
- ذخیرهٔ IPهای سالم در یک فایل متنی
- تست با کانفیگ خودتان (vless / trojan با TLS و ws، grpc یا xhttp): IP اسکن‌شده جای آدرس کانفیگ می‌نشیند و فقط IPهایی که واقعاً جواب بدهند می‌مانند. خروجی را می‌توان به صورت فایل «همهٔ کانفیگ‌ها» ذخیره کرد.
- اسکنر SNI / تارگت: پینگ، زمان پاسخ، سرعت، پشتیبانی از TLS 1.3 و h2
- تست آیپی (شبیه check-host.net): مشخصات (کشور، ISP، ASN، Reverse DNS)، پینگ ICMP و TCP، وضعیت پورت‌های رایج و بررسی HTTP/HTTPS؛ به‌همراه نمایش آیپی خودتان با دکمهٔ بررسی دوباره
- تم تیره و روشن، زبان فارسی و انگلیسی
- بررسی آپدیت از همین صفحهٔ Releases (از منوی سه‌خط بالای صفحه)

</div>

- Scan Cloudflare and Fastly IPs (the range lists are downloaded from the official sources, with a built-in fallback)
- Optional IPv6 ranges and a few extra (unofficial) Fastly blocks; only IPs that really respond are kept
- Choose the port from the ports supported by Cloudflare (443, 2053, 2083, 2087, 2096, 8443, 80, 8080, etc.)
- Three scan modes: Quick, Normal, Deep
- Ping and download speed for every IP
- Save the healthy IPs to a text file
- Test with your own config (vless / trojan over TLS with ws, grpc or xhttp): each scanned IP is placed in your config and only the IPs that really respond are kept. The result can be saved as an "all configs" file.
- SNI / target scanner: ping, response time, speed, TLS 1.3 and h2 support
- IP Test (like check-host.net): details (country, ISP, ASN, reverse DNS), ICMP and TCP ping, common TCP ports and an HTTP/HTTPS check, plus your own IP with a re-check button
- Dark and light themes, Persian and English interface
- Update check from this repository's Releases page (from the three-line menu at the top)

## دانلود و نصب | Download and install

<div dir="rtl" align="right">

1. به صفحهٔ [Releases](https://github.com/ParvaneZone/Scan-Cloud/releases) بروید.
2. آخرین نسخه را باز کنید و فایل `.apk` را از بخش **Assets** دانلود کنید.
3. فایل را باز و نصب کنید. اگر گوشی اجازهٔ «نصب از منبع ناشناس» را خواست، برای مرورگر یا فایل‌منیجر فعالش کنید.

</div>

1. Go to the [Releases](https://github.com/ParvaneZone/Scan-Cloud/releases) page.
2. Open the latest release and download the `.apk` file from the **Assets** section.
3. Open the file and install it. If your phone asks for permission to "install unknown apps", enable it for your browser or file manager.

## موقع نصب ارور می‌دهد؟ | Getting an error while installing?

<div dir="rtl" align="right">

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

</div>

While installing you may see a **Google Play Protect** window saying:

> App blocked to protect your device — Play Protect hasn't seen an app from this developer before. It may be unsafe.

**Why does it happen?**
Play Protect looks at the app's digital signature. This app is not published on any store and was built by an independent developer with a new signing key. Google has not yet seen this key on many phones, so it warns by default. The warning appears for **any** app installed from outside a store whose developer is unknown. It does not mean the app contains malicious code. The warning usually fades as the app is installed on more devices.

**How to get past it**

1. In the warning window, do **not** tap "Got it". Tap **More details** instead.
2. Tap **Install anyway**.
3. If that option is missing: Play Store → profile icon → **Play Protect** → gear icon → temporarily turn off "Scan apps with Play Protect", install the app, then turn it back on.
4. If a window offers "Send app for scanning", you can tap it.

**"App not installed as package conflicts" error?**
It means an older version with a different signature is already on your phone. Uninstall the previous Scan-Cloud / Parvane Scanner first, then install again. From version 2.0 on the signature is fixed and versions install over each other.

## متن‌باز و امنیت | Open source and security

<div dir="rtl" align="right">

- این پروژه **کاملاً رایگان و متن‌باز** است و همهٔ کد آن همین‌جا در همین مخزن قابل بررسی است.
- **هیچ کد مخرب، تبلیغاتی یا جاسوسی** داخل آن نیست. برنامه هیچ داده‌ای برای سازنده ارسال نمی‌کند و همهٔ تست‌ها روی گوشی خودتان انجام می‌شود.
- دسترسی‌های برنامه فقط دو مورد است: `INTERNET` برای اسکن، و `REQUEST_INSTALL_PACKAGES` برای نصب آپدیت.
- برنامه فقط به این مقصدها وصل می‌شود: IPهای کلودفلر و Fastly که خودتان اسکن می‌کنید، `speed.cloudflare.com` و `files.pythonhosted.org` برای تست سرعت، لیست رنج‌ها (`cloudflare.com` و `api.fastly.com`)، سایت‌های لیست SNI، `api.github.com` برای بررسی آپدیت، و در صورت تست با کانفیگ، سرور کانفیگ خودِ شما.
- هیچ‌کس حق فروش این برنامه یا نسخه‌های تغییریافتهٔ آن را ندارد. اگر برای آن پول پرداخته‌اید، فریب خورده‌اید.

</div>

- This project is **completely free and open source**, and all of its code can be reviewed right here in this repository.
- It contains **no malicious, advertising or spying code**. The app sends no data to the developer, and all tests run on your own phone.
- The app only needs two permissions: `INTERNET` for scanning and `REQUEST_INSTALL_PACKAGES` for installing updates.
- The app only connects to: the Cloudflare and Fastly IPs you scan, `speed.cloudflare.com` and `files.pythonhosted.org` for speed tests, the range lists (`cloudflare.com` and `api.fastly.com`), the sites in the SNI list, `api.github.com` for the update check, and, if you test with a config, your own config's server.
- Nobody has the right to sell this app or modified copies of it. If you paid for it, you were scammed.

## درباره نحوهٔ ساخت (وایب کدینگ) | How it was made (vibe coding)

<div dir="rtl" align="right">

این پروژه با **وایب کدینگ** (کدنویسی با کمک هوش مصنوعی) نوشته شده است. هدف این بود که کسانی که **به کامپیوتر یا سیستم دسترسی ندارند** و فقط گوشی دارند هم یک ابزار کاربردی داشته باشند. حتی ساخت APK هم کاملاً روی گوشی و با GitHub Actions انجام شده است.

به همین دلیل ممکن است جاهایی کد کامل نباشد یا باگ داشته باشد. اگر مشکلی دیدید یا پیشنهادی داشتید، در Issues بنویسید یا در تلگرام به سازنده پیام بدهید.

</div>

This project was written with **vibe coding** (coding with the help of AI). The goal was to give people who **have no access to a computer or system** and only have a phone a useful tool. Even building the APK was done entirely on a phone with GitHub Actions.

Because of this, some parts may be incomplete or contain bugs. If you find a problem or have a suggestion, open an Issue or message the developer on Telegram.

## حمایت و دونیت | Support and donate

<div dir="rtl" align="right">

این برنامه رایگان است. اگر به کارتان آمد و خواستید از ادامهٔ توسعه حمایت کنید با استارز⭐ دادن به پروژه یا از آدرس‌های زیر استفاده کنید. لطفاً هر ارز را فقط روی شبکهٔ خودش ارسال کنید، چون ارسال روی شبکهٔ اشتباه قابل برگشت نیست.

</div>

This app is free. If it was useful to you and you would like to support further development, use the addresses below. Please send each coin only on its own network, because a transfer on the wrong network cannot be recovered.

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

## ارتباط | Contact

- کانال تلگرام / Telegram channel: [t.me/ParvaneZone](https://t.me/ParvaneZone)
- سازنده / Developer: [t.me/Parv49e](https://t.me/Parv49e)
