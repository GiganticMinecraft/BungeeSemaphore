package click.seichi.bungeesemaphore.application

import click.seichi.bungeesemaphore.domain.PlayerName
import simulacrum.typeclass

@typeclass trait HasGlobalPlayerSemaphore[F[_]] {

  def lock(playerName: PlayerName): F[Unit]

  def awaitLockAvailability(playerName: PlayerName): F[Unit]

}
