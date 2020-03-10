#!/bin/bash

set -eu

NODE_NAME=
CURRENT_IP=
PRIMARY_SERVICE_URI=
REGISTRY_DOMAIN_NAME=
NAMESERVER_ADMIN_EMAIL=
NO_NGINX=0
DEBUG=0
NGINX_NO_SSL=0
ALLOW_STALE_CATALOGUE=0
DONT_UPDATE_CERTIFICATE=0
NGINX_NO_SSL_PORT=80

echo "Startup check..."

while [[ $# -gt 0 ]]; do
        PARAM_NAME=$1
        shift
        case $PARAM_NAME in
                --ip)
                        CURRENT_IP=$1
                        shift;
                        ;;
                --primary-service-uri)
                        PRIMARY_SERVICE_URI=$1
                        shift
                        ;;
                --registry-domain-name)
                        REGISTRY_DOMAIN_NAME=$1
                        shift;
                        ;;
                --node-name)
                        NODE_NAME=$1
                        shift;
                        ;;
                --nameserver-admin-email)
                        NAMESERVER_ADMIN_EMAIL=$1
                        shift;
                        ;;
                --no-nginx)
                        NO_NGINX=1
                        ;;
                --nginx-no-ssl)
                        NGINX_NO_SSL=1
                        ;;
                --debug)
                        DEBUG=1
                        ;;
                --allow-stale-catalogue)
                        ALLOW_STALE_CATALOGUE=1
                        ;;
                --dont-update-certificate)
                        DONT_UPDATE_CERTIFICATE=1
                        ;;
                *)
                        echo "Unknown parameter $PARAM_NAME" >&2
                        exit 1
        esac
done


if [[ "$NGINX_NO_SSL" -eq 1 ]] && [[ "$DONT_UPDATE_CERTIFICATE" -eq 0 ]]; then
        echo "If you have disabled SSL, then you should also disable certificate update (--dont-update-certificate)."
        exit 1
fi

SENTRY_DSN=$(cat /application.properties | grep "sentry.dsn" | cut -f2 -d"=")
NAMESERVER_DOMAIN_NAME=${NODE_NAME}.${REGISTRY_DOMAIN_NAME}

echo "" > /vars
echo "CURRENT_IP=${CURRENT_IP}" | tee -a /vars
echo "PRIMARY_SERVICE_URI=${PRIMARY_SERVICE_URI}" | tee -a /vars
echo "REGISTRY_DOMAIN_NAME=${REGISTRY_DOMAIN_NAME}" | tee -a /vars
echo "NO_NGINX=${NO_NGINX}" | tee -a /vars
echo "DEBUG=${DEBUG}" | tee -a /vars
echo "NGINX_NO_SSL=${NGINX_NO_SSL}" | tee -a /vars
echo "NGINX_NO_SSL_PORT=${NGINX_NO_SSL_PORT}" | tee -a /vars
echo "ALLOW_STALE_CATALOGUE=${ALLOW_STALE_CATALOGUE}" | tee -a /vars
echo "DONT_UPDATE_CERTIFICATE=${DONT_UPDATE_CERTIFICATE}" | tee -a /vars
echo "NAMESERVER_DOMAIN_NAME=${NAMESERVER_DOMAIN_NAME}" | tee -a /vars
echo "NAMESERVER_ADMIN_EMAIL=${NAMESERVER_ADMIN_EMAIL}" | tee -a /vars
echo "SENTRY_DSN=${SENTRY_DSN}" | tee -a /vars


if [[ "$NGINX_NO_SSL" -eq 1 ]]; then
        echo "SSL is disabled, skipping /certs directory verification."
else
        if [[ ! -d "/certs" ]]; then
                echo "You should mount a catalogue with certificate and private key under /certs."
                exit 1
        fi

        if [[ ! -f "/certs/$REGISTRY_DOMAIN_NAME.key" ]]; then
                echo "Private key should be placed in /certs/$REGISTRY_DOMAIN_NAME.key. That directory should be mounted."
                exit 1
        fi

        if [[ ! -f "/certs/$REGISTRY_DOMAIN_NAME.crt" ]]; then
                if [[ "$DONT_UPDATE_CERTIFICATE" -eq 1 ]]; then
                        echo "Automatic certificate update is disabled, you should place the certificate file in /certs/$REGISTRY_DOMAIN_NAME.crt. That directory should be mounted."
                        exit 1
                else
                        echo "Certificate file not provided, it'll be downloaded."
                fi
        else
                CERT_CN=$(openssl x509 -noout -subject -in "/certs/$REGISTRY_DOMAIN_NAME.crt" | awk -F= '{print $NF}' | tr -d ' ')
                if [[ "$CERT_CN" != "$REGISTRY_DOMAIN_NAME" ]]; then
                        echo "Provided certificate's CN doesn't match REGISTRY_DOMAIN_NAME, got \"${CERT_CN}\", expected \"${REGISTRY_DOMAIN_NAME}\""
                        exit 1
                fi
        fi

        if ! >> /certs/$REGISTRY_DOMAIN_NAME.crt; then
                echo "Certificate file /certs/$REGISTRY_DOMAIN_NAME.crt should be writeable, it will be updated automatically."
                exit 1
        fi
fi

if [[ ! -d "/logs" ]]; then
        echo "You should mount /logs catalogue - we will store logs there."
        exit 1
fi

if [[ ! -f "/application.properties" ]]; then
        echo "application.properties file should be mounted to /."
        exit 1
fi

if [[ ! -d "/cache" ]]; then
        echo "You should mount /cache folder, we will store fetched catalogue file there."
        exit 1
fi

if ! >> /cache/.test-file; then
        echo "Mounted /cache should be writeable."
        exit 1
fi
rm /cache/.test-file

if [[ -f /cache/catalogue-v1.xml ]]; then
        if ! >> /cache/catalogue-v1.xml; then
                echo "Mounted /cache contains catalogue-v1.xml file that is not writeable."
                exit 1
        fi
fi

if [[ -f /cache/catalogue-v1-metadata.xml ]]; then
        if ! >> /cache/catalogue-v1.xml; then
                echo "Mounted /cache contains catalogue-v1-metadata.xml file that is not writeable."
                exit 1
        fi
fi

if [[ ! -x /usr/local/bin/sentry-cli ]]; then
        echo "sentry-cli not found in /usr/local/bin/sentry-cli."
        exit 1
fi


echo "Done."

sed -i "s/REGISTRY_DOMAIN_NAME/${REGISTRY_DOMAIN_NAME}/g" /etc/bind/named.conf.ewp-zones

sed -i "s/CURRENT_IP/${CURRENT_IP}/g" /etc/bind/db.ewp
sed -i "s/NAMESERVER_DOMAIN_NAME/${NAMESERVER_DOMAIN_NAME}/g" /etc/bind/db.ewp
sed -i "s/NAMESERVER_ADMIN_EMAIL/${NAMESERVER_ADMIN_EMAIL}/g" /etc/bind/db.ewp

sed -i "s!PRIMARY_SERVICE_URI!${PRIMARY_SERVICE_URI}!g" /etc/nginx/nginx.conf
sed -i "s!REGISTRY_DOMAIN_NAME!${REGISTRY_DOMAIN_NAME}!g" /etc/nginx/nginx.conf

if [[ "$NGINX_NO_SSL" -eq 1 ]]; then
        sed -i "s/NO_SSL_ONLY/ /g" /etc/nginx/nginx.conf
        sed -i "s/SSL_ONLY/#/g" /etc/nginx/nginx.conf
        sed -i "s/NGINX_NO_SSL_PORT/$NGINX_NO_SSL_PORT/g" /etc/nginx/nginx.conf
else
        sed -i "s/NO_SSL_ONLY/#/g" /etc/nginx/nginx.conf
        sed -i "s/SSL_ONLY/ /g" /etc/nginx/nginx.conf
fi


echo "Starting and stopping dns service to check config..."
/etc/init.d/bind9 start
/etc/init.d/bind9 stop
echo "Done."

echo "Updating TLS Certificate..."
if [[ "$DONT_UPDATE_CERTIFICATE" -eq 1 ]]; then
        echo "Skipping."
else
        ./update_certificate.sh
        echo "Done."
fi


echo "Starting nginx..."
if [[ "$NO_NGINX" -eq 1 ]]; then
        echo "Skipped."
else
        nginx -t
        /etc/init.d/nginx start
        echo "Done."
fi


echo "Configuring cron..."

echo "*/1 * * * * /stop_dns_server_if_the_catalogue_is_not_served.sh >> /logs/cron.log 2>&1" > /tmp/cronfile

if [[ "$DONT_UPDATE_CERTIFICATE" -eq 1 ]]; then
        echo "Skipping update_certificate.sh configuration."
else
        echo "*/30 * * * * /update_certificate.sh >> /logs/cron.log 2>&1" >> /tmp/cronfile
fi
crontab /tmp/cronfile
echo "Done."

echo "Starting cron..."
/etc/init.d/cron start
echo "Done."

exec java -Dlogging.config=/logback.xml -Dsentry.dsn=${SENTRY_DSN} -jar /ewp-catalogue-server.jar
