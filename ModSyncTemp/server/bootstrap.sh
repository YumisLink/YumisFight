set -euo pipefail
umask 027
root=/srv/yumis-mods
password_b64='__PASSWORD_BASE64__'
if [ -e "$root" ] && { [ ! -f "$root/.yumis-owner" ] || [ "$(cat "$root/.yumis-owner")" != 'YumisModSync/v1' ]; }; then
    echo 'Refusing to reuse an unowned deployment directory' >&2
    exit 1
fi
install -d -m 0751 -o root -g www "$root"
install -d -m 0750 -o root -g www "$root/objects" "$root/releases" "$root/secure"
printf '%s\n' 'YumisModSync/v1' > "$root/.yumis-owner"
install -d -m 0700 -o ubuntu -g ubuntu "$root/incoming"
password=$(printf '%s' "$password_b64" | base64 -d)
auth="$root/secure/download.htpasswd"
if [ -f "$auth" ]; then
    previous=$(cut -d: -f2- "$auth")
    salt=$(printf '%s' "$previous" | cut -d'$' -f3)
    actual=$(printf '%s' "$password" | openssl passwd -6 -salt "$salt" -stdin)
    if [ "$actual" != "$previous" ]; then
        echo 'Existing download credential differs; recover the original local profile before rebuilding' >&2
        exit 1
    fi
else
    hash=$(printf '%s' "$password" | openssl passwd -6 -stdin)
    printf 'mods:%s\n' "$hash" > "$auth"
fi
unset password password_b64 hash actual previous
chown root:www "$auth"
chmod 0640 "$auth"
if [ ! -f "$root/secure/tls.key" ]; then
    openssl req -x509 -newkey rsa:3072 -noenc -days 3650 \
        -keyout "$root/secure/tls.key" -out "$root/secure/tls.crt" \
        -subj '/CN=119.91.113.235' -addext 'subjectAltName=IP:119.91.113.235' 2>/dev/null
fi
chmod 0600 "$root/secure/tls.key"
chmod 0644 "$root/secure/tls.crt"
config=/www/server/panel/vhost/nginx/yumis-mods.conf
if [ -f "$config" ] && ! head -n 1 "$config" | grep -q '^# YumisModSync/v1$'; then
    echo 'Refusing to overwrite an unrelated nginx vhost' >&2
    exit 1
fi
cat > "$root/secure/yumis-mods.conf.next" <<'NGINX'
# YumisModSync/v1
server {
    listen 443 ssl;
    server_name 119.91.113.235;
    ssl_certificate /srv/yumis-mods/secure/tls.crt;
    ssl_certificate_key /srv/yumis-mods/secure/tls.key;
    ssl_protocols TLSv1.2 TLSv1.3;
    auth_basic "Yumis Mods";
    auth_basic_user_file /srv/yumis-mods/secure/download.htpasswd;
    access_log off;
    location = /yumis-mods/current.json {
        alias /srv/yumis-mods/current.json;
        default_type application/json;
        add_header Cache-Control "no-store" always;
        limit_except GET { deny all; }
    }
    location ^~ /yumis-mods/objects/ {
        alias /srv/yumis-mods/objects/;
        default_type application/java-archive;
        autoindex off;
        add_header Cache-Control "private, max-age=31536000, immutable";
        limit_except GET { deny all; }
    }
    location / { return 404; }
}
NGINX
had_config=0
if [ -f "$config" ]; then
    cp "$config" "$root/secure/yumis-mods.conf.previous"
    had_config=1
fi
cp "$root/secure/yumis-mods.conf.next" "$config"
if ! /www/server/nginx/sbin/nginx -t -c /www/server/nginx/conf/nginx.conf >&2; then
    if [ "$had_config" = 1 ]; then cp "$root/secure/yumis-mods.conf.previous" "$config"; else rm -f "$config"; fi
    echo 'nginx configuration check failed; prior vhost restored without reloading' >&2
    exit 1
fi
/www/server/nginx/sbin/nginx -c /www/server/nginx/conf/nginx.conf -s reload
openssl x509 -in "$root/secure/tls.crt" -pubkey -noout | openssl pkey -pubin -outform DER 2>/dev/null | sha256sum | cut -d' ' -f1
