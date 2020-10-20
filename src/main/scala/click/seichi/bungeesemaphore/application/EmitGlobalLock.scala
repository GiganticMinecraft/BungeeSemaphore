package click.seichi.bungeesemaphore.application

import cats.effect.Sync
import click.seichi.bungeesemaphore.application.configuration.Configuration
import click.seichi.bungeesemaphore.domain.{PlayerName, ServerName}

object EmitGlobalLock {
  def of[F[_]: HasGlobalPlayerDataSaveLock: Sync](playerName: PlayerName, disconnectionSource: ServerName)
                                                 (implicit configuration: Configuration): F[Unit] = {
    if (configuration.emitsSaveSignalOnDisconnect(disconnectionSource)) {
      HasGlobalPlayerDataSaveLock[F].lock(playerName)
    } else {
      Sync[F].unit
    }
  }
}