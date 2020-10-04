package click.seichi.bungeesemaphore.application

import click.seichi.bungeesemaphore.domain.PlayerName
import simulacrum.typeclass

/**
 * A typeclass for specifying that there is a machine-local lock synchronized to player's connection
 */
@typeclass trait HasPlayerConnectionLock[F[_]] {
  def awaitDisconnectedState(playerName: PlayerName): F[Unit]
}
