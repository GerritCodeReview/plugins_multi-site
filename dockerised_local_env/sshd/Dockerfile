FROM sickp/alpine-sshd:6.8

RUN apk update && apk upgrade && \
    apk add --no-cache bash git netcat-openbsd curl

COPY sshd_config /etc/ssh/sshd_config
