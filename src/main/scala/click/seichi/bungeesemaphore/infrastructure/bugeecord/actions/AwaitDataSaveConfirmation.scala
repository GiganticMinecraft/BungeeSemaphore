package click.seichi.bungeesemaphore.infrastructure.bugeecord.actions

import cats.effect.Sync
import click.seichi.bungeesemaphore.application.HasGlobalPlayerDataSaveLock
import click.seichi.bungeesemaphore.application.configuration.Configuration
import click.seichi.bungeesemaphore.domain.{PlayerName, ServerName}
import net.md_5.bungee.api.config.ServerInfo
import net.md_5.bungee.api.connection.ProxiedPlayer

object AwaitDataSaveConfirmation {

  import cats.implicits._

  def of[F[_] : Sync : HasGlobalPlayerDataSaveLock](player: ProxiedPlayer, targetServer: ServerInfo)
                                                   (implicit configuration: Configuration): F[Unit] = {
    if (configuration.shouldAwaitForSaveSignal(ServerName(targetServer.getName))) {
      HasGlobalPlayerDataSaveLock[F]
        .awaitLockAvailability(PlayerName(player.getName))
        .orElse {
          Sync[F].delay {
            player.disconnect(configuration.errorMessages.downstreamCouldNotSaveData)
          }
        }
    } else {
      Sync[F].unit
    }
  }

}
