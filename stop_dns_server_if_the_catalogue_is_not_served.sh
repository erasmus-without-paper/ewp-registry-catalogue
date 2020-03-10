#!/bin/bash

set -eu

echo ""
echo "[$(date -Is)] DNS watchdog started..."

REGISTRY_DOMAIN_NAME=$(cat /vars | grep REGISTRY_DOMAIN_NAME | cut -f2 -d"=")
DEBUG=$(cat /vars | grep DEBUG | cut -f2 -d"=")
ALLOW_STALE_CATALOGUE=$(cat /vars | grep ALLOW_STALE_CATALOGUE | cut -f2 -d"=")
NGINX_NO_SSL=$(cat /vars | grep -w NGINX_NO_SSL | cut -f2 -d"=")
NGINX_NO_SSL_PORT=$(cat /vars | grep NGINX_NO_SSL_PORT | cut -f2 -d"=")
export SENTRY_DSN=$(cat /vars | grep SENTRY_DNS | cut -f2 -d"=")

if [[ "$NGINX_NO_SSL" -eq 1 ]]; then
        LOCALHOST_PORT="$NGINX_NO_SSL_PORT"
        USE_HTTP=1
else
        LOCALHOST_PORT="443"
        USE_HTTP=0
fi


CURL_SILENT=1

MAX_TIME_WITHOUT_FETCH_SECONDS=120

LOCAL_CATALOGUE_CACHE_DIR="/cache/"
LOCAL_CATALOGUE_FILE_PATH="${LOCAL_CATALOGUE_CACHE_DIR}/catalogue-v1.xml"
LOCAL_CATALOGUE_METADATA_PATH="${LOCAL_CATALOGUE_CACHE_DIR}/catalogue-v1-metadata.xml"
CATALOGUE_ENDPOINT="/catalogue-v1.xml"
TEMP_CATALOGUE_FILE="/tmp/test_catalogue.xml"

if ! /etc/init.d/bind9 status; then
        IS_BIND9_STOPPED=1
else
        IS_BIND9_STOPPED=0
fi

function sentry_log() {
        if [[ -n "$SENTRY_DSN" ]]; then
                /usr/local/bin/sentry-cli send-event -m "$*"
        fi
}

function log_error() {
        echo "$@"
        sentry_log "$@"
}

function stop_dns_and_exit() {
        log_error "Stopping DNS server: $1"
        if [[ "$IS_BIND9_STOPPED" -eq 1 ]]; then
                echo "Already stopped."
        else
                /etc/init.d/bind9 stop
        fi
        exit 1
}

function check_catalogue_file_availability() {
        IP=$1
        PORT=$2

        rm -f "$TEMP_CATALOGUE_FILE"


        CURL_FLAGS="-H 'Host $REGISTRY_DOMAIN_NAME' --max-time 5 --connect-timeout 5"
        if [[ 1 -eq "$CURL_SILENT" ]]; then
                CURL_FLAGS="${CURL_FLAGS} -s"
        fi

        # Plain HTTP: just send a request to specified $IP:$PORT.
        if [[ 1 -eq "$USE_HTTP" ]]; then
            echo "Using http connection"
            URL="http://${IP}:${PORT}${CATALOGUE_ENDPOINT}"
        # HTTPS: we want to verify if certificate served by the server matched $REGISTRY_DOMAIN_NAME.
        # We will send a request to $REGISTRY_DOMAIN_NAME:443, but we will tell curl to resolve it's IP address as $IP.
        # curl will send the request to localhost, but it'll expect $REGISTRY_DOMAIN_NAME certificate.
        else
             CURL_FLAGS="${CURL_FLAGS} --resolve ${REGISTRY_DOMAIN_NAME}:${PORT}:${IP} --cacert /certs/${REGISTRY_DOMAIN_NAME}.crt"
             URL="https://${REGISTRY_DOMAIN_NAME}:${PORT}${CATALOGUE_ENDPOINT}"
        fi

        COMMAND="curl $CURL_FLAGS $URL"

        echo "executing: $COMMAND"

        if ! $COMMAND > "$TEMP_CATALOGUE_FILE"; then
                rm -f "$TEMP_CATALOGUE_FILE"
                stop_dns_and_exit "cURL to $URL failed."
        fi

        if ! diff "$LOCAL_CATALOGUE_FILE_PATH" "$TEMP_CATALOGUE_FILE" > /dev/null; then
                rm -f "$TEMP_CATALOGUE_FILE"
                stop_dns_and_exit "Catalogue file hosted by this host and a catalogue copy stored in ${LOCAL_CATALOGUE_FILE_PATH} differ."
        fi
        rm -f "$TEMP_CATALOGUE_FILE"
}

function date_to_timestamp() {
        date -d "$@" +%s
}

function is_local_catalogue_copy_stale() {
        FETCH_DATE=$(grep -oPm1 "(?<=<last-fetch-date>)[^<]+" ${LOCAL_CATALOGUE_METADATA_PATH})
        CURRENT_DATE=$(date -Is -u)
        FETCH_TIMESTAMP=$(date_to_timestamp ${FETCH_DATE})
        CURRENT_TIMESTAMP=$(date_to_timestamp ${CURRENT_DATE})
        TIME_DIFF_SECONDS=$((${CURRENT_TIMESTAMP} - ${FETCH_TIMESTAMP}))
        [[ "${TIME_DIFF_SECONDS}" -ge "${MAX_TIME_WITHOUT_FETCH_SECONDS}" ]]
}

echo "Checking if catalogue file is downloaded..."
if [[ ! -f "$LOCAL_CATALOGUE_FILE_PATH" ]]; then
        stop_dns_and_exit "Local Catalogue file not found. Checked path '${LOCAL_CATALOGUE_FILE_PATH}'."
fi
echo "OK"


echo "Checking if it is served locally..."
check_catalogue_file_availability 127.0.0.1 ${LOCALHOST_PORT}
echo "OK"


echo "Checking if the local catalogue file was fetched recently..."
if is_local_catalogue_copy_stale; then
        if [[ "$ALLOW_STALE_CATALOGUE" -eq 1 ]]; then
                log_error "The local catalogue file is stale, however this server is allowed to serve stale content."
        else
                stop_dns_and_exit "The local catalogue file is stale. This might indicate problems with connecting to GitHub API, rate limit might have been exceeded."
        fi
fi
echo "OK"


if [[ $IS_BIND9_STOPPED -eq 1 ]]; then
        echo "Starting dns server..."
        /etc/init.d/bind9 start
        echo "Done."
else
        echo "DNS server is already running."
fi
echo "Done."
