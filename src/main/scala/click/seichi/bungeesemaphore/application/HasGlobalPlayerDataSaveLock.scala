package click.seichi.bungeesemaphore.application

import click.seichi.bungeesemaphore.domain.PlayerName

/**
 * A typeclass for specifying that there is a cluster-synchronized data-save lock available.
 */
trait HasGlobalPlayerDataSaveLock[F[_]] {
  def lock(playerName: PlayerName): F[Unit]

  /**
   * Await for an availability of the lock on given `playerName`.
   *
   * This action is cancellable when next case.
   *  - When it is found that data save has failed.
   */
  def awaitLockAvailability(playerName: PlayerName): F[Unit]
}

object HasGlobalPlayerDataSaveLock {
  def apply[F[_]](implicit ev: HasGlobalPlayerDataSaveLock[F]): HasGlobalPlayerDataSaveLock[F] = ev
}
