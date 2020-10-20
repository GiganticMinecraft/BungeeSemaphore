package click.seichi.bungeesemaphore.infrastructure.bugeecord

import java.util.concurrent.ConcurrentHashMap

import cats.effect.{ConcurrentEffect, Sync, Timer}
import click.seichi.bungeesemaphore.application.configuration.Configuration
import click.seichi.bungeesemaphore.application.{EffectEnvironment, EmitGlobalLock, HasGlobalPlayerDataSaveLock, HasPlayerConnectionLock}
import click.seichi.bungeesemaphore.domain.{PlayerName, ServerName, Sleeping}
import net.md_5.bungee.api.ProxyServer
import net.md_5.bungee.api.connection.ProxiedPlayer
import net.md_5.bungee.api.event.{PlayerDisconnectEvent, ServerConnectEvent}
import net.md_5.bungee.api.plugin.Listener
import net.md_5.bungee.event.{EventHandler, EventPriority}

import scala.collection.mutable

class SemaphoringServerSwitcher[
  F[_]: ConcurrentEffect: HasGlobalPlayerDataSaveLock: HasPlayerConnectionLock: Timer
](implicit configuration: Configuration, effectEnvironment: EffectEnvironment, proxy: ProxyServer)
  extends Listener {

  import cats.implicits._

  private def disconnectServerConnection(player: ProxiedPlayer): F[Unit] = Sync[F].delay {
    player.getServer.disconnect()
  }

  @EventHandler(priority = EventPriority.LOWEST)
  def onPlayerDisconnect(event: PlayerDisconnectEvent): Unit = {
    val player = event.getPlayer
    val playerName = PlayerName(player.getName)
    val serverName = ServerName(player.getServer.getInfo.getName)

    effectEnvironment.unsafeRunEffectAsync(
      "Lock on disconnection",
      EmitGlobalLock.of[F](playerName, serverName) >>
        disconnectServerConnection(player)
    )
  }

  // A concurrent Set for controls over ServerConnectEvent
  private val playersBeingConnectedToNewServer: mutable.Set[PlayerName] = {
    import scala.jdk.CollectionConverters._

    java.util.Collections
      .newSetFromMap(new ConcurrentHashMap[PlayerName, java.lang.Boolean]())
      .asScala
  }

  @EventHandler(priority = EventPriority.LOWEST)
  def onServerConnect(event: ServerConnectEvent): Unit = {
    val player = event.getPlayer
    val targetServer = event.getTarget
    val playerName = PlayerName(player.getName)

    if (!playersBeingConnectedToNewServer.contains(playerName)) {
      val disconnectSourceIfExists = player.getServer match {
        case null => Sync[F].unit
        case originalServer =>
          ConnectionModifications.letConnectionLinger[F](player) >>
            EmitGlobalLock.of[F](playerName, ServerName(originalServer.getInfo.getName)) >>
            disconnectServerConnection(player)
      }

      val reconnectToTarget = Sync[F].delay {
        // prevent this listener from reacting again
        playersBeingConnectedToNewServer.add(playerName)

        player.connect(targetServer)
      }

      event.setCancelled(true)

      effectEnvironment.unsafeRunEffectAsync(
        "Execute semaphoric flow on server switching",
        disconnectSourceIfExists >>
          ConcurrentEffect[F].race(
            ConcurrentEffect[F].race(
              AwaitDataSaveConfirmation.of[F](player, targetServer),
              Sleeping.sleep[F](configuration.joinBlockTimeout)
            ) >> reconnectToTarget,
            HasPlayerConnectionLock[F].awaitDisconnectedState(playerName)
          )
      )
    } else {
      // so that this listener ignores one `ServerConnectEvent` for marked players
      playersBeingConnectedToNewServer.remove(playerName)
    }
  }

}
