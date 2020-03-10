FROM ubuntu:18.04

RUN apt-get update && apt-get install -y vim nginx bind9 bind9utils bind9-doc dnsutils curl cron openjdk-8-jre
RUN curl -sL https://sentry.io/get-cli/ | bash

COPY db.ewp /etc/bind/
COPY named.conf /etc/bind/
COPY named.conf.ewp-zones /etc/bind/

EXPOSE 53/udp

COPY nginx.conf /etc/nginx/nginx.conf

EXPOSE 80 443

COPY logback.xml /logback.xml

COPY startup.sh /startup.sh
COPY stop_dns_server_if_the_catalogue_is_not_served.sh /stop_dns_server_if_the_catalogue_is_not_served.sh
COPY update_certificate.sh /update_certificate.sh

COPY application/target/ewp-catalogue-server-*-SNAPSHOT.jar ewp-catalogue-server.jar

ENTRYPOINT ["/startup.sh"]
