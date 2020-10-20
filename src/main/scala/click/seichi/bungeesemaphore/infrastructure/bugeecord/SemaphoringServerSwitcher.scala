package click.seichi.bungeesemaphore.infrastructure.bugeecord

import java.util.concurrent.ConcurrentHashMap

import cats.effect.{ConcurrentEffect, Sync, Timer}
import click.seichi.bungeesemaphore.application.configuration.Configuration
import click.seichi.bungeesemaphore.application.{EffectEnvironment, HasGlobalPlayerDataSaveLock, HasPlayerConnectionLock}
import click.seichi.bungeesemaphore.domain.{PlayerName, ServerName, Sleeping}
import net.md_5.bungee.UserConnection
import net.md_5.bungee.api.ProxyServer
import net.md_5.bungee.api.config.ServerInfo
import net.md_5.bungee.api.connection.ProxiedPlayer
import net.md_5.bungee.api.event.{PlayerDisconnectEvent, ServerConnectEvent}
import net.md_5.bungee.api.plugin.Listener
import net.md_5.bungee.event.{EventHandler, EventPriority}
import net.md_5.bungee.netty.HandlerBoss

import scala.collection.mutable
import scala.concurrent.duration.{Duration, FiniteDuration}

class SemaphoringServerSwitcher[
  F[_]: ConcurrentEffect: HasGlobalPlayerDataSaveLock: HasPlayerConnectionLock: Timer
](implicit configuration: Configuration, effectEnvironment: EffectEnvironment, proxy: ProxyServer)
  extends Listener {

  private val playersBeingConnectedToNewServer: mutable.Set[PlayerName] = {
    import scala.jdk.CollectionConverters._

    java.util.Collections
      .newSetFromMap(new ConcurrentHashMap[PlayerName, java.lang.Boolean]())
      .asScala
  }

  import cats.implicits._

  private def lockOnDisconnection(player: ProxiedPlayer, server: ServerInfo): F[Unit] = {
    val preMark =
      if (configuration.emitsSaveSignalOnDisconnect(ServerName(server.getName))) {
        HasGlobalPlayerDataSaveLock[F].lock(PlayerName(player.getName))
      } else {
        Sync[F].unit
      }

    val disconnectPlayer = Sync[F].delay {
      player.getServer.disconnect()
    }

    preMark >> disconnectPlayer
  }

  @EventHandler(priority = EventPriority.LOWEST)
  def onPlayerDisconnect(event: PlayerDisconnectEvent): Unit = {
    effectEnvironment.unsafeRunEffectAsync(
      "Lock on disconnection",
      lockOnDisconnection(event.getPlayer, event.getPlayer.getServer.getInfo)
    )
  }

  @EventHandler(priority = EventPriority.LOWEST)
  def onServerConnect(event: ServerConnectEvent): Unit = {
    val player = event.getPlayer
    val targetServer = event.getTarget
    val playerName = PlayerName(player.getName)

    if (!playersBeingConnectedToNewServer.contains(playerName)) {
      val disconnectSourceIfExists = player.getServer match {
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

      val awaitSaveConfirmationIfRequired =
        if (configuration.shouldAwaitForSaveSignal(ServerName(targetServer.getName))) {
          HasGlobalPlayerDataSaveLock[F]
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

      val awaitPlayerDisconnection = HasPlayerConnectionLock[F].awaitDisconnectedState(playerName)

      event.setCancelled(true)

      effectEnvironment.unsafeRunEffectAsync(
        "Execute semaphoric flow on server switching",
        disconnectSourceIfExists >>
          ConcurrentEffect[F].racePair(
            ConcurrentEffect[F].racePair(
              awaitSaveConfirmationIfRequired,
              Sleeping.sleep[F](configuration.joinBlockTimeout)
            ) >> reconnectToTarget,
            awaitPlayerDisconnection
          )
      )
    } else {
      // so that this listener ignores one `ServerConnectEvent` for marked players
      playersBeingConnectedToNewServer.remove(playerName)
    }
  }

}
