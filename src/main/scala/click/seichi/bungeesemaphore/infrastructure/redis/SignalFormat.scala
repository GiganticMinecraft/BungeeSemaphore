package click.seichi.bungeesemaphore.infrastructure.redis

import click.seichi.bungeesemaphore.domain.PlayerName
import redis.api.pubsub.PMessage

object SignalFormat {

  final val keyEventChannelPattern = "__keyevent@0__:*"
  final val lockKeyPrefix = "bungee_semaphore_"

  sealed trait BungeeSemaphoreMessage
  case class DataLockRequest(playerName: PlayerName) extends BungeeSemaphoreMessage
  case class ReleaseDataLock(playerName: PlayerName) extends BungeeSemaphoreMessage
  case class DataSaveFailed(playerName: PlayerName) extends BungeeSemaphoreMessage

  def lockKeyOf(playerName: PlayerName): String = s"$lockKeyPrefix${playerName.value}"

  def parsePMessage(message: PMessage): Option[BungeeSemaphoreMessage] = {
    val splitData = message.data.utf8String.split(' ')
    if (splitData.length == 0) return None

    val playerName = PlayerName(splitData(0).stripPrefix(lockKeyPrefix))

    message.channel.stripPrefix("__keyevent@0__:") match {
      case "set" => Some(DataLockRequest(playerName))
      case "del" => Some(ReleaseDataLock(playerName))
      case "expired" => Some(DataSaveFailed(playerName))
      case _ => None
    }
  }
}
