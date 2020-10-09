package click.seichi.generic.concurrent.synchronization.barrier

/**
 * A reference to a queue of fibers awaiting for a signal to be restarted.
 *
 * This object has two "phases", affecting how the `await` action is completed:
 *
 * - pass-through phase
 * - blocking phase
 *
 * When the object is in the pass-through phase, any execution of `await` completes immediately.
 * However, if the object is in the blocking phase,
 * `await` blocks until the object is switched back to the pass-through phase.
 *
 * @define atomic
 * This operation is atomic.
 *
 * @define idempotent
 * This operation is idempotent, meaning that
 * if the operation happens twice without other operations happening,
 * the result is the same as if the operation happened only once.
 */
trait SwitchableBarrier[F[_]] {

  /**
   * The action to atomically set the object to blocking phase if it is in pass-through phase.
   *
   * $atomic
   *
   * $idempotent
   */
  def beginBlock: F[Unit]

  /**
   * The action to
   *  - atomically switch back to the pass-through phase
   *  - signal resumption to all fibers that has been executing `await` at the point the phase is switched back
   *
   * The parameter `success` indicates if the execution of `await` should
   * continue or fail with a [[BarrierUnblockedExceptionally]] exception.
   *
   * $idempotent
   */
  def unblock(success: Boolean): F[Unit]

  /**
   * The computation to (semantically) block until the variable state becomes `false`.
   *
   * This action fails with [[BarrierUnblockedExceptionally]] if [[unblock]] with `false` has been run.
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
  final def unlock: F[Unit] = unblock(success = true)

  /**
   * The computation to release a lock, if exists.
   *
   * Unlike [[unlock]], this action will make awaiting [[await]] actions fail with an error.
   *
   * $atomic
   */
  final def unlockWithFailure: F[Unit] =unblock(success = false)

}
