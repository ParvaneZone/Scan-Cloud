package com.example.cfscanner

val S = mapOf(
    "title" to ("Cloudflare IP Scanner" to "اسکنر IP کلودفلر"),
    "dark" to ("Dark" to "تیره"),
    "port" to ("Port" to "پورت"),
    "scan_size" to ("Scan size (IPs tested)" to "حجم اسکن (تعداد IP تست‌شده)"),
    "include_v6" to ("Include IPv6 ranges (needs IPv6 on your network)" to "شامل کردن رنج‌های IPv6 (نیاز به IPv6 روی اینترنت شما)"),
    "extended_cf" to (
        "Extended Cloudflare ranges (+613 ASN ranges, many more IPs to test)" to
            "رنج‌های گسترده کلودفلر (+۶۱۳ رنج ASN، تعداد IP تست‌شده بسیار بیشتر)"
    ),
    "quick" to ("Quick" to "سریع"),
    "normal" to ("Normal" to "معمولی"),
    "deep" to ("Deep" to "عمیق"),
    "start" to ("Start scan" to "شروع اسکن"),
    "stop" to ("Stop" to "توقف"),
    "ready" to ("Ready" to "آماده"),
    "stopped" to ("Stopped" to "متوقف شد"),
    "done" to ("Done (tap an item to copy)" to "تمام شد (برای کپی روی مورد بزنید)"),
    "q_out" to ("What should I do with the results?" to "با نتایج چه کنم؟"),
    "to_file" to ("Deliver as file" to "تحویل در فایل"),
    "show_only" to ("Just show" to "فقط نمایش"),
    "q_cfg" to ("Do you also want to test with your config?" to "با کانفیگ شما هم تست بگیرم؟"),
    "yes" to ("Yes" to "بله"),
    "no" to ("No" to "خیر"),
    "cfg_t" to (
        "Paste your config (vless/vmess/trojan/shadowsocks; " +
            "TLS/Reality; tcp/ws/grpc/xhttp/httpupgrade)" to
            "کانفیگ را بگذارید (vless/vmess/trojan/shadowsocks؛ " +
                "TLS/Reality؛ tcp/ws/grpc/xhttp/httpupgrade)"
    ),
    "cfg_hint" to (
        "The scanned IP replaces only the server address; the original port, SNI, host/path, " +
            "fingerprint, ALPN and other parameters are kept. Reality SNI must be one of the " +
            "server's configured serverNames." to
            "فقط آدرس سرور با IP اسکن‌شده جایگزین می‌شود؛ " +
                "پورت، SNI، host/path، fingerprint، ALPN و سایر " +
                "پارامترهای کانفیگ حفظ می‌شوند. در Reality، SNI باید " +
                "یکی از serverNames تنظیم‌شده روی سرور باشد."
    ),
    "test" to ("Test" to "تست"),
    "cancel" to ("Cancel" to "لغو"),
    "bad" to (
        "Config is not supported or is incomplete." to
            "کانفیگ پشتیبانی نمی‌شود یا ناقص است."
    ),
    "xray_unavailable" to (
        "The bundled Xray binary is not available for this device ABI." to
            "باینری Xray برای معماری این دستگاه در دسترس نیست."
    ),
    "xray_speed" to ("Measure a small Xray download" to "اندازه‌گیری دانلود کوچک با Xray"),
    "saved" to ("File saved" to "فایل ذخیره شد"),
    "none" to ("Nothing found" to "موردی پیدا نشد"),
    "found" to ("Results" to "نتایج"),
    "save_ips" to ("Save IPs file" to "ذخیرهٔ فایل IPها"),
    "save_cfgs" to ("Save all configs file" to "ذخیرهٔ فایل همهٔ کانفیگ‌ها"),
    "tab_cf" to ("IP Scan" to "اسکن IP"),
    "tab_sni" to ("SNI" to "SNI"),
    "tab_about" to ("About" to "درباره ما"),
    "sni_title" to ("SNI / Target Scanner" to "اسکنر SNI / تارگت"),
    "sni_hint" to (
        "OkHttp is used as a fast pre-filter, then Xray TLS ping checks the same resolved IP. " +
            "TLS version, ALPN/h2 and TLS handshake time are shown. For Reality, the SNI must be " +
            "accepted by the server's serverNames." to
            "ابتدا OkHttp به‌عنوان فیلتر سریع اجرا می‌شود و سپس " +
                "Xray TLS ping همان IP resolve‌شده را بررسی می‌کند. " +
                "نسخه TLS، ALPN/h2 و زمان handshake نمایش داده می‌شود. " +
                "در Reality، SNI باید توسط serverNames سرور پذیرفته شود."
    ),
    "extra" to ("Extra domains (optional)" to "دامنه‌های اضافه (اختیاری)"),
    "dns" to ("DNS" to "DNS"),
    "dns_system" to ("System DNS" to "DNS سیستم"),
    "dns_cf" to ("Cloudflare DoH" to "DoH کلودفلر"),
    "dns_google" to ("Google DoH" to "DoH گوگل"),
    "join_t" to ("Join our Telegram channel" to "به کانال تلگرام ما بپیوندید"),
    "join_msg" to (
        "Get updates and new versions of the app. Joining is optional." to
            "از آپدیت‌ها و نسخه‌های جدید برنامه باخبر شوید. " +
                "عضویت اختیاری است."
    ),
    "join" to ("Join channel" to "عضویت در کانال"),
    "later" to ("Skip for now" to "فعلاً نه"),
    "about_t" to ("About us" to "درباره ما"),
    "about_text" to (
        "Parvane Scanner is a free tool that finds Cloudflare/Fastly IPs and SNI / target domains " +
            "using your own connection. Xray-core is bundled for real end-to-end proxy checks.\n\n" +
            "• Free and open source.\n• Tests run on your phone.\n• Xray-core is licensed under " +
            "MPL-2.0; attribution is included in CHANGES.md and README.md.\n• Results depend on your " +
            "network and are not guaranteed.\n• Use responsibly and according to local law.\n\n" +
            "For suggestions or problems, message the developer with the button below." to
            "اسکنر پروانه یک ابزار رایگان برای پیدا کردن " +
                "IPهای کلودفلر/Fastly و SNI / تارگت با اینترنت " +
                "خودِ شماست. Xray-core برای تست واقعی و end-to-end " +
                "داخل برنامه قرار گرفته است.\n\n" +
                "• رایگان و متن‌باز است.\n• همهٔ تست‌ها روی گوشی شما " +
                "انجام می‌شود.\n• Xray-core با مجوز MPL-2.0 منتشر شده و " +
                "attribution آن در README و CHANGES آمده است.\n• " +
                "نتیجه‌ها به شبکه شما بستگی دارد و تضمینی نیست.\n• " +
                "مسئولانه و طبق قوانین محل استفاده کنید.\n\n" +
                "برای پیشنهاد یا گزارش مشکل، با دکمهٔ زیر به سازنده " +
                "پیام بدهید."
    ),
    "tab_ipt" to ("IP Test" to "تست آیپی"),
    "ipt_title" to ("IP Test" to "تست آیپی"),
    "ipt_hint" to (
        "Enter an IP or domain. Details, ports and HTTP are tested from your phone; the location " +
            "table is measured from servers in different countries." to
            "یک آیپی یا دامنه وارد کنید. مشخصات، پورت‌ها و HTTP از روی گوشی شما تست می‌شود و " +
                "جدول کشورها از سرورهایی در کشورهای مختلف اندازه‌گیری می‌شود."
    ),
    "ipt_my" to ("Your IP" to "آیپی شما"),
    "ipt_recheck" to ("Check again" to "بررسی دوباره"),
    "ipt_my_err" to (
        "Could not detect your IP. Check your internet." to
            "آیپی شما شناسایی نشد. اینترنت خود را چک کنید."
    ),
    "ipt_vpn_note" to (
        "If a VPN or proxy is on, this shows its IP." to
            "اگر VPN یا پروکسی روشن باشد، آیپی آن نمایش داده می‌شود."
    ),
    "ipt_use_mine" to ("Test my IP" to "تست آیپی من"),
    "ipt_field" to ("IP or domain" to "آیپی یا دامنه"),
    "ipt_run" to ("Run test" to "شروع تست"),
    "ipt_bad" to ("Invalid IP or domain." to "آیپی یا دامنه نامعتبر است."),
    "ipt_resolved" to ("Resolved to" to "تبدیل‌شده به"),
    "ipt_info" to ("Information" to "مشخصات"),
    "ipt_info_err" to (
        "Could not fetch details (needs internet)." to
            "دریافت مشخصات انجام نشد (نیاز به اینترنت)."
    ),
    "ipt_ping" to ("Ping" to "پینگ"),
    "ipt_ports" to ("TCP ports" to "پورت‌های TCP"),
    "ipt_http" to ("HTTP check" to "بررسی HTTP"),
    "ipt_ip" to ("IP" to "آیپی"),
    "ipt_type" to ("Type" to "نوع"),
    "ipt_country" to ("Country" to "کشور"),
    "ipt_region" to ("Region" to "استان / منطقه"),
    "ipt_city" to ("City" to "شهر"),
    "ipt_isp" to ("ISP" to "ارائه‌دهنده"),
    "ipt_org" to ("Organization" to "سازمان"),
    "ipt_asn" to ("ASN" to "ASN"),
    "ipt_tz" to ("Time zone" to "منطقهٔ زمانی"),
    "ipt_rdns" to ("Reverse DNS" to "Reverse DNS"),
    "ipt_icmp" to ("ICMP ping" to "پینگ ICMP"),
    "ipt_tcpping" to ("TCP ping" to "پینگ TCP"),
    "ipt_noicmp" to (
        "ICMP is unavailable on this device" to "ICMP روی این دستگاه در دسترس نیست"
    ),
    "ipt_sent" to ("Sent / received" to "ارسال / دریافت"),
    "ipt_loss" to ("Packet loss" to "از دست رفتن بسته"),
    "ipt_latency" to ("Min / avg / max" to "کمینه / میانگین / بیشینه"),
    "ipt_open" to ("Open" to "باز"),
    "ipt_closed" to ("Closed / filtered" to "بسته / فیلتر"),
    "ipt_open_count" to ("Open ports" to "پورت‌های باز"),
    "ipt_status" to ("Status" to "وضعیت"),
    "ipt_server" to ("Server" to "سرور"),
    "ipt_redirect" to ("Redirect" to "ریدایرکت"),
    "ipt_proto" to ("Protocol" to "پروتکل"),
    "ipt_time" to ("Response time" to "زمان پاسخ"),
    "ipt_fail" to ("No response" to "بدون پاسخ"),
    "ipt_cert_note" to (
        "The HTTPS certificate is not verified in this check." to
            "در این بررسی، گواهی HTTPS اعتبارسنجی نمی‌شود."
    ),
    "ipt_na" to ("Not available" to "در دسترس نیست"),
    "ipt_no_v4" to ("No IPv4 address found" to "آیپی IPv4 پیدا نشد"),
    "ipt_no_v6" to ("No IPv6 address found" to "آیپی IPv6 پیدا نشد"),
    "ipt_global" to ("Test from locations" to "تست از کشورهای مختلف"),
    "ipt_local_ping" to ("Ping from your phone" to "پینگ از گوشی شما"),
    "ipt_g_tcp" to ("TCP port" to "پورت TCP"),
    "ipt_g_ok" to ("Responded" to "پاسخ داده"),
    "ipt_g_fail" to ("Failed" to "ناموفق"),
    "ipt_g_err" to (
        "Location test is unavailable right now." to
            "تست از کشورهای مختلف الان در دسترس نیست."
    ),
    "check_update" to ("Check for updates" to "بررسی آپدیت"),
    "checking" to ("Checking" to "در حال بررسی"),
    "upd_none" to ("You have the latest version." to "شما آخرین نسخه را دارید."),
    "upd_new" to ("New version available:" to "نسخهٔ جدید موجود است:"),
    "upd_err" to (
        "Could not check for updates. Check your internet." to
            "بررسی آپدیت انجام نشد. اینترنت خود را چک کنید."
    ),
    "upd_noapk" to (
        "A new version exists but it has no APK file yet." to
            "نسخهٔ جدید هست ولی هنوز فایل APK ندارد."
    ),
    "update" to ("Update" to "آپدیت"),
    "downloading" to (
        "Downloading; the installer opens when it finishes." to
            "در حال دانلود؛ بعد از پایان، نصب‌کننده باز می‌شود."
    ),
    "allow_install" to (
        "Allow this app to install updates in the settings that just opened, then press Update " +
            "again." to
            "در تنظیماتی که باز شد اجازهٔ نصب آپدیت را بدهید و دوباره " +
                "آپدیت را بزنید."
    ),
    "ok" to ("OK" to "باشه"),
    "provider" to ("Network" to "شبکه"),
    "channel" to ("Telegram channel" to "کانال تلگرام"),
    "contact" to ("Message the developer on Telegram" to "پیام به سازنده در تلگرام")
)

fun tr(key: String, fa: Boolean): String {
    val pair = S[key] ?: error("Missing string: $key")
    return if (fa) pair.second else pair.first
}
