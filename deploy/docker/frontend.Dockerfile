FROM nginx:1.27-alpine

COPY deploy/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY frontend/web/dist /usr/share/nginx/html
COPY frontend/admin/dist /usr/share/nginx/html/admin

EXPOSE 80
