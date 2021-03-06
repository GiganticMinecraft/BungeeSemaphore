package click.seichi.bungeesemaphore.application

import click.seichi.bungeesemaphore.domain.PlayerName
import simulacrum.typeclass

/**
 * A typeclass for specifying that there is a cluster-synchronized data-save lock available.
 */
@typeclass trait HasGlobalPlayerDataSaveLock[F[_]] {
  def lock(playerName: PlayerName): F[Unit]

  /**
   * Await for an availability of the lock on given `playerName`.
   *
   * This action is cancellable.
   */
  def awaitLockAvailability(playerName: PlayerName): F[Unit]
}