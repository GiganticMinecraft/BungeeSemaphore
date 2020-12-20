package click.seichi.bungeesemaphore.infrastructure.redis

import akka.actor.{ActorSystem, Props}
import akka.util.ByteString
import cats.Monad
import cats.effect.{ContextShift, Effect, IO, Sync}
import click.seichi.bungeesemaphore.application.configuration.Configuration
import click.seichi.bungeesemaphore.application.{EffectEnvironment, HasGlobalPlayerDataSaveLock}
import click.seichi.bungeesemaphore.domain.PlayerName
import click.seichi.generic.concurrent.synchronization.barrier.IndexedSwitchableBarrier

import scala.concurrent.duration.{Duration, FiniteDuration}

object LocalLockRedisBridge {
  import cats.implicits._

  def bindLocalLockToRedis[
    F[_]: Effect
  ](localLock: IndexedSwitchableBarrier[F, PlayerName])
   (implicit configuration: Configuration,
    actorSystem: ActorSystem,
    effectEnvironment: EffectEnvironment,
    publishingContext: ContextShift[IO]): F[HasGlobalPlayerDataSaveLock[F]] = {

    val pxMillis = configuration.saveLockTimeout match {
      case _: Duration.Infinite => None
      case duration: FiniteDuration => Some(duration.toMillis)
    }

    Sync[F].delay {
      val client = ConfiguredRedisClient()

      // bind the subscriber
      actorSystem.actorOf(
        Props(new ManipulateLockActor[F](localLock))
          .withDispatcher("rediscala.rediscala-client-worker-dispatcher")
      )

      // expose HasGlobalPlayerSemaphore operations to external world
      new HasGlobalPlayerDataSaveLock[F] {
        override def lock(playerName: PlayerName): F[Unit] = {
          Effect[F].liftIO {
            IO.fromFuture {
              IO {
                client.set(SignalFormat.lockKeyOf(playerName), 0, pxMilliseconds = pxMillis)
              }
            }.as(())
          }
        }

        override def awaitLockAvailability(playerName: PlayerName): F[Unit] = {
          for {
            // TODO begin critical section
            currentlyLocked <- Effect[F].liftIO {
              IO.fromFuture {
                IO {
                  client.get[ByteString](SignalFormat.lockKeyOf(playerName))
                }
              }
            }.map(_.nonEmpty)
            _ <-
              if (currentlyLocked)
                localLock(playerName).beginBlock
              else
                Monad[F].unit
            // TODO end critical section
            _ <- localLock(playerName).await
          } yield ()
        }
      }
    }
  }
}
