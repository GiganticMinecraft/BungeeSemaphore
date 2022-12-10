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
import java.util.logging.Logger
import scala.collection.mutable

/**
 * The following diagram illustrates how this listener reacts to server switches requested by the player.
 * The horizontal arrows with hyphens (-) represent normal signals, whereas arrows with equals (=)
 * represent signals introduced by this listener.
 *
 * The horizontal lines with greater-than symbol (>) are publish signals that Spigot servers are __expected__
 * to send when they complete persisting (potentially to external data sources such as databases) any data
 * associated with the player. Lines with less-than symbol (<) are signals subscribed by BungeeCord servers.
 *
 * When the Spigot B has a server name `s` that both
 *  - `configuration.shouldAwaitForSaveSignal(s)`
 *  - `configuration.emitsSaveSignalOnDisconnect(s)`
 *  are `true`, the following flow happens:
 *
 * {{{
 *                       [BungeeCord]    [Spigot A] [Spigot B]  [Redis]
 *  Player p requests for a   |              |          |          |
 *  switch from Spigot A      |              |          |          |
 *  to Spigot B               |   Notifies   |          |          |
 * -----------[1]---------->  |  disconnect  |          |          |
 *                            |  ===[2]===>  |          |          |
 *                            |              |          |          |
 *                            |         "data being saved"         |
 *                            |  ==============[3]==============>  |
 *                            |              |          |          |
 *                            |          [Saves data]   |          |
 *                        [holds p's ]   [associated]   |          |
 *                        [connection]   [  with p  ]   |          |
 *                            |              |     "data saved"    |
 *                            |              |  >>>>>>>[4]>>>>>>>  |
 *                            |       "data has been saved"        |
 *                            |  <<<<<<<<<<<<<<[5]<<<<<<<<<<<<<<<  |
 *                            |              |          |          |
 *                            |        Notifies         |          |
 *   Player is notified a     |       connection        |          |
 *   world switch             |  ========[6]=========>  |          |
 *  <---------[7]-----------  |              |          |          |
 *                            |              |          |          |
 * }}}
 *
 * The reason why we must take control over [2] and [6] is due to the default behaviour of BungeeCord.
 * When it receives a server switch request (A -> B), it first sends a login signal to B, and when
 * that connection is fully established, disconnect the player from A and transfers them to B.
 * This goes against our intention to "disconnect from A, hold p's connection and then connect to B".
 */
class SemaphoringServerSwitcher[
  F[_]: ConcurrentEffect: HasGlobalPlayerDataSaveLock: HasPlayerConnectionLock: Timer
](implicit configuration: Configuration, effectEnvironment: EffectEnvironment, proxy: ProxyServer, logger: Logger)
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

    logger.info(s"${player.getName} requested a connection to ${targetServer.getName}")

    // If we get a server switch command from a player
    // that tries to go to the same server the player is already connected (e.g. sending `/server lobby` in lobby),
    // the ServerConnectEvent is not called hence the connection hangs.
    // We therefore need to explicitly avoid custom server switches in this case.
    if (Option(player.getServer).map(_.getInfo.getName).contains(targetServer.getName))
      return

    if (!configuration.shouldAwaitForSaveSignal(ServerName(targetServer.getName)))
      return

    if (!playersBeingConnectedToNewServer.contains(playerName)) {
      val disconnectSourceIfExists = player.getServer match {
        case null => Sync[F].unit
        case originalServer =>
          ConnectionModifications.letConnectionLinger[F](player) >>
            EmitGlobalLock.of[F](playerName, ServerName(originalServer.getInfo.getName)) >>
            ConnectionModifications.disconnectFromServer(player) >> Sync[F].delay { logger.info(s"Notification of $playerName's connection'") }
      }

      val reconnectToTarget = Sync[F].delay {
        // prevent this listener from reacting again
        playersBeingConnectedToNewServer.add(playerName)

        player.connect(targetServer)

        logger.info(s"Notification of $playerName's connection'")
      }

      event.setCancelled(true)

      effectEnvironment.unsafeRunEffectAsync(
        "Execute semaphoric flow on server switching",
        disconnectSourceIfExists >>
          ConcurrentEffect[F].race(
            AwaitDataSaveConfirmation.of[F](player, targetServer) >> reconnectToTarget,
            HasPlayerConnectionLock[F].awaitDisconnectedState(playerName)
          ) >> Sync[F].delay { logger.info(s"$playerName is notified a world switch") }
      )
    } else {
      // so that this listener ignores one `ServerConnectEvent` for marked players
      playersBeingConnectedToNewServer.remove(playerName)
    }
  }

}
