package click.seichi.bungeesemaphore.infrastructure.bugeecord

import cats.effect.Sync
import net.md_5.bungee.UserConnection
import net.md_5.bungee.api.ProxyServer
import net.md_5.bungee.api.connection.ProxiedPlayer
import net.md_5.bungee.netty.HandlerBoss

object ConnectionModifications {

  def letConnectionLinger[F[_]: Sync](player: ProxiedPlayer)
                                     (implicit proxy: ProxyServer): F[Unit] =
    Sync[F].delay {
      val userConnection = player.asInstanceOf[UserConnection]
      val serverConnection = userConnection.getServer

      serverConnection.getCh.getHandle.pipeline().get(classOf[HandlerBoss]).setHandler {
        new ConnectionRetainingDownstreamBridge(proxy, userConnection, serverConnection)
      }
    }

}
