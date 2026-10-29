#!/bin/sh
set -eu
value="${KEYCLOAK_PUBLIC_URL:?Set KEYCLOAK_PUBLIC_URL}"
case "$value" in http://*|https://*) ;; *) exit 1 ;; esac
case "$value" in *[!a-zA-Z0-9.:/_-]*) echo 'Invalid Keycloak public URL' >&2; exit 1 ;; esac
printf 'window.__ILLARI_CONFIG__={"keycloakUrl":"%s"};\n' "$value" > /usr/share/nginx/html/assets/runtime-config.js

