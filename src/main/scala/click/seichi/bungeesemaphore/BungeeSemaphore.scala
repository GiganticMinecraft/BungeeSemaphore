package click.seichi.bungeesemaphore

import cats.effect.{ContextShift, IO}
import click.seichi.bungeesemaphore.application.DownstreamSignalHandler
import click.seichi.bungeesemaphore.application.configuration.{Configuration, ServerNamePredicate}
import click.seichi.bungeesemaphore.domain.PlayerName
import jdk.internal.net.http.common.Utils
import net.md_5.bungee.api.plugin.Plugin

import scala.concurrent.ExecutionContext

class BungeeSemaphore extends Plugin {

  override def onEnable(): Unit = {
    implicit val _executionContext: ExecutionContext = ExecutionContext.global
    implicit val _contextShift: ContextShift[IO] = IO.contextShift(_executionContext)

    // TODO 本物の設定ファイルで置き換える
    implicit val _configuration: Configuration = new Configuration {
      override val shouldAwaitForSaveSignal: ServerNamePredicate = _ => true
    }

    // TODO 本物のシグナルハンドラで置き換える
    implicit val _signalHandler: DownstreamSignalHandler[IO] = {
      (_: PlayerName, _: Utils.ServerName) => {
        import scala.concurrent.duration._

        IO.timer(_executionContext).sleep(5.seconds)
      }
    }
  }
}
