package click.seichi.bungeesemaphore.application

import cats.effect.concurrent.Deferred
import cats.effect.{Concurrent, Sync}
import click.seichi.bungeesemaphore.domain.PlayerName

import scala.collection.concurrent
import scala.collection.concurrent.TrieMap

/**
 * Class of machine-local locks which uses [[PlayerName]] as the key
 *
 * @tparam F the context in which locking and unlocking is performed
 */
class PlayerNameLocalLock[F[_]](private val lockMap: concurrent.Map[PlayerName, Deferred[F, Unit]])
                               (implicit private val F: Concurrent[F]) {

  import cats.implicits._

  /**
   * The computation to perform "lock" on the given [[PlayerName]].
   *
   * Right after this computation, [[awaitLockAvailability]] with the same [[PlayerName]] blocks
   * until [[unlock]] is performed with the same [[PlayerName]].
   */
  def lock(playerName: PlayerName): F[Unit] = {
    for {
      promise <- Deferred[F, Unit]
      _ <- F.delay {
        lockMap.putIfAbsent(playerName, promise)
      }
    } yield ()
  }

  /**
   * The computation to release a lock, if exists, on the given [[PlayerName]].
   */
  def unlock(playerName: PlayerName): F[Unit] = {
    for {
      promise <- F.delay {
        lockMap.remove(playerName)
      }
      _ <- promise match {
        case Some(promise) => promise.complete(())
        case None => F.unit
      }
    } yield ()
  }

  /**
   * The computation to (semantically) block until the lock on the given [[PlayerName]] is released.
   */
  def awaitLockAvailability(playerName: PlayerName): F[Unit] = {
    for {
      promise <- F.delay {
        lockMap.get(playerName)
      }
      _ <- promise match {
        case Some(promise) => promise.get
        case None => F.unit
      }
    } yield ()
  }

}

object PlayerNameLocalLock {
  /**
   * Unsafely allocate state and get an instance of [[PlayerNameLocalLock]].
   */
  def unsafe[F[_]: Concurrent]: PlayerNameLocalLock[F] = new PlayerNameLocalLock[F](new TrieMap())

  /**
   * A computation to allocate state and get an instance of [[PlayerNameLocalLock]].
   */
  def apply[F[_]: Concurrent]: F[PlayerNameLocalLock[F]] = Sync[F].delay(unsafe[F])
}
