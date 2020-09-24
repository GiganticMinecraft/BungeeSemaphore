package click.seichi.bungeesemaphore.application

import cats.effect.Effect

trait EffectEnvironment {

  /**
   * Unsafely execute `program` in the context described by `context`.
   *
   * Invocation of this method is generally side-effectful, hence the prefix "unsafe".
   * Ideally, this method should be invoked only once at the "very end" of the program.
   *
   * Implementations of this method may use `context` for, say, logging purposes.
   */
  def unsafeRunEffectAsync[U, F[_]: Effect](context: String, program: F[U]): Unit

}

object EffectEnvironment {

  def apply(implicit effectEnvironment: EffectEnvironment): EffectEnvironment = effectEnvironment

}
