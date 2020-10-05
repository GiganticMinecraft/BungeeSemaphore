package click.seichi.bungeesemaphore.application.lock

import cats.effect.{Concurrent, Sync}
import cats.effect.concurrent.Deferred
import click.seichi.bungeesemaphore.domain.PlayerName

import scala.collection.concurrent
import scala.collection.concurrent.TrieMap

/**
 * Class of machine-local locks which uses [[PlayerName]] as the key
 *
 * @tparam F the context in which locking and unlocking is performed
 */
class MapPlayerNameLocalLock[F[_]](private val lockMap: concurrent.Map[PlayerName, Deferred[F, Boolean]])
                                  (implicit private val F: Concurrent[F]) extends PlayerNameLocalLock[F] {

  import cats.implicits._

  override def lock(playerName: PlayerName): F[Unit] = {
    for {
      promise <- Deferred[F, Boolean]
      _ <- F.delay {
        lockMap.putIfAbsent(playerName, promise)
      }
    } yield ()
  }

  override def unlockWithSuccessFlag(playerName: PlayerName, success: Boolean): F[Unit] = {
    for {
      promise <- F.delay {
        lockMap.remove(playerName)
      }
      _ <- promise match {
        case Some(promise) => promise.complete(success)
        case None => F.unit
      }
    } yield ()
  }

  override def awaitLockAvailability(playerName: PlayerName): F[Unit] = {
    for {
      promise <- F.delay {
        lockMap.get(playerName)
      }
      success <- promise match {
        case Some(promise) => promise.get
        case None => F.pure(true)
      }
      _ <- if (success) {
        F.unit
      } else {
        F.raiseError(LockReleasedExceptionally)
      }
    } yield ()
  }

}

object MapPlayerNameLocalLock {
  /**
   * Unsafely allocate state and get an instance of [[PlayerNameLocalLock]].
   */
  def unsafe[F[_]: Concurrent]: PlayerNameLocalLock[F] = new MapPlayerNameLocalLock[F](new TrieMap())

  /**
   * A computation to allocate state and get an instance of [[PlayerNameLocalLock]].
   */
  def apply[F[_]: Concurrent]: F[PlayerNameLocalLock[F]] = Sync[F].delay(unsafe[F])
}
