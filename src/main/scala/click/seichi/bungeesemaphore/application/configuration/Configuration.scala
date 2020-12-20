package click.seichi.bungeesemaphore.application.configuration

import net.md_5.bungee.api.chat.BaseComponent

import java.net.InetSocketAddress
import scala.concurrent.duration.Duration

trait ErrorMessages {

  val downstreamCouldNotSaveData: BaseComponent

}

trait RedisConnectionSettings {

  val host: String

  val port: Int

  val password: Option[String]

  lazy val address = new InetSocketAddress(host, port)

}

trait Configuration {

  val emitsSaveSignalOnDisconnect: ServerNamePredicate

  val shouldAwaitForSaveSignal: ServerNamePredicate

  val errorMessages: ErrorMessages

  val redis: RedisConnectionSettings

  val saveLockTimeout: Duration

}
