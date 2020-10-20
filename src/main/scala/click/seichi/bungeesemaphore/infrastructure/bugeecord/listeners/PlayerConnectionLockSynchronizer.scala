package click.seichi.bungeesemaphore.infrastructure.bugeecord.listeners

import cats.effect.Effect
import click.seichi.bungeesemaphore.application.{EffectEnvironment, HasPlayerConnectionLock}
import click.seichi.bungeesemaphore.domain.PlayerName
import click.seichi.generic.concurrent.synchronization.barrier.IndexedSwitchableBarrier
import net.md_5.bungee.api.event.{PlayerDisconnectEvent, PostLoginEvent}
import net.md_5.bungee.api.plugin.Listener

class PlayerConnectionLockSynchronizer[
  F[_]: Effect
](localLock: IndexedSwitchableBarrier[F, PlayerName])
 (implicit effectEnvironment: EffectEnvironment) extends Listener {

  def provideConnectionLock: HasPlayerConnectionLock[F] = {
    (playerName: PlayerName) => localLock(playerName).await
  }

  def onPlayerConnect(event: PostLoginEvent): Unit = {
    effectEnvironment.unsafeRunEffectAsync(
      "Lock connection lock on PostLogin",
      localLock(PlayerName(event.getPlayer.getName)).beginBlock
    )
  }

  def onPlayerDisconnect(event: PlayerDisconnectEvent): Unit = {
    effectEnvironment.unsafeRunEffectAsync(
      "Unlock connection lock on disconnect",
      localLock(PlayerName(event.getPlayer.getName)).unblock
    )
  }

}
