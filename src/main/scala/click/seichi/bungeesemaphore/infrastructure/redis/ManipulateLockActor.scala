package click.seichi.bungeesemaphore.infrastructure.redis

import cats.effect.Effect
import click.seichi.bungeesemaphore.application.EffectEnvironment
import click.seichi.bungeesemaphore.application.configuration.Configuration
import click.seichi.bungeesemaphore.domain.PlayerName
import click.seichi.generic.concurrent.synchronization.barrier.IndexedSwitchableBarrier
import redis.actors.RedisSubscriberActor
import redis.api.pubsub.{Message, PMessage}

class ManipulateLockActor[
  F[_]: Effect
](localLock: IndexedSwitchableBarrier[F, PlayerName])
 (implicit effectEnvironment: EffectEnvironment,
  configuration: Configuration)
  extends RedisSubscriberActor(
    address = configuration.redis.address,
    channels = Nil,
    patterns = Seq(SignalFormat.keyEventChannelPattern),
    authPassword = configuration.redis.password,
    onConnectStatus = _ => ()
  ) {

  override def onMessage(m: Message): Unit = {}

  override def onPMessage(pm: PMessage): Unit = {
    import SignalFormat._

    val effect = parsePMessage(pm) match {
      case Some(value) => value match {
        case DataLockRequest(playerName) => localLock(playerName).beginBlock
        case ReleaseDataLock(playerName) => localLock(playerName).unblock
        case DataSaveFailed(playerName) => localLock(playerName).unblockWithFailure
      }
      case None =>
        Effect[F].unit
    }

    effectEnvironment.unsafeRunEffectAsync("Reacting to incoming redis message", effect)
  }
}
