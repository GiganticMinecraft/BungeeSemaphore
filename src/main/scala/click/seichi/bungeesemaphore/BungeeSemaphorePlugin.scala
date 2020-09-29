package click.seichi.bungeesemaphore

import cats.effect.{ContextShift, IO, SyncIO}
import click.seichi.bungeesemaphore.application.configuration.Configuration
import click.seichi.bungeesemaphore.application.{EffectEnvironment, HasGlobalPlayerSemaphore}
import click.seichi.bungeesemaphore.domain.PlayerName
import click.seichi.bungeesemaphore.infrastructure.JulLoggerEffectEnvironment
import click.seichi.bungeesemaphore.infrastructure.bugeecord.SemaphoringServerSwitcher
import net.md_5.bungee.api.ProxyServer
import net.md_5.bungee.api.plugin.Plugin

import scala.concurrent.ExecutionContext

class BungeeSemaphorePlugin extends Plugin {
  override def onEnable(): Unit = {
    implicit val _executionContext: ExecutionContext = ExecutionContext.global
    implicit val _contextShift: ContextShift[IO] = IO.contextShift(_executionContext)
    implicit val _effectEnvironment: EffectEnvironment = JulLoggerEffectEnvironment(getLogger)

    implicit val _proxy: ProxyServer = getProxy

    implicit val _configuration: Configuration = {
      new PluginConfiguration[SyncIO](getDataFolder).getConfiguration.unsafeRunSync()
    }

    // TODO 本物のシグナルハンドラで置き換える
    implicit val _signalHandler: HasGlobalPlayerSemaphore[IO] = new HasGlobalPlayerSemaphore[IO] {
      override def lock(playerName: PlayerName): IO[Unit] = IO.unit

      override def awaitLockAvailability(playerName: PlayerName): IO[Unit] = {
        import scala.concurrent.duration._

        IO.timer(_executionContext).sleep(5.seconds)
      }
    }

    val listeners = Vector(
      new SemaphoringServerSwitcher[IO]
    )

    listeners.foreach { listener =>
      getProxy.getPluginManager.registerListener(this, listener)
    }
  }

  override def onDisable(): Unit = {
    getProxy.getPluginManager.unregisterListeners(this)
  }
}
