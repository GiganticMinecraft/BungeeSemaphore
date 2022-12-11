package click.seichi.bungeesemaphore.application

import cats.effect.Sync
import cats.implicits._
import click.seichi.bungeesemaphore.application.configuration.Configuration
import click.seichi.bungeesemaphore.domain.{PlayerName, ServerName}

import java.util.logging.Logger

object EmitGlobalLock {
  def of[F[_]: HasGlobalPlayerDataSaveLock: Sync](playerName: PlayerName, disconnectionSource: ServerName)
                                                 (implicit configuration: Configuration, logger: Logger): F[Unit] = {
    if (configuration.emitsSaveSignalOnDisconnect(disconnectionSource)) {
      HasGlobalPlayerDataSaveLock[F].lock(playerName) >> Sync[F].delay {
        logger.info(s"Globally locked $playerName's data-save lock")
      }
    } else {
      Sync[F].unit
    }
  }
}