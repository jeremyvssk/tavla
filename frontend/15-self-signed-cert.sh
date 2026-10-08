#!/bin/sh
# Creates the self-signed TLS certificate nginx serves HTTPS with, on the container's first start.
#
# Made here rather than in the image or the repo, so the private key exists only in the tls_data
# volume of this machine. Valid for localhost and 127.0.0.1; other devices on the LAN get a name
# mismatch warning, which they can accept the same way.
set -eu
dir=/etc/nginx/tls
if [ ! -s "$dir/cert.pem" ] || [ ! -s "$dir/key.pem" ]; then
  mkdir -p "$dir"
  openssl req -x509 -newkey rsa:2048 -nodes -days 825 -subj "/CN=localhost" \
    -addext "subjectAltName=DNS:localhost,IP:127.0.0.1" \
    -keyout "$dir/key.pem" -out "$dir/cert.pem" 2>/dev/null
  chmod 600 "$dir/key.pem"
  echo "15-self-signed-cert.sh: created a self-signed certificate for localhost"
fi
