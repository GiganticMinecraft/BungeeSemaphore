package click.seichi.bungeesemaphore.infrastructure.bugeecord

import cats.effect.Effect
import click.seichi.bungeesemaphore.application.lock.IndexedLocalConditionVariables
import click.seichi.bungeesemaphore.application.{EffectEnvironment, HasPlayerConnectionLock}
import click.seichi.bungeesemaphore.domain.PlayerName
import net.md_5.bungee.api.event.{PlayerDisconnectEvent, PostLoginEvent}
import net.md_5.bungee.api.plugin.Listener

class PlayerConnectionLockSynchronizer[
  F[_]: Effect
](localLock: IndexedLocalConditionVariables[F, PlayerName])
 (implicit effectEnvironment: EffectEnvironment) extends Listener {

  def provideConnectionLock: HasPlayerConnectionLock[F] = {
    (playerName: PlayerName) => localLock(playerName).await
  }

  def onPlayerConnect(event: PostLoginEvent): Unit = {
    effectEnvironment.unsafeRunEffectAsync(
      "Lock connection lock on PostLogin",
      localLock(PlayerName(event.getPlayer.getName)).beginLock
    )
  }

  def onPlayerDisconnect(event: PlayerDisconnectEvent): Unit = {
    effectEnvironment.unsafeRunEffectAsync(
      "Unlock connection lock on disconnect",
      localLock(PlayerName(event.getPlayer.getName)).unlock
    )
  }

}
