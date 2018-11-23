FROM java:latest

MAINTAINER William Hill Online - Platform Enginnering Team <martin.hatas@williamhill.co.uk>
LABEL description="Fenix"

WORKDIR /opt/fenix
ADD opt /opt
RUN ["chown", "-R", "daemon:daemon", "."]
USER daemon
ENTRYPOINT ["bin/fenix"]

CMD []
