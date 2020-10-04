package click.seichi.bungeesemaphore.infrastructure.redis

import cats.effect.Effect
import click.seichi.bungeesemaphore.application.configuration.Configuration
import click.seichi.bungeesemaphore.application.{EffectEnvironment, PlayerNameLocalLock}
import redis.actors.RedisSubscriberActor
import redis.api.pubsub.{Message, PMessage}

class ManipulateLockActor[
  F[_]: Effect
](channel: String, localLock: PlayerNameLocalLock[F])
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
        case DataLockRequest(playerName) => localLock.lock(playerName)
        case ReleaseDataLock(playerName) => localLock.unlock(playerName)
        case DataSaveFailed(playerName) => localLock.unlockWithFailure(playerName)
      }
      case None =>
        Effect[F].unit
    }

    effectEnvironment.unsafeRunEffectAsync("Reacting to incoming redis message", effect)
  }

  override def onPMessage(pm: PMessage): Unit = {}
}
