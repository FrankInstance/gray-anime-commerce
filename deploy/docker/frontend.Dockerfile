FROM nginx:1.27-alpine

COPY deploy/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY deploy/nginx/public-http.conf.template /etc/nginx/gray-public/public-http.conf.template
COPY deploy/nginx/public-tls.conf.template /etc/nginx/gray-public/public-tls.conf.template
COPY deploy/nginx/public-app.inc /etc/nginx/gray-public/public-app.inc
COPY deploy/nginx/40-gray-public-config.sh /docker-entrypoint.d/40-gray-public-config.sh
RUN chmod 0755 /docker-entrypoint.d/40-gray-public-config.sh
COPY frontend/web/dist /usr/share/nginx/html
COPY frontend/admin/dist /usr/share/nginx/html/admin

EXPOSE 80 443
