package click.seichi.bungeesemaphore.application

import click.seichi.bungeesemaphore.domain.PlayerName
import jdk.internal.net.http.common.Utils.ServerName
import simulacrum.typeclass

@typeclass trait DownstreamSignalHandler[F[_]] {

  def awaitSaveConfirmationOf(playerName: PlayerName, serverName: ServerName): F[Unit]

}
