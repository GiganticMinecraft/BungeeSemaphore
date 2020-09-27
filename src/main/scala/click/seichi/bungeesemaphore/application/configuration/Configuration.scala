package click.seichi.bungeesemaphore.application.configuration

import net.md_5.bungee.api.chat.BaseComponent

trait ErrorMessages {

  val downstreamCouldNotSaveData: BaseComponent

}

trait Configuration {

  val emitsSaveSignalOnDisconnect: ServerNamePredicate

  val shouldAwaitForSaveSignal: ServerNamePredicate

  val errorMessages: ErrorMessages

}
