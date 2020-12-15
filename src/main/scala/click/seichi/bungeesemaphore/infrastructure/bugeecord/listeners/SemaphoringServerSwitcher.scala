package click.seichi.bungeesemaphore.infrastructure.bugeecord.listeners

import cats.effect.{ConcurrentEffect, Sync, Timer}
import click.seichi.bungeesemaphore.application._
import click.seichi.bungeesemaphore.application.configuration.Configuration
import click.seichi.bungeesemaphore.domain.{PlayerName, ServerName}
import click.seichi.bungeesemaphore.infrastructure.bugeecord.actions.{AwaitDataSaveConfirmation, ConnectionModifications}
import net.md_5.bungee.api.ProxyServer
import net.md_5.bungee.api.event.{PlayerDisconnectEvent, ServerConnectEvent}
import net.md_5.bungee.api.plugin.Listener
import net.md_5.bungee.event.{EventHandler, EventPriority}

import java.util.concurrent.ConcurrentHashMap
import scala.collection.mutable

class SemaphoringServerSwitcher[
  F[_]: ConcurrentEffect: HasGlobalPlayerDataSaveLock: HasPlayerConnectionLock: Timer
](implicit configuration: Configuration, effectEnvironment: EffectEnvironment, proxy: ProxyServer)
  extends Listener {

  import cats.implicits._

   @EventHandler(priority = EventPriority.LOWEST)
  def onPlayerDisconnect(event: PlayerDisconnectEvent): Unit = {
    val player = event.getPlayer
    val playerName = PlayerName(player.getName)
    val serverName = ServerName(player.getServer.getInfo.getName)

    effectEnvironment.unsafeRunEffectAsync(
      "Lock on disconnection",
      EmitGlobalLock.of[F](playerName, serverName) >>
        ConnectionModifications.disconnectFromServer(player)
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

    // もし接続先ターゲットが保存シグナル待機サーバーのリストに入っていなければこのリスナには関係ない
    if (!configuration.shouldAwaitForSaveSignal(ServerName(targetServer.getName)))
      return

    if (!playersBeingConnectedToNewServer.contains(playerName)) {
      val disconnectSourceIfExists = player.getServer match {
        case null => Sync[F].unit
        case originalServer =>
          ConnectionModifications.letConnectionLinger[F](player) >>
            EmitGlobalLock.of[F](playerName, ServerName(originalServer.getInfo.getName)) >>
            ConnectionModifications.disconnectFromServer(player)
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
