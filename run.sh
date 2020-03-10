docker run -dt \
        -p 80:80 \
        -p 443:443 \
        -p 53:53/udp \
        -v $(readlink -f ./application.properties):/application.properties:ro \
        -v $(readlink -f ./certs):/certs \
        -v $(readlink -f ./logs):/logs \
        -v $(readlink -f ./cache):/cache \
        catalogue-server \
        --ip 193.0.109.47 \
        --primary-service-uri "https://ewp-web.usos.edu.pl/" \
        --registry-domain-name "registry.usos.edu.pl" \
        --node-name "ns1" \
        --nameserver-admin-email "hostmaster.usos.edu.pl" \
        --allow-stale-catalogue --dont-update-certificate

