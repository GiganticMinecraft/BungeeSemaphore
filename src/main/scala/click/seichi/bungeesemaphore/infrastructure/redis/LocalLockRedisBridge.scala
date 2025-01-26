package click.seichi.bungeesemaphore.infrastructure.redis

import org.apache.pekko.actor.{ActorSystem, Props}
import org.apache.pekko.util.ByteString
import cats.Monad
import cats.effect.concurrent.Deferred
import cats.effect.{Concurrent, ConcurrentEffect, ContextShift, Effect, IO, Sync}
import click.seichi.bungeesemaphore.application.configuration.Configuration
import click.seichi.bungeesemaphore.application.{EffectEnvironment, HasGlobalPlayerDataSaveLock}
import click.seichi.bungeesemaphore.domain.PlayerName
import click.seichi.generic.concurrent.synchronization.barrier.IndexedSwitchableBarrier
import redis.RedisClient

import java.util.logging.Logger
import scala.concurrent.duration.{Duration, FiniteDuration}

object LocalLockRedisBridge {
  import cats.implicits._

  private def lockRedis[F[_]: Effect](playerName: PlayerName, pxMillis: Option[Long])
                                     (implicit client: RedisClient,
                                      publishingContext: ContextShift[IO]): F[Unit] =
    Effect[F].liftIO {
      IO.fromFuture {
        IO {
          client.set(SignalFormat.lockKeyOf(playerName), 0, pxMilliseconds = pxMillis)
        }
      }.as(())
    }

  private def isAlreadyUnlockedOnRedis[F[_]: Effect](playerName: PlayerName)
                                             (implicit client: RedisClient,
                                              redisAccessContext: ContextShift[IO]): F[Boolean] =
    Effect[F].liftIO {
      IO.fromFuture {
        IO {
          client.get[ByteString](SignalFormat.lockKeyOf(playerName))
        }
      }
    }.map(_.isEmpty)

  def bindLocalLockToRedis[
    F[_]: ConcurrentEffect
  ](localLock: IndexedSwitchableBarrier[F, PlayerName])
   (implicit configuration: Configuration,
    actorSystem: ActorSystem,
    effectEnvironment: EffectEnvironment,
    publishingContext: ContextShift[IO],
    logger: Logger): F[HasGlobalPlayerDataSaveLock[F]] = {

    val pxMillis = configuration.saveLockTimeout match {
      case _: Duration.Infinite => None
      case duration: FiniteDuration => Some(duration.toMillis)
    }

    Sync[F].delay {
      implicit val client: RedisClient = ConfiguredRedisClient()

      // bind the subscriber
      actorSystem.actorOf(
        Props(new ManipulateLockActor[F](localLock))
          .withDispatcher("rediscala.rediscala-client-worker-dispatcher")
      )

      // expose HasGlobalPlayerSemaphore operations to external world
      new HasGlobalPlayerDataSaveLock[F] {
        override def lock(playerName: PlayerName): F[Unit] = lockRedis(playerName, pxMillis)

        override def awaitLockAvailability(playerName: PlayerName): F[Unit] = {
          for {
            // always begin by blocking the application-local lock,
            // since
            //  - if the lock is not present then the request-local promise
            //    that is allocated below will be completed soon
            //  - if the lock is present at this point, then the application-local lock
            //    would be unblocked when the lock on Redis expires
            _ <- localLock(playerName).beginBlock

            // allocate an empty promise that is local to this await request
            requestLocalPromise <- Deferred[F, Unit]

            // concurrently query the Redis, immediately completing
            // the request-local promise when the lock is not present
            _ <- Concurrent[F].start {
              Monad[F].ifM(isAlreadyUnlockedOnRedis(playerName))(
                requestLocalPromise.complete(()),
                Monad[F].unit
              )
            }

            _ <- Concurrent[F].race(
              localLock(playerName).await,
              requestLocalPromise.get
            )

            _ <- Sync[F].delay {
              logger.info(s"${playerName.value}'s save-lock has been successfully cleared")
            }
          } yield ()
        }
      }
    }
  }
}
