package click.seichi.bungeesemaphore.infrastructure.bugeecord

import cats.effect.Effect
import click.seichi.bungeesemaphore.application.{EffectEnvironment, HasPlayerConnectionLock, PlayerNameLocalLock}
import click.seichi.bungeesemaphore.domain.PlayerName
import net.md_5.bungee.api.event.{PlayerDisconnectEvent, PostLoginEvent}
import net.md_5.bungee.api.plugin.Listener

class PlayerConnectionLockSynchronizer[
  F[_]: Effect
](localLock: PlayerNameLocalLock[F])
 (implicit effectEnvironment: EffectEnvironment) extends Listener {

  def provideConnectionLock: HasPlayerConnectionLock[F] = {
    (playerName: PlayerName) => localLock.awaitLockAvailability(playerName)
  }

  def onPlayerConnect(event: PostLoginEvent): Unit = {
    effectEnvironment.unsafeRunEffectAsync(
      "Lock connection lock on PostLogin",
      localLock.lock(PlayerName(event.getPlayer.getName))
    )
  }

  def onPlayerDisconnect(event: PlayerDisconnectEvent): Unit = {
    effectEnvironment.unsafeRunEffectAsync(
      "Unlock connection lock on disconnect",
      localLock.unlock(PlayerName(event.getPlayer.getName))
    )
  }

}
