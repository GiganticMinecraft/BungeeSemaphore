package click.seichi.bungeesemaphore.application

import click.seichi.bungeesemaphore.domain.PlayerName
import simulacrum.typeclass

/**
 * A typeclass for specifying that there is a machine-local lock synchronized to player's connection
 */
trait HasPlayerConnectionLock[F[_]] {
  /**
   * An action to semantically block until the player with given `playerName` disconnects from the proxy server.
   *
   * This action is cancellable.
   *
   * This action is cancelled when the data save has been reported failed.
   */
  def awaitDisconnectedState(playerName: PlayerName): F[Unit]
}

object HasPlayerConnectionLock {
  def apply[F[_]](implicit ev: HasPlayerConnectionLock[F]): HasPlayerConnectionLock[F] = ev
}
