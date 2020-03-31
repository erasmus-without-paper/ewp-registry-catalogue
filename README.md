# Running ewp-registry-catalogue on your machine
1. Create a GitHub account. It will be used to access the catalogue repository and docker images.
2. Contact the EWP Administrator that you want to host a secondary ewp-registry-catalogue server. Send them public IPv4 of the machine that will be used as an ewp-registry-catalogue server.
3. Once your request is approved you will receive:
  - private TLS key, named `registry.erasmuswithoutpaper.eu.key`, keep it safe;
  - node name assigned to your server, use it later as value of `--node-name` parameter.
4. Install `docker` on your server. You can find the installation guide for you OS [here][docker-overview-supported-platforms] (Beware that `docker` package available in Debian/Ubuntu repository is NOT the package you want to install!)
5. Create a GitHub access token. It will be used to download docker images released as packages and the catalogue. It should have `repo` and `read:packages` scopes. The token can be created [here][generate-github-access-token].
6. Login to GitHub docker repository using your username and token generated in step 5:
```
docker login docker.pkg.github.com --username <your username>
<enter your GitLab access token when prompted for password>
```
7. Pull the image.
```
docker pull docker.pkg.github.com/erasmus-without-paper/ewp-registry-catalogue/ewp-registry-catalogue:latest
```
8. You need to prepare 3 directories for running the ewp-registry-catalogue:  
8.1. logs,  
8.2. certificate,  
8.3. cache.  
9. Copy TLS private key you have received in step 2 into `certificate` catalogue. Name it `registry.erasmuswithoutpaper.eu.key`.
10. Create application.properties file with the following content. Replace GitHub username and token placeholders with those you've created in the previous steps.
```properties
spring.profiles.active=production
app.git-hub-catalogue.user-name=erasmus-without-paper
app.git-hub-catalogue.repo-name=ewp-registry-log-prod
app.git-hub-catalogue.file-path=catalogue-v1.xml
app.git-hub-auth.user-name=<github username>
app.git-hub-auth.user-token=<github token>
# Set those entries if you want to use sentry to monitor your application state.
sentry.dsn=
sentry.enabled=false
```
11. Run the following command to run ewp-registry-catalogue in the background, replace names in `<brackets>` with correct values:
```bash
docker run -dt \
        -p 80:80 \
        -p 443:443 \
        -p 53:53/udp \
        -v <absolute path to application.properties file from step 10>:/application.properties:ro \
        -v <absolute path to logs directory from 8.1>:/logs \
        -v <absolute path to certificates directory from 8.2>:/certs \
        -v <absolute path to cache directory from 8.3>:/cache \
        --restart unless-stopped \
        docker.pkg.github.com/erasmus-without-paper/ewp-registry-catalogue/ewp-registry-catalogue \
        --ip <Public IPv4 address of this server> \
        --node-name <node name> \
        --primary-service-uri "https://ewp-web.usos.edu.pl" \
        --registry-domain-name "registry.erasmuswithoutpaper.eu"
```
example of the command with filled placeholders:
```bash
docker run -dt \
        -p 80:80 \
        -p 443:443 \
        -p 53:53/udp \
        -v /var/ewp-registry-catalogue/application.properties:/application.properties:ro \
        -v /var/ewp-registry-catalogue/logs:/logs \
        -v /var/ewp-registry-catalogue/certs:/certs \
        -v /var/ewp-registry-catalogue/cache:/cache \
        --restart unless-stopped \
        docker.pkg.github.com/erasmus-without-paper/ewp-registry-catalogue/ewp-registry-catalogue \
        --ip 131.12.121.85 \
        --node-name "warsaw" \
        --primary-service-uri "https://ewp-web.usos.edu.pl" \
        --registry-domain-name "registry.erasmuswithoutpaper.eu" \
```
12. Verify if the catalogue is running, check Troubleshooting section below in case of any problems.
`docker ps` should list one docker container using image `docker.pkg.github.com/erasmus-without-paper/ewp-registry-catalogue/ewp-registry-catalogue:latest`, it's status should be `Up`. If it is `Restarting`, then check Troubleshooting section.
13. Verify if the catalogue file is hosted on your machine.
```
curl https://localhost/catalogue-v1.xml --insecure  # --insecure is used to disable certificate check, because we are using localhost.
```
It should print long xml file to your console.

14. Verify if DNS server has started.  
`host registry.erasmuswithoutpaper.eu localhost` should return `<Public IPv4 address of this server>`.

15. Verify if you catalogue and DNS server is working from a different machine.  
Repeat steps 13 and 14, but instead of `localhost` use `<Public IPv4 address of this server>`. It is important to use a different machine in a different network, as it will test if your server is available from the Internet.

16. After verifying if your server is available from the Internet inform the EWP Administrator that you are ready.


## Troubleshooting
1. Error message when issuing docker commands:
```
Got permission denied while trying to connect to the Docker daemon socket at unix:///var/run/docker.sock: Get http://%2Fvar%2Frun%2Fdocker.sock/v1.40/containers/json: dial unix /var/run/docker.sock: connect: permission denied
```
indicates that you need to run those commands as the root (use sudo) or add your current user to `docker` group.

2. If running the command in step 11 returns a message similar to the following:
```
docker: Error response from daemon: driver failed programming external connectivity on endpoint magical_jennings (eccf8445c7c6a57fd76bea777b48dad89ed6c3bd3f228992711102f22ec62f1a): Error starting userland proxy: listen udp 0.0.0.0:53: bind: address already in use.
```
you should check what application is using port 53 (or different, if the message shows different one).  
In the case of Debian/Ubuntu OSes port 53 is probably used by systemd-resolved. You can stop this service using `sudo systemctl stop systemd-resolved`

3. `docker ps` lists the container as restarting.  
The container cannot start correctly if you haven't configured it properly.  
Logs from the container should contain an error message describing the cause of the problems.  
You can see them using `docker logs --tail 10 -f $(docker ps -q)` (in case of only one running container), or by using `docker logs -f <Container ID of restarting container>` if you have more than one container on that machine.  
Fix the error and use `docker stop $(docker ps -q)` to stop the container (or, as before, use `<Container ID>` if you have more than one container) and retry command from step 11.

4. `Certificate downloaded from ewp-web.usos.edu.pl:443 is not compatible with local private key!`  
This means that certificate used by `ewp-web.usos.edu.pl` (which is the main registry node) is not compatible with TLS private key you have received. Please check once again if you copied the right file, and if you are sure, then contact the EWP Administrator.


# Running as a primary node
If you are setting up a primary node or a node that will serve as a backup for ewp-registry-service, you should follow this scenario.


## Download and set-up the Registry
1. Pull the latest image from GitHub docker images repository.
```bash
docker login docker.pkg.github.com --username <your username>
<enter your GitLab access token when prompted for password>
docker pull docker.pkg.github.com/erasmus-without-paper/ewp-registry-service/ewp-registry-service:latest
```
2. Create a directory for configuration files of the Registry Service, here we will use `/var/ewp-registry-service` directory, but you can name it as you like.
```bash
mkdir /var/ewp-registry-service
```
3. Generate a new RSA key pair. It will be used to access GitHub repository. You can follow [this guide][github-ssh-key-guide], but generate them into `/var/ewp-registry-service/.ssh` directory.
4. Send PUBLIC key (`/var/ewp-registry-service/.ssh/id_rsa.pub`) generated in step 3 to the EWP Administrator. It will be used to push changes into the catalogue repository.
5. Perform the following command to clone catalogue repository into `/var/ewp-registry-service/repo` directory.
```bash
docker run -it \
    -p 8080:8080 \
    -v /var/ewp-registry-service:/root \
    --entrypoint bash \
    docker.pkg.github.com/erasmus-without-paper/ewp-registry-service/ewp-registry-service \
    /clone_github_repository.sh git@github.com:erasmus-without-paper/ewp-registry-log-prod.git
```
You will be asked to confirm RSA key fingerprint, verify if it matches any of fingerprints available [here][github-ssh-keys-fingerprints] and confirm.

6. Prepare `/var/ewp-registry-service/manifest-sources.xml` file. Latest version of this file will be sent to you by the EWP Administrator.
7. Prepare the `/var/ewp-registry-service/application.properties` file, it should look like this:
```
spring.profiles.active=production
app.admin-emails=<your-admin-mail-address>
spring.mail.host=<IP or hostname of SMPT server that will be used to send email notifications.>
app.instance-name=EWP Registry Service <Your host name>
# Depending on your configuration you might also need to set the following values:
# spring.mail.port=25
# spring.mail.username=
# spring.mail.password=

# If you want to use Sentry for collecting errors, you can set those values:
# sentry.dsn=
# sentry.enabled=true
```
8. Perform a test run, as described in `Registry test run` section, to verify if you have set everything up correctly.

## Registry test run
1. Modify your `/var/ewp-registry-service/application.properties` file to disable pushing to catalogue repository. Add the following line to it:
```
app.repo.enable-pushing=false
```
2. Start the registry.
```bash
docker run -it \
    -p 8080:8080 \
    -v /var/ewp-registry-service:/root \
    docker.pkg.github.com/erasmus-without-paper/ewp-registry-service/ewp-registry-service
```
The Registry Service will be started and its logs will be printed to your terminal. You shouldn't see any errors.

3. Check if the Registry is available locally. Visit `http://localhost:8080` in your browser and verify if the main site of the Registry is visible. You can also use `curl http://localhost:8080` if you don't have a browser available.
4. Stop the Registry, press Ctrl+C in the terminal from step 2.
5. Verify if ssh keys are allowed to push to the repository, perform the following command:
```
docker run -it \
    -v /var/ewp-registry-service:/root \
    --entrypoint bash \
    docker.pkg.github.com/erasmus-without-paper/ewp-registry-service/ewp-registry-service \
    /verify_if_repository_is_writeable.sh
```
It should print `Verified - OK` in the last line of the output.

6. Remove `/var/ewp-registry-service/database.mv.db` file that was created during test run.
7. Remove `app.repo.enable-pushing=false` line from application.properties file, that was added in step 1.

## Running the EWP Registry
Be aware that there should be only one instance of the EWP Registry running in the EWP Network.
You should run and stop your copy only on request of the EWP Administrator.
When you will receive such a request, perform the following steps

1. Pull the latest version of the registry (as in step 2. in set-up):
```bash
docker login docker.pkg.github.com --username <your username>
<enter your GitLab access token when prompted for password>
docker pull docker.pkg.github.com/erasmus-without-paper/ewp-registry-service/ewp-registry-service:latest
```
2. Update the `manifest-sources.xml` file to the latest version. EWP Administrator will send it to you via email.
3. Run the registry.
```bash
docker run -v /var/ewp-registry-service:/root -p 8080:8080 --restart=unless-stopped \
    docker.pkg.github.com/erasmus-without-paper/ewp-registry-service/ewp-registry-service
```
4. Verify if the Registry is available on your machine 8080 port.
5. If the EWP Administrator asks you to, restart your copy of ewp-registry-catalogue as described in `Running ewp-registry-catalogue on a machine with EWP Registry running` section.

## Running ewp-registry-catalogue on a machine with EWP Registry running
First, stop your copy of ewp-registry-catalogue if it is already running:
```bash
docker stop <ewp-registry-catalogue container id>
# Use docker ps command to list running containers and their ids.
```


Perform steps described in `Running ewp-registry-catalogue on your machine.` section, with slightly different `docker run` command:

```bash
docker run -dt \
        -p 80:80 \
        -p 443:443 \
        -p 53:53/udp \
        -v <absolute path to application.properties file from step 11>:/application.properties:ro \
        -v <absolute path to logs directory from 8.1>:/logs \
        -v <absolute path to certificates directory from 8.2>:/certs \
        -v <absolute path to cache directory from 8.3>:/cache \
        --restart unless-stopped \
        docker.pkg.github.com/erasmus-without-paper/ewp-registry-catalogue/ewp-registry-catalogue \
        --ip <Public IPv4 address of this server> \
        --primary-service-uri "http://localhost:8080" \
        --node-name <node name> \
        --registry-domain-name "registry.erasmuswithoutpaper.eu" \
        --allow-stale-catalogue --dont-update-certificate
```
Note that you should use port different than 8080 in `--primary-service-uri` parameter if your registry is available on a different port.  
If you were already running a copy of ewp-registry-catalogue, you should reuse its logs, certificate and cache directories and application.properties file.  
Validate if your ewp-registry-catalogue service have started properly, as described in the aforementioned section.  
Validate if registry website is available by visiting https://registry.erasmuswithoutpaper.eu/ and it's subpages.

If there are more than two backup servers, other backup servers should restart their ewp-registry-catalogue with `--primary-service-uri` parameter set to this server's domain name (They should not use `docker run` command described in this section, commands found in `Running ewp-registry-catalogue on your machine.` should be used.)


## Stopping EWP Registry
When main EWP Registry node is running again, you will be asked to stop your copy of the EWP Registry. Perform the following steps.
1. If you have performed steps in `Running ewp-registry-catalogue on a machine with EWP Registry running` section, then stop your copy and perform steps from `Running ewp-registry-catalogue on your machine.` section once again.
2. Stop the registry:
```bash
docker stop <registry container id>
```

[github]: https://github.com
[generate-github-access-token]: https://github.com/settings/tokens
[github-ssh-key-guide]: https://help.github.com/en/github/authenticating-to-github/generating-a-new-ssh-key-and-adding-it-to-the-ssh-agent
[github-ssh-keys-fingerprints]: https://help.github.com/en/github/authenticating-to-github/githubs-ssh-key-fingerprints
[docker-overview-supported-platforms]: https://docs.docker.com/install/#supported-platforms
