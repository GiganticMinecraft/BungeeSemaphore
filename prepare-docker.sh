#!/bin/bash

build_image() {
  set -e

  rm -r target/build || true

  ./sbt assembly

  cp -n docker/spigot/eula.txt docker/spigot/serverfiles/eula.txt || true

  docker-compose build -m 2g
}

stop_docker_service() {
  set -e
  docker-compose down
}

set -e

# we'd like to refer these functions by commands, so we export them
export -f build_image
export -f stop_docker_service

# stop and build
echo "stop_docker_service build_image" | xargs -P 0 -n 1 bash -c

# start necessary containers for debugging
docker-compose up --abort-on-container-exit
