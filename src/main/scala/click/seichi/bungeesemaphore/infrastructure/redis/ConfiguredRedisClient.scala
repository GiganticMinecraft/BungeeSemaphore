package click.seichi.bungeesemaphore.infrastructure.redis

import org.apache.pekko.actor.ActorSystem
import click.seichi.bungeesemaphore.application.configuration.Configuration
import redis.RedisClient

object ConfiguredRedisClient {

  def apply()(implicit configuration: Configuration, actorSystem: ActorSystem): RedisClient = {
    RedisClient(
      host = configuration.redis.host,
      port = configuration.redis.port,
      password = configuration.redis.password
    )
  }

}
