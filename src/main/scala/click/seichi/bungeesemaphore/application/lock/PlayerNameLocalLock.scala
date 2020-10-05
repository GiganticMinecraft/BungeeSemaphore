package click.seichi.bungeesemaphore.application.lock

import click.seichi.bungeesemaphore.domain.PlayerName

trait PlayerNameLocalLock[F[_]] {
  /**
   * The computation to perform "lock" on the given [[PlayerName]].
   *
   * Right after this computation, [[awaitLockAvailability]] with the same [[PlayerName]] blocks
   * until [[unlock]] is performed with the same [[PlayerName]].
   */
  def lock(playerName: PlayerName): F[Unit]

  /**
   * The computation to release the lock, if exists, on the given [[PlayerName]].
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
   *
   * This completes any computation, if exists, of [[awaitLockAvailability]] with the same `playerName`.
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
