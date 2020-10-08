package click.seichi.bungeesemaphore.infrastructure.redis

import akka.actor.{ActorSystem, Props}
import cats.effect.{ContextShift, Effect, IO, Sync}
import click.seichi.bungeesemaphore.application.configuration.Configuration
import click.seichi.bungeesemaphore.application.lock.IndexedLocalConditionVariables
import click.seichi.bungeesemaphore.application.{EffectEnvironment, HasGlobalPlayerDataSaveLock}
import click.seichi.bungeesemaphore.domain.PlayerName

object LocalLockRedisBridge {
  import cats.implicits._

  def bindLocalLockToRedis[
    F[_]: Effect
  ](localLock: IndexedLocalConditionVariables[F, PlayerName])
   (implicit configuration: Configuration,
    actorSystem: ActorSystem,
    effectEnvironment: EffectEnvironment,
    publishingContext: ContextShift[IO]): F[HasGlobalPlayerDataSaveLock[F]] = {

    Sync[F].delay {
      val client = ConfiguredRedisClient()

      // bind the subscriber
      actorSystem.actorOf(
        Props(new ManipulateLockActor[F](SignalFormat.signalingChannel, localLock))
          .withDispatcher("rediscala.rediscala-client-worker-dispatcher")
      )

      // expose HasGlobalPlayerSemaphore operations to external world
      new HasGlobalPlayerDataSaveLock[F] {
        override def lock(playerName: PlayerName): F[Unit] = {
          localLock(playerName).beginLock >> Effect[F].liftIO {
            IO.fromFuture {
              IO {
                client.publish(
                  SignalFormat.signalingChannel,
                  SignalFormat.DataLockRequest(playerName).toString
                )
              }
            }.as(())
          }
        }

        override def awaitLockAvailability(playerName: PlayerName): F[Unit] = {
          localLock(playerName).await
        }
      }
    }
  }
}
