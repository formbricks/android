#!/usr/bin/env bash
# A TLS front for the E2E server, for the emulator. The SDK only accepts an https:// appUrl, so the
# emulator talks to https://10.0.2.2:3443 (10.0.2.2 is its alias for the runner's loopback), and
# nginx forwards that to the server e2e/server/up.sh started on 127.0.0.1:3000.
#
#   https-proxy.sh up          creates a throwaway CA and a server certificate for 10.0.2.2 and
#                              127.0.0.1 in $E2E_TLS_DIR, then starts nginx on the host network
#   https-proxy.sh down [dir]  saves the nginx log to dir, then removes the container
#
# install-ca.sh then makes the emulator trust the CA. Nothing here is a secret: the key never
# leaves the runner and the CA is valid for two days.
set -euo pipefail
# shellcheck source-path=SCRIPTDIR source=lib.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

up() {
  rm -rf "${E2E_TLS_DIR}"
  mkdir -p "${E2E_TLS_DIR}"
  cd "${E2E_TLS_DIR}"

  log "Creating a throwaway CA and a certificate for 10.0.2.2"
  openssl req -x509 -newkey rsa:2048 -nodes -days 2 -sha256 \
    -keyout ca.key -out ca.pem -subj "/CN=Formbricks SDK E2E throwaway CA" \
    -addext "basicConstraints=critical,CA:TRUE" \
    -addext "keyUsage=critical,keyCertSign,cRLSign" 2>/dev/null
  openssl req -newkey rsa:2048 -nodes -sha256 \
    -keyout server.key -out server.csr -subj "/CN=10.0.2.2" 2>/dev/null
  cat >server.ext <<'EXT'
basicConstraints=critical,CA:FALSE
keyUsage=critical,digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth
subjectAltName=IP:10.0.2.2,IP:127.0.0.1,DNS:localhost
EXT
  openssl x509 -req -in server.csr -CA ca.pem -CAkey ca.key -CAcreateserial -days 2 -sha256 \
    -extfile server.ext -out server.pem 2>/dev/null
  # nginx serves the chain; the CA key is no longer needed.
  rm -f ca.key server.csr ca.srl
  chmod 644 server.key

  cat >nginx.conf <<NGINX
server {
  listen ${E2E_HTTPS_PORT} ssl;
  ssl_certificate /etc/nginx/tls/server.pem;
  ssl_certificate_key /etc/nginx/tls/server.key;
  client_max_body_size 25m;

  location / {
    proxy_pass http://${E2E_UPSTREAM};
    proxy_http_version 1.1;
    proxy_set_header Host \$http_host;
    proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto https;
    proxy_read_timeout 120s;
  }
}
NGINX

  log "Starting nginx on :${E2E_HTTPS_PORT} -> ${E2E_UPSTREAM}"
  docker rm -f "${E2E_PROXY_CONTAINER}" >/dev/null 2>&1 || true
  docker run --detach --name "${E2E_PROXY_CONTAINER}" --network host \
    --volume "${E2E_TLS_DIR}/nginx.conf:/etc/nginx/conf.d/default.conf:ro" \
    --volume "${E2E_TLS_DIR}:/etc/nginx/tls:ro" \
    "${E2E_PROXY_IMAGE}" >/dev/null

  for _ in $(seq 1 30); do
    if curl -fsS --cacert "${E2E_TLS_DIR}/ca.pem" "https://127.0.0.1:${E2E_HTTPS_PORT}/health/ready" >/dev/null 2>&1; then
      log "TLS proxy ready: https://127.0.0.1:${E2E_HTTPS_PORT} (the emulator uses ${E2E_EMULATOR_URL})"
      return 0
    fi
    sleep 1
  done
  docker logs "${E2E_PROXY_CONTAINER}" >&2 || true
  die "the TLS proxy did not answer /health/ready within 30s"
}

down() {
  if [[ $# -gt 0 ]]; then
    mkdir -p "$1"
    docker logs --timestamps "${E2E_PROXY_CONTAINER}" >"$1/https-proxy.log" 2>&1 || true
  fi
  docker rm -f "${E2E_PROXY_CONTAINER}" >/dev/null 2>&1 || true
}

case "${1:-}" in
  up) up ;;
  down) shift; down "$@" ;;
  *) die "usage: $0 up | down [logdir]" ;;
esac
