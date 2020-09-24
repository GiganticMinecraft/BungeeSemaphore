package click.seichi.bungeesemaphore.infrastructure.bugeecord

import net.md_5.bungee.api.ProxyServer
import net.md_5.bungee.api.event.ServerDisconnectEvent
import net.md_5.bungee.connection.DownstreamBridge
import net.md_5.bungee.netty.ChannelWrapper
import net.md_5.bungee.{ServerConnection, UserConnection}

class ConnectionRetainingDownstreamBridge(server: ProxyServer,
                                          user: UserConnection,
                                          serverConnection: ServerConnection)
  extends DownstreamBridge(server, user, serverConnection) {

  override def disconnected(channel: ChannelWrapper): Unit = {
    println("Downstream disconnected!")

    val serverDisconnectEvent = new ServerDisconnectEvent(user, serverConnection.getInfo)

    server.getPluginManager.callEvent(serverDisconnectEvent)
  }

}
