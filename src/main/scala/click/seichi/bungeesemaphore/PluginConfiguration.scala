package click.seichi.bungeesemaphore

import java.io.File
import java.nio.file.{Files, Path}

import cats.effect.Sync
import click.seichi.bungeesemaphore.application.configuration.{Configuration, ErrorMessages, ServerNamePredicate}
import click.seichi.bungeesemaphore.domain.ServerName
import net.md_5.bungee.api.chat.{BaseComponent, TextComponent}
import net.md_5.bungee.config.{ConfigurationProvider, YamlConfiguration}

import scala.util.matching.Regex

object PluginConfiguration {
  final val configurationFileName = "config.yml"
}

class PluginConfiguration[F[_]: Sync](dataFolder: File) {

  import cats.implicits._

  private def writeDefaultConfigIfNotPresent(targetPath: Path): F[Unit] = Sync[F].delay {
    val stream = getClass.getClassLoader.getResourceAsStream(PluginConfiguration.configurationFileName)

    try Files.copy(stream, targetPath)
    finally stream.close()
  }

  private val getConfigurationFile: F[File] = {
    for {
      file <- Sync[F].delay {
        if (!dataFolder.exists())
          dataFolder.mkdirs()

        new File(dataFolder, PluginConfiguration.configurationFileName)
      }
      _ <- if (!file.exists()) writeDefaultConfigIfNotPresent(file.toPath) else Sync[F].unit
    } yield file
  }

  val getConfiguration: F[Configuration] = {
    for {
      file <- getConfigurationFile
    } yield {
      val config = ConfigurationProvider
        .getProvider(classOf[YamlConfiguration])
        .load(file)

      val serverIsSynchronized: ServerNamePredicate = {
        val synchronizedServerRegex = new Regex(config.getString("synchronized-server-regex", ""))

        (serverName: ServerName) => synchronizedServerRegex.matches(serverName.value)
      }

      val errorMessagesFromConfig = {
        val errorLocaleSettings = config.getSection("locale.error")

        new ErrorMessages {
          override val downstreamCouldNotSaveData: BaseComponent = {
            new TextComponent(errorLocaleSettings.getString("failed-saving-data", ""))
          }
        }
      }

      new Configuration {
        override val emitsSaveSignalOnDisconnect: ServerNamePredicate = serverIsSynchronized
        override val shouldAwaitForSaveSignal: ServerNamePredicate = serverIsSynchronized
        override val errorMessages: ErrorMessages = errorMessagesFromConfig
      }
    }
  }

}
