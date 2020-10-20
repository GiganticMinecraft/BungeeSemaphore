package click.seichi.bungeesemaphore.domain

import cats.effect.{Async, Timer}

import scala.concurrent.duration.{Duration, FiniteDuration}

object Sleeping {

  def sleep[F[_]: Timer: Async](duration: Duration): F[Unit] = {
    duration match {
      case _: Duration.Infinite => Async[F].never
      case duration: FiniteDuration => Timer[F].sleep(duration)
    }
  }

}
