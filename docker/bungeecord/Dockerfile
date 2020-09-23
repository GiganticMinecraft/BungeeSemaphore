# use seichiassist-runner to overwrite line endings of shell script using nkf
FROM docker.pkg.github.com/giganticminecraft/dockers/seichiassist-runner:9128bc6651c6

WORKDIR /bungeecord
ADD 'https://ci.md-5.net/job/BungeeCord/lastSuccessfulBuild/artifact/bootstrap/target/BungeeCord.jar' BungeeCord.jar

COPY docker/bungeecord/serverfiles /bungeecord-files/
COPY target/build /bungeecord-files/plugins/

RUN nkf -Lu --overwrite /bungeecord-files/start-server.sh

CMD bash /bungeecord-files/start-server.sh
