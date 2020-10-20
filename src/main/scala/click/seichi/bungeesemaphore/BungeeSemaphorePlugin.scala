package click.seichi.bungeesemaphore

import java.util.concurrent.Executors

import akka.actor.ActorSystem
import cats.effect.{ContextShift, IO, SyncIO, Timer}
import click.seichi.bungeesemaphore.application.configuration.Configuration
import click.seichi.bungeesemaphore.application.{EffectEnvironment, HasGlobalPlayerDataSaveLock, HasPlayerConnectionLock}
import click.seichi.bungeesemaphore.domain.PlayerName
import click.seichi.bungeesemaphore.infrastructure.JulLoggerEffectEnvironment
import click.seichi.bungeesemaphore.infrastructure.akka.ConfiguredActorSystemProvider
import click.seichi.bungeesemaphore.infrastructure.bugeecord.{PlayerConnectionLockSynchronizer, SemaphoringServerSwitcher}
import click.seichi.bungeesemaphore.infrastructure.redis.LocalLockRedisBridge
import click.seichi.generic.concurrent.synchronization.barrier.IndexedSwitchableBarrier
import net.md_5.bungee.api.ProxyServer
import net.md_5.bungee.api.plugin.Plugin

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

class BungeeSemaphorePlugin extends Plugin {
  var akkaSystem: ActorSystem = _

  override def onEnable(): Unit = {
    implicit val _executionContext: ExecutionContext = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())
    implicit val _contextShift: ContextShift[IO] = IO.contextShift(_executionContext)
    implicit val _effectEnvironment: EffectEnvironment = JulLoggerEffectEnvironment(getLogger)
    implicit val _timer: Timer[IO] = IO.timer(_executionContext)

    implicit val _configuration: Configuration = {
      new PluginConfiguration[SyncIO](getDataFolder).getConfiguration.unsafeRunSync()
    }

    implicit val _akkaSystem: ActorSystem = {
      this.akkaSystem = ConfiguredActorSystemProvider("reference.conf").provide()
      this.akkaSystem
    }

    implicit val _ioHasGlobalPlayerSemaphore: HasGlobalPlayerDataSaveLock[IO] = {
      // A lock whose state corresponds to downstream servers saving player data
      val downstreamSaveLock = IndexedSwitchableBarrier.unsafe[IO, PlayerName]

      LocalLockRedisBridge.bindLocalLockToRedis[IO](downstreamSaveLock).unsafeRunSync()
    }

    val connectionLockSynchronizer = {
      // A lock whose state corresponds to player connection states
      val connectionLock = IndexedSwitchableBarrier.unsafe[IO, PlayerName]

      new PlayerConnectionLockSynchronizer[IO](connectionLock)
    }

    implicit val _ioHasPlayerConnectionLock: HasPlayerConnectionLock[IO] = {
      connectionLockSynchronizer.provideConnectionLock
    }

    implicit val _proxy: ProxyServer = getProxy

    val listeners = Vector(
      connectionLockSynchronizer,
      new SemaphoringServerSwitcher[IO]
    )

    listeners.foreach { listener =>
      getProxy.getPluginManager.registerListener(this, listener)
    }
  }

  override def onDisable(): Unit = {
    getProxy.getPluginManager.unregisterListeners(this)
    Await.ready(this.akkaSystem.terminate(), Duration.Inf)
  }
}
