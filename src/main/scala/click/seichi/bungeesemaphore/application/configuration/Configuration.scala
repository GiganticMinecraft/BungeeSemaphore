package click.seichi.bungeesemaphore.application.configuration

trait Configuration {

  val shouldAwaitForSaveSignal: ServerNamePredicate

}
