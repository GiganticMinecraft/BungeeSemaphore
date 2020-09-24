package click.seichi.bungeesemaphore.infrastructure.bugeecord

import cats.effect.{Effect, Sync}
import click.seichi.bungeesemaphore.application.configuration.Configuration
import click.seichi.bungeesemaphore.application.{CanHandleDownstreamSignal, EffectEnvironment}
import click.seichi.bungeesemaphore.domain.{PlayerName, ServerName}
import net.md_5.bungee.UserConnection
import net.md_5.bungee.api.ProxyServer
import net.md_5.bungee.api.event.ServerConnectEvent
import net.md_5.bungee.api.plugin.Listener
import net.md_5.bungee.event.EventHandler
import net.md_5.bungee.netty.HandlerBoss

import scala.collection.mutable

class SemaphoringServerSwitcher[
  F[_]: Effect: CanHandleDownstreamSignal
](implicit configuration: Configuration, effectEnvironment: EffectEnvironment, proxy: ProxyServer)
  extends Listener {

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
    if (!configuration.shouldAwaitForSaveSignal(sourceServerName)) return

    if (!playersBeingConnectedToNewServer.contains(playerName)) {
      import cats.implicits._

      val overwriteBridge =
        Sync[F].delay {
          val userConnection = player.asInstanceOf[UserConnection]
          val serverConnection = userConnection.getServer

          serverConnection.getCh.getHandle.pipeline().get(classOf[HandlerBoss]).setHandler {
            new ConnectionRetainingDownstreamBridge(proxy, userConnection, serverConnection)
          }
        }

      val disconnectPlayer = Sync[F].delay {
        player.getServer.disconnect()
      }

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

      event.setCancelled(true)

      effectEnvironment.unsafeRunEffectAsync(
        "Execute semaphoric flow on server switching",
        overwriteBridge >>
          disconnectPlayer >>
          awaitSaveConfirmation >>
          reconnectToTarget
      )
    } else {
      // so that this listener ignores one `ServerConnectEvent` for marked players
      playersBeingConnectedToNewServer.remove(playerName)
    }
  }

}
