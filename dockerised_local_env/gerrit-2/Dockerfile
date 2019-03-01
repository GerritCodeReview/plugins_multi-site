FROM openjdk:8-jdk-alpine

RUN adduser -D -h /var/gerrit gerrit && \
    apk update && apk upgrade && \
    apk add --no-cache bash git openssh netcat-openbsd curl ttf-dejavu && \
    apk add --no-cache git-daemon

COPY --chown=gerrit:gerrit bin/release.war /var/gerrit/bin/gerrit.war
COPY --chown=gerrit:gerrit plugins/* /var/gerrit/plugins/
COPY --chown=gerrit:gerrit etc /var/gerrit/etc

ADD bin/git-daemon.sh /usr/local/bin/git-daemon.sh
RUN chmod +x /usr/local/bin/git-daemon.sh

USER gerrit

WORKDIR /var/gerrit

COPY docker-entrypoint.sh /bin

ENTRYPOINT /bin/docker-entrypoint.sh
