package click.seichi.bungeesemaphore.infrastructure

import java.util.logging.Logger

import cats.effect.Effect
import click.seichi.bungeesemaphore.application.EffectEnvironment

case class JulLoggerEffectEnvironment(julLogger: Logger) extends EffectEnvironment {
  override def unsafeRunEffectAsync[U, F[_] : Effect](context: String, program: F[U]): Unit = {
    Effect[F].toIO(program).unsafeRunAsync {
      case Left(error) =>
        julLogger.severe(error.toString)
        julLogger.severe(error.getStackTrace.map(_.toString).mkString("\n"))
      case Right(_) => ()
    }
  }
}
