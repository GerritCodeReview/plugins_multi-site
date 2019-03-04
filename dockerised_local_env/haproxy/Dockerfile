FROM haproxy:1.5.18

RUN apt-get update && \
    apt-get -y install gettext-base netcat && \
    mkdir /var/lib/haproxy && \
    mkdir /var/run/haproxy && \
    useradd haproxy && \
    chown haproxy: /var/lib/haproxy /var/run/haproxy

COPY haproxy.cfg /usr/local/etc/haproxy/haproxy.cfg.template

COPY docker-entrypoint.sh /
