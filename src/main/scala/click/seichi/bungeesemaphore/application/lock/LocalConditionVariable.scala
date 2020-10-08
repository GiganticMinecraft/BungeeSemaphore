package click.seichi.bungeesemaphore.application.lock

/**
 * A reference to a shared boolean state to which we can
 *
 *  - atomically set to true (lock)
 *  - atomically set to false, along with a completion state (unlock)
 *  - wait for the state to become false
 *
 * @define atomic
 * This operation is atomic.
 *
 * @define idempotent
 * This operation is idempotent, meaning that
 * if the operation happens twice without other operations happening,
 * the result is the same as if the operation happened only once.
 */
trait LocalConditionVariable[F[_]] {

  /**
   * The action to atomically set the variable state to `true`.
   *
   * After this action, [[await]] blocks until [[signalEndOfLock]] is performed.
   *
   * $atomic
   *
   * $idempotent
   */
  def beginLock: F[Unit]

  /**
   * This is an operation that
   *  - (atomically) set the variable state to `false`
   *  - signal [[await]] actions to proceed with their continuations
   *
   * $idempotent
   */
  def signalEndOfLock(success: Boolean): F[Unit]

  /**
   * The computation to (semantically) block until the variable state becomes `false`.
   *
   * This action is cancellable.
   */
  def await: F[Unit]

  /**
   * The computation to release a lock, if exists.
   *
   * This completes any computation, if exists, of [[await]].
   *
   * $atomic
   */
  final def unlock: F[Unit] = signalEndOfLock(success = true)

  /**
   * The computation to release a lock, if exists.
   *
   * Unlike [[unlock]], this action will make awaiting [[await]] actions fail with an error.
   *
   * $atomic
   */
  final def unlockWithFailure: F[Unit] =signalEndOfLock(success = false)

}
