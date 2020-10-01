package click.seichi.bungeesemaphore

import akka.actor.ActorSystem
import cats.effect.{ContextShift, IO, SyncIO}
import click.seichi.bungeesemaphore.application.configuration.Configuration
import click.seichi.bungeesemaphore.application.{EffectEnvironment, HasGlobalPlayerSemaphore, PlayerNameLocalLock}
import click.seichi.bungeesemaphore.infrastructure.JulLoggerEffectEnvironment
import click.seichi.bungeesemaphore.infrastructure.bugeecord.SemaphoringServerSwitcher
import click.seichi.bungeesemaphore.infrastructure.redis.LocalLockRedisBridge
import net.md_5.bungee.api.ProxyServer
import net.md_5.bungee.api.plugin.Plugin

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

class BungeeSemaphorePlugin extends Plugin {
  var akkaSystem: ActorSystem = _

  override def onEnable(): Unit = {
    implicit val _executionContext: ExecutionContext = ExecutionContext.global
    implicit val _contextShift: ContextShift[IO] = IO.contextShift(_executionContext)
    implicit val _effectEnvironment: EffectEnvironment = JulLoggerEffectEnvironment(getLogger)

    implicit val _configuration: Configuration = {
      new PluginConfiguration[SyncIO](getDataFolder).getConfiguration.unsafeRunSync()
    }

    implicit val _akkaSystem: ActorSystem = {
      this.akkaSystem = akka.actor.ActorSystem()
      this.akkaSystem
    }
    implicit val _localLock: PlayerNameLocalLock[IO] = PlayerNameLocalLock.unsafe
    implicit val _ioHasGlobalPlayerSemaphore: HasGlobalPlayerSemaphore[IO] = {
      LocalLockRedisBridge.bindLocalLockToRedis[IO].unsafeRunSync()
    }

    implicit val _proxy: ProxyServer = getProxy

    val listeners = Vector(
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
