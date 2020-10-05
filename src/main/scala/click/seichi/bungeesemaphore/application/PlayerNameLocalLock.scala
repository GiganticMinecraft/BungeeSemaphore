package click.seichi.bungeesemaphore.application

import cats.effect.concurrent.Deferred
import cats.effect.{Concurrent, Sync}
import click.seichi.bungeesemaphore.domain.PlayerName

import scala.collection.concurrent
import scala.collection.concurrent.TrieMap

trait PlayerNameLocalLock[F[_]] {
  /**
   * The computation to perform "lock" on the given [[PlayerName]].
   *
   * Right after this computation, [[awaitLockAvailability]] with the same [[PlayerName]] blocks
   * until [[unlock]] is performed with the same [[PlayerName]].
   */
  def lock(playerName: PlayerName): F[Unit]

  /**
   * The computation to unlock the lock on the given [[PlayerName]].
   *
   * If there is any computation awaiting for the lock on `playerName`,
   * they complete when `success` is `true`, or fail if `success` is false.
   */
  def unlockWithSuccessFlag(playerName: PlayerName, success: Boolean): F[Unit]

  /**
   * The computation to (semantically) block until the lock on the given [[PlayerName]] is released.
   *
   * This action is cancellable.
   */
  def awaitLockAvailability(playerName: PlayerName): F[Unit]

  /**
   * The computation to release a lock, if exists, on the given [[PlayerName]].
   */
  final def unlock(playerName: PlayerName): F[Unit] = {
    unlockWithSuccessFlag(
      playerName, success = true
    )
  }

  /**
   * The computation to release a lock, if exists, on the given [[PlayerName]].
   *
   * Unlike [[unlock]], this action will make awaiting [[awaitLockAvailability]] actions fail with an error.
   */
  final def unlockWithFailure(playerName: PlayerName): F[Unit] = {
    unlockWithSuccessFlag(
      playerName, success = false
    )
  }
}

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
        F.raiseError(PlayerNameLocalLock.LockReleasedExceptionally)
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

object PlayerNameLocalLock {
  case object LockReleasedExceptionally extends Throwable
}
