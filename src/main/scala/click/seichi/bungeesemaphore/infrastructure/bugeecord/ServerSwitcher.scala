package click.seichi.bungeesemaphore.infrastructure.bugeecord

import cats.effect.{Effect, Sync}
import click.seichi.bungeesemaphore.application.configuration.Configuration
import click.seichi.bungeesemaphore.application.{CanHandleDownstreamSignal, EffectEnvironment}
import click.seichi.bungeesemaphore.domain.{PlayerName, ServerName}
import net.md_5.bungee.api.event.ServerConnectEvent
import net.md_5.bungee.api.plugin.Listener
import net.md_5.bungee.event.EventHandler

import scala.collection.mutable

class ServerSwitcher[
  F[_]: Effect: CanHandleDownstreamSignal
](implicit configuration: Configuration,
  effectEnvironment: EffectEnvironment) extends Listener {

  private val playersBeingConnectedToNewServer: mutable.Set[PlayerName] = mutable.HashSet()

  @EventHandler
  def onServerConnect(event: ServerConnectEvent): Unit = {
    val player = event.getPlayer
    val connectedServer =
      player.getServer match {
        case null => return
        case server => server.getInfo
      }
    val targetServer = event.getTarget

    val playerName = PlayerName(player.getName)
    val sourceServerName = ServerName(connectedServer.getName)

    if (connectedServer.getAddress == targetServer.getAddress) return
    if (configuration.shouldAwaitForSaveSignal(sourceServerName)) return

    if (!playersBeingConnectedToNewServer.contains(playerName)) {
      import cats.implicits._

      val awaitSaveConfirmation =
        CanHandleDownstreamSignal[F]
          .awaitSaveConfirmationOf(playerName, sourceServerName)
          .onError { _ =>
            Sync[F].delay {
              player.disconnect(configuration.errorMessages.downstreamCouldNotSaveData)
            }
          }

      val reconnectToTarget = Sync[F].delay {
        // prevent this listener from reacting again
        playersBeingConnectedToNewServer.add(playerName)

        player.connect(targetServer)
      }

      val program = for {
        _ <- awaitSaveConfirmation
        _ <- reconnectToTarget
      } yield ()

      event.setCancelled(true)

      effectEnvironment.unsafeRunEffectAsync(
        "Execute semaphoric flow on server switching",
        program
      )
    } else {
      // so that this listener ignores one `ServerConnectEvent` for marked players
      playersBeingConnectedToNewServer.remove(playerName)
    }
  }

}
