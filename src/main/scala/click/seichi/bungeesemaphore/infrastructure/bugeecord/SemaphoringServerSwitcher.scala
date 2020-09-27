package click.seichi.bungeesemaphore.infrastructure.bugeecord

import cats.effect.{Effect, Sync}
import click.seichi.bungeesemaphore.application.configuration.Configuration
import click.seichi.bungeesemaphore.application.{EffectEnvironment, HasGlobalPlayerSemaphore}
import click.seichi.bungeesemaphore.domain.{PlayerName, ServerName}
import net.md_5.bungee.UserConnection
import net.md_5.bungee.api.ProxyServer
import net.md_5.bungee.api.config.ServerInfo
import net.md_5.bungee.api.connection.ProxiedPlayer
import net.md_5.bungee.api.event.{PlayerDisconnectEvent, ServerConnectEvent}
import net.md_5.bungee.api.plugin.Listener
import net.md_5.bungee.event.EventHandler
import net.md_5.bungee.netty.HandlerBoss

import scala.collection.mutable

class SemaphoringServerSwitcher[
  F[_]: Effect: HasGlobalPlayerSemaphore
](implicit configuration: Configuration, effectEnvironment: EffectEnvironment, proxy: ProxyServer)
  extends Listener {

  private val playersBeingConnectedToNewServer: mutable.Set[PlayerName] = mutable.HashSet()

  import cats.implicits._

  private def lockOnDisconnection(player: ProxiedPlayer, server: ServerInfo): F[Unit] = {
    val preMark =
      if (configuration.emitsSaveSignalOnDisconnect(ServerName(server.getName))) {
        HasGlobalPlayerSemaphore[F].lock(PlayerName(player.getName))
      } else {
        Sync[F].unit
      }

    val disconnectPlayer = Sync[F].delay {
      player.getServer.disconnect()
    }

    preMark >> disconnectPlayer
  }

  @EventHandler
  def onPlayerDisconnect(event: PlayerDisconnectEvent): Unit = {
    effectEnvironment.unsafeRunEffectAsync(
      "Lock on disconnection",
      lockOnDisconnection(event.getPlayer, event.getPlayer.getServer.getInfo)
    )
  }

  @EventHandler
  def onServerConnect(event: ServerConnectEvent): Unit = {
    val player = event.getPlayer
    val targetServer = event.getTarget
    val playerName = PlayerName(player.getName)

    val disconnectSource = player.getServer match {
      case null => Sync[F].unit
      case server =>
        val overwriteDownstreamBridge =
          Sync[F].delay {
            val userConnection = player.asInstanceOf[UserConnection]
            val serverConnection = userConnection.getServer

            serverConnection.getCh.getHandle.pipeline().get(classOf[HandlerBoss]).setHandler {
              new ConnectionRetainingDownstreamBridge(proxy, userConnection, serverConnection)
            }
          }

        overwriteDownstreamBridge >> lockOnDisconnection(player, server.getInfo)
    }

    if (!playersBeingConnectedToNewServer.contains(playerName)) {
      val awaitSaveConfirmation =
        if (configuration.shouldAwaitForSaveSignal(ServerName(targetServer.getName))) {
          HasGlobalPlayerSemaphore[F]
            .awaitLockAvailability(playerName)
            .onError { _ =>
              Sync[F].delay {
                player.disconnect(configuration.errorMessages.downstreamCouldNotSaveData)
              }
            }
        } else {
          Sync[F].unit
        }

      val reconnectToTarget = Sync[F].delay {
        // prevent this listener from reacting again
        playersBeingConnectedToNewServer.add(playerName)

        player.connect(targetServer)
      }

      event.setCancelled(true)

      effectEnvironment.unsafeRunEffectAsync(
        "Execute semaphoric flow on server switching",
        disconnectSource >> awaitSaveConfirmation >> reconnectToTarget
      )
    } else {
      // so that this listener ignores one `ServerConnectEvent` for marked players
      playersBeingConnectedToNewServer.remove(playerName)
    }
  }

}
