package click.seichi.bungeesemaphore.infrastructure.bugeecord.actions

import cats.effect.Sync
import net.md_5.bungee.api.ProxyServer
import net.md_5.bungee.api.connection.ProxiedPlayer
import net.md_5.bungee.api.event.ServerDisconnectEvent
import net.md_5.bungee.connection.DownstreamBridge
import net.md_5.bungee.netty.{ChannelWrapper, HandlerBoss}
import net.md_5.bungee.{ServerConnection, UserConnection}

object ConnectionModifications {

  private class ConnectionRetainingDownstreamBridge(server: ProxyServer,
                                            user: UserConnection,
                                            serverConnection: ServerConnection)
    extends DownstreamBridge(server, user, serverConnection) {

    override def disconnected(channel: ChannelWrapper): Unit = {
      val serverDisconnectEvent = new ServerDisconnectEvent(user, serverConnection.getInfo)

      server.getPluginManager.callEvent(serverDisconnectEvent)
    }

  }

  def letConnectionLinger[F[_] : Sync](player: ProxiedPlayer)
                                      (implicit proxy: ProxyServer): F[Unit] =
    Sync[F].delay {
      val userConnection = player.asInstanceOf[UserConnection]
      val serverConnection = userConnection.getServer

      serverConnection.getCh.getHandle.pipeline().get(classOf[HandlerBoss]).setHandler {
        new ConnectionRetainingDownstreamBridge(proxy, userConnection, serverConnection)
      }
    }

  def disconnectFromServer[F[_] : Sync](player: ProxiedPlayer): F[Unit] = Sync[F].delay {
    player.getServer.disconnect()
  }
}
