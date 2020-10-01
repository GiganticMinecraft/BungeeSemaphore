package click.seichi.bungeesemaphore.infrastructure.redis

import click.seichi.bungeesemaphore.domain.PlayerName
import redis.api.pubsub.Message

object SignalFormat {

  final val signalingChannel = "BungeeSemaphore"

  object MessagePrefix {
    final val dataLockRequest = "player_data_being_saved"
    final val releaseDataLock = "confirm_player_data_saved"
    final val dataSaveFailed = "failed_saving_some_player_data"
  }

  sealed trait BungeeSemaphoreMessage
  case class DataLockRequest(playerName: PlayerName) extends BungeeSemaphoreMessage {
    override def toString: String = s"${MessagePrefix.dataLockRequest} ${playerName.value}"
  }
  case class ReleaseDataLock(playerName: PlayerName) extends BungeeSemaphoreMessage {
    override def toString: String = s"${MessagePrefix.releaseDataLock} ${playerName.value}"
  }
  case class DataSaveFailed(playerName: PlayerName) extends BungeeSemaphoreMessage {
    override def toString: String = s"${MessagePrefix.dataSaveFailed} ${playerName.value}"
  }

  def parseMessage(message: Message): Option[BungeeSemaphoreMessage] = {
    message.data.utf8String.split(' ') match {
      case Array(MessagePrefix.dataLockRequest, playerName) => Some(DataLockRequest(PlayerName(playerName)))
      case Array(MessagePrefix.releaseDataLock, playerName) => Some(ReleaseDataLock(PlayerName(playerName)))
      case Array(MessagePrefix.dataSaveFailed, playerName) => Some(DataSaveFailed(PlayerName(playerName)))
      case _ => None
    }
  }
}
