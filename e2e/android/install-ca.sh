#!/usr/bin/env bash
# Makes the running emulator trust the throwaway CA from https-proxy.sh, for the SDK's own HTTP
# client and for the survey WebView alike. Both use the system store (the SDK's network security
# config trusts system CAs only, and WebView ignores per-app trust anchors), so the CA goes there.
#
# /system is read-only, so the store is copied onto a tmpfs mounted over it, plus the CA. That
# needs a rootable image (google_apis, not google_play) on API 33 or lower: from API 34 the store
# lives in the Conscrypt APEX. The mount lasts until the emulator stops.
set -euo pipefail
# shellcheck source-path=SCRIPTDIR source=lib.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

ca="${E2E_TLS_DIR}/ca.pem"
[[ -f "${ca}" ]] || die "no CA at ${ca}; run https-proxy.sh up first"
# Android names system CA files after OpenSSL's old subject hash.
name="$(openssl x509 -subject_hash_old -noout -in "${ca}").0"
store=/system/etc/security/cacerts

adb wait-for-device
booted() { [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; }
for _ in $(seq 1 150); do booted && break; sleep 2; done
booted || die "the emulator did not finish booting"
log "Restarting adbd as root"
# A non-rootable image fails here; the id check below reports it with the reason.
adb root >/dev/null || true
adb wait-for-device
for _ in $(seq 1 30); do
  [[ "$(adb shell id -u 2>/dev/null | tr -d '\r')" == "0" ]] && break
  sleep 1
done
[[ "$(adb shell id -u | tr -d '\r')" == "0" ]] || die "adbd is not root; the emulator needs a google_apis image"

log "Adding ${name} to the emulator's system CA store"
adb push "${ca}" "/data/local/tmp/${name}" >/dev/null
adb shell "set -e
  if ! grep -q ' ${store} tmpfs ' /proc/mounts; then
    rm -rf /data/local/tmp/cacerts && mkdir -m 700 /data/local/tmp/cacerts
    cp ${store}/* /data/local/tmp/cacerts/
    mount -t tmpfs tmpfs ${store}
    cp /data/local/tmp/cacerts/* ${store}/
  fi
  cp /data/local/tmp/${name} ${store}/${name}
  chown root:root ${store} ${store}/*
  chmod 755 ${store}
  chmod 644 ${store}/*
  chcon u:object_r:system_file:s0 ${store} ${store}/*"
adb shell "test -f ${store}/${name}" || die "the CA is not in ${store}"
log "The emulator trusts the E2E CA"
