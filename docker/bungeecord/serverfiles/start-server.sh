#!/bin/sh

set -e

mkdir -p /bungeecord/plugins/BungeeSemaphore/

rm /bungeecord/plugins/BungeeSemaphore*.jar || true

# overwrite server directory
cp -Rf /bungeecord-files/* /bungeecord/

# extract fresh config and replace
# cd /bungeecord/plugins/BungeeSemaphore
# rm config.yml
# jar xf ../BungeeSemaphore-*.jar config.yml

cd /bungeecord/ && java -jar /bungeecord/BungeeCord*.jar
