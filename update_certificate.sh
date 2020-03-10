#!/bin/bash

set -u

export SENTRY_DSN=$(cat /application.properties | grep "sentry.dsn" | cut -f2 -d"=")
function sentry_log() {
        if [[ -n "SENTRY_DSN" ]]; then
                /usr/local/bin/sentry-cli send-event -m "$*"
        fi
}

function log_error() {
        echo "$*"
        sentry_log "$*"
}

PRIMARY_SERVICE_URI=$(cat /vars | grep PRIMARY_SERVICE_URI | cut -f2 -d"=")
REGISTRY_DOMAIN_NAME=$(cat /vars | grep REGISTRY_DOMAIN_NAME | cut -f2 -d"=")
OLD_CERT_PATH=/certs/$REGISTRY_DOMAIN_NAME.crt
NEW_CERT_PATH=/tmp/cert.pem
PRIVATE_KEY_PATH=/certs/$REGISTRY_DOMAIN_NAME.key


# Remove https:// prefix
PRIMARY_SERVICE_URI=${PRIMARY_SERVICE_URI#https://}

# Add port if not present
if ! [[ "$PRIMARY_SERVICE_URI" =~ ^.*:[[:digit:]]+$ ]]; then
        PRIMARY_SERVICE_URI=${PRIMARY_SERVICE_URI}:443
fi

echo "Downloading new certificate from $PRIMARY_SERVICE_URI."
openssl s_client -showcerts -servername "$REGISTRY_DOMAIN_NAME" -connect "$PRIMARY_SERVICE_URI"  </dev/null 2>/dev/null | sed -ne '/-BEGIN CERTIFICATE-/,/-END CERTIFICATE-/p' >$NEW_CERT_PATH

if diff -q $NEW_CERT_PATH $OLD_CERT_PATH; then
        echo "The certificate is unchanged."
        exit 0
fi

echo "The certificate has changed."

echo "Checking if CN matches REGISTRY_DOMAIN_NAME..."
NEW_CERT_CN=$(openssl x509 -noout -subject -in "$NEW_CERT_PATH" | awk -F= '{print $NF}' | tr -d ' ')

if [[ "$NEW_CERT_CN" != "$REGISTRY_DOMAIN_NAME" ]]; then
        log_error "Certificate CN doesn't match REGISTRY_DOMAIN_NAME, found \"${NEW_CERT_CN}\", expected \"${REGISTRY_DOMAIN_NAME}\""
        exit 1
fi
echo "Done."


# Comparing public keys.
if diff -q <(openssl x509 -in $NEW_CERT_PATH -pubkey -noout) <(openssl rsa -in $PRIVATE_KEY_PATH -pubout); then
        echo "Public keys match, changing certificate."
        cp $NEW_CERT_PATH $OLD_CERT_PATH
        if /etc/init.d/nginx status; then
                echo "Reloading nginx configuration."
                /etc/init.d/nginx reload
                echo "Done"
        fi
        echo "Done"
        exit 0
else
        log_error "Certificate downloaded from $PRIMARY_SERVICE_URI is not compatible with local private key!"
        exit 1
fi
