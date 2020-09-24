package click.seichi.bungeesemaphore.application.configuration

import click.seichi.bungeesemaphore.domain.ServerName

trait ServerNamePredicate extends (ServerName => Boolean)
