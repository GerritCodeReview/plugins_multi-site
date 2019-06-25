FROM haproxy:1.9.4

RUN apt-get update && \
    apt-get -y install gettext-base netcat && \
    rm -rf /var/lib/apt/lists/* && \
    mkdir /var/lib/haproxy && \
    mkdir /var/run/haproxy && \
    useradd haproxy && \
    chown haproxy: /var/lib/haproxy /var/run/haproxy

COPY haproxy.cfg /usr/local/etc/haproxy/haproxy.cfg.template

COPY docker-entrypoint.sh /
