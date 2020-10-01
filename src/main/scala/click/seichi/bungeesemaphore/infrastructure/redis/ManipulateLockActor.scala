package click.seichi.bungeesemaphore.infrastructure.redis

import java.net.InetSocketAddress

import cats.effect.Effect
import click.seichi.bungeesemaphore.application.{EffectEnvironment, PlayerNameLocalLock}
import redis.actors.RedisSubscriberActor
import redis.api.pubsub.{Message, PMessage}

class ManipulateLockActor[
  F[_]: Effect
](address: InetSocketAddress, channel: String)
 (implicit localLock: PlayerNameLocalLock[F], effectEnvironment: EffectEnvironment)
  extends RedisSubscriberActor(
    address = address,
    channels = Seq(channel),
    patterns = Nil,
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
