#!/bin/sh
set -eu

if [ "${GRAY_PUBLIC_ENTRYPOINT:-false}" != "true" ]; then
  exit 0
fi

: "${PUBLIC_DOMAIN:?PUBLIC_DOMAIN is required for the public Nginx entrypoint}"

certificate="/etc/letsencrypt/live/${PUBLIC_DOMAIN}/fullchain.pem"
template="/etc/nginx/gray-public/public-http.conf.template"
if [ -s "$certificate" ]; then
  template="/etc/nginx/gray-public/public-tls.conf.template"
fi

envsubst '${PUBLIC_DOMAIN}' < "$template" > /etc/nginx/conf.d/default.conf
nginx -t
