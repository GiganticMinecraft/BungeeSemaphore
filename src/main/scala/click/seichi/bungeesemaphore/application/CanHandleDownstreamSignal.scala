package click.seichi.bungeesemaphore.application

import click.seichi.bungeesemaphore.domain.{PlayerName, ServerName}
import simulacrum.typeclass

@typeclass trait CanHandleDownstreamSignal[F[_]] {

  def awaitSaveConfirmationOf(playerName: PlayerName, serverName: ServerName): F[Unit]

}
