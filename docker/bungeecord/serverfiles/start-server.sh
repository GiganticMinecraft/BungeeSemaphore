#!/bin/sh

set -e

mkdir -p /bungeecord/plugins/BungeeSemaphore/

rm /bungeecord/plugins/BungeeSemaphore*.jar || true

# overwrite server directory
cp -Rf /bungeecord-files/* /bungeecord/

# extract fresh config and replace
cd /bungeecord/plugins/BungeeSemaphore
rm config.yml || true
jar xf ../BungeeSemaphore*.jar config.yml

sed -i -e "s/  host: .*/  host: \"${REDIS_HOST}\"/" /bungeecord/plugins/BungeeSemaphore/config.yml

cd /bungeecord/ && java -jar /bungeecord/BungeeCord*.jar
