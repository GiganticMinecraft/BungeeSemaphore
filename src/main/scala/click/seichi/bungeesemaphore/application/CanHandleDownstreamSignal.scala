package click.seichi.bungeesemaphore.application

import click.seichi.bungeesemaphore.domain.PlayerName
import simulacrum.typeclass

@typeclass trait CanHandleDownstreamSignal[F[_]] {

  def awaitSaveConfirmationOf(playerName: PlayerName): F[Unit]

}
