package click.seichi.bungeesemaphore.infrastructure.redis

import cats.effect.Effect
import click.seichi.bungeesemaphore.application.EffectEnvironment
import click.seichi.bungeesemaphore.application.configuration.Configuration
import click.seichi.bungeesemaphore.application.lock.IndexedLocalConditionVariables
import click.seichi.bungeesemaphore.domain.PlayerName
import redis.actors.RedisSubscriberActor
import redis.api.pubsub.{Message, PMessage}

class ManipulateLockActor[
  F[_]: Effect
](channel: String, localLock: IndexedLocalConditionVariables[F, PlayerName])
 (implicit effectEnvironment: EffectEnvironment,
  configuration: Configuration)
  extends RedisSubscriberActor(
    address = configuration.redis.address,
    channels = Seq(channel),
    patterns = Nil,
    authPassword = configuration.redis.password,
    onConnectStatus = _ => ()
  ) {

  override def onMessage(m: Message): Unit = {
    import SignalFormat._

    val effect = parseMessage(m) match {
      case Some(value) => value match {
        case DataLockRequest(playerName) => localLock(playerName).beginLock
        case ReleaseDataLock(playerName) => localLock(playerName).unlock
        case DataSaveFailed(playerName) => localLock(playerName).unlockWithFailure
      }
      case None =>
        Effect[F].unit
    }

    effectEnvironment.unsafeRunEffectAsync("Reacting to incoming redis message", effect)
  }

  override def onPMessage(pm: PMessage): Unit = {}
}
