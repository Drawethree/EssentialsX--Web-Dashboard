# Reverse Proxy & HTTPS

The dashboard's embedded web server speaks **plain HTTP** on `port` (default `8095`). For any
deployment reachable from the internet you should run it **behind a reverse proxy that terminates
TLS** (HTTPS) and forwards to the dashboard on `127.0.0.1:8095`. This page has copy-paste configs for
**nginx**, **Caddy** and **Apache**, plus the two things people most often get wrong: **Server-Sent
Events** (the live console/chat stream) and **keeping session tokens out of access logs**.

> **Why a proxy?** TLS/HTTPS, a clean domain, HTTP→HTTPS redirects, and the ability to put the panel
> on port 443 without exposing the raw plugin port. HSTS is sent by the dashboard automatically but
> only takes effect once you're actually on HTTPS.

---

## Before you start

1. **Point a domain** (e.g. `panel.example.com`) at your server's public IP.
2. **Firewall the raw port.** Only the proxy needs to reach `8095`. Block `8095` from the public
   internet (or bind your firewall so only `127.0.0.1`/the proxy can hit it) and expose only 80/443.
3. **Lock CORS** to your panel's exact origin in [[Configuration|Configuration]]:
   ```yaml
   cors:
     allowed-origins: "https://panel.example.com"
   ```
4. Have a TLS certificate ready. **Caddy** gets one automatically; for **nginx**/**Apache** use
   [Certbot](https://certbot.eff.org/) / Let's Encrypt.

---

## Two things that matter for this app

### 1. Server-Sent Events (live console & chat)

The live console and chat stream use **SSE** over `GET /api/events/stream`. Proxies that **buffer**
responses will make the stream appear "connected" but show **no output**. You must disable response
buffering and allow long-lived connections on that path (the examples below do this for the whole
site, which is simplest and safe).

### 2. Keep the `?token=` out of your logs

The browser `EventSource` API can't send `Authorization` headers, so the SSE stream authenticates with
a **`?token=<session>`** query parameter. If your proxy logs full request lines (including the query
string), those session tokens land in your access logs. Configure your proxy to log the **path only**
(examples below).

---

## nginx

```nginx
# Log the path only — NOT $request/$query_string — so ?token= session tokens don't hit disk.
log_format noquery '$remote_addr - $remote_user [$time_local] '
                   '"$request_method $uri $server_protocol" $status $body_bytes_sent '
                   '"$http_referer" "$http_user_agent"';

server {
    listen 80;
    server_name panel.example.com;
    return 301 https://$host$request_uri;      # force HTTPS
}

server {
    listen 443 ssl;
    http2 on;
    server_name panel.example.com;

    ssl_certificate     /etc/letsencrypt/live/panel.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/panel.example.com/privkey.pem;

    access_log /var/log/nginx/panel.access.log noquery;

    location / {
        proxy_pass http://127.0.0.1:8095;
        proxy_http_version 1.1;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # --- Server-Sent Events (live console/chat) ---
        proxy_buffering off;        # stream chunks straight through
        proxy_cache off;
        gzip off;                   # don't buffer/compress the event stream
        proxy_read_timeout 3600s;   # allow long-lived connections
    }
}
```

Reload: `nginx -t && systemctl reload nginx`.

---

## Caddy

Caddy provisions and renews HTTPS for you. `flush_interval -1` disables response buffering so SSE
streams immediately.

```caddyfile
panel.example.com {
    encode gzip

    reverse_proxy 127.0.0.1:8095 {
        flush_interval -1
    }

    # Optional: strip the ?token= from logged URIs.
    log {
        output file /var/log/caddy/panel.log
    }
}
```

That's the whole config — restart with `caddy reload` (or `systemctl reload caddy`).

---

## Apache (httpd)

Enable `mod_proxy`, `mod_proxy_http` and `mod_ssl`, then:

```apache
<VirtualHost *:80>
    ServerName panel.example.com
    Redirect permanent / https://panel.example.com/
</VirtualHost>

<VirtualHost *:443>
    ServerName panel.example.com

    SSLEngine on
    SSLCertificateFile    /etc/letsencrypt/live/panel.example.com/fullchain.pem
    SSLCertificateKeyFile /etc/letsencrypt/live/panel.example.com/privkey.pem

    ProxyPreserveHost On
    RequestHeader set X-Forwarded-Proto "https"

    # flushpackets=on keeps the SSE stream flowing (no buffering).
    ProxyPass        / http://127.0.0.1:8095/ flushpackets=on
    ProxyPassReverse / http://127.0.0.1:8095/

    # Log the path without the query string so ?token= isn't recorded.
    LogFormat "%h %l %u %t \"%m %U %H\" %>s %b" noquery
    CustomLog /var/log/apache2/panel.access.log noquery
</VirtualHost>
```

---

## After setting it up

- Browse to `https://panel.example.com` — the padlock should be valid and the login page branded.
- Open the **Live Console** and confirm output streams in. If it stays empty, buffering is still on —
  re-check the SSE settings above and see
  [[Troubleshooting & FAQ|Troubleshooting-and-FAQ]] → *Live console connects but shows no output*.
- Enable [[2FA|First-Login-and-Security]] and change the default admin password if you haven't.

## IP allowlisting behind a proxy

`security.allowed-ips` in [[Configuration|Configuration]] filters on the **connecting** IP — behind a
proxy that's the proxy itself, so the allowlist won't see real client IPs. To restrict who can reach
the panel, allowlist at the **proxy** (e.g. nginx `allow`/`deny`, Caddy `@blocked`/`remote_ip`, or a
firewall) instead.

## Related

- [[Configuration|Configuration]] — `port`, `cors.allowed-origins`, `security.allowed-ips`.
- [[First Login & Security|First-Login-and-Security]] — passwords, 2FA, sessions, security headers.
- [[Troubleshooting & FAQ|Troubleshooting-and-FAQ]] — SSE/CORS problems.
