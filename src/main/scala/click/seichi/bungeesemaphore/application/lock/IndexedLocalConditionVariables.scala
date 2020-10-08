package click.seichi.bungeesemaphore.application.lock

import cats.effect.Concurrent
import cats.effect.concurrent.Deferred

import scala.collection.concurrent
import scala.collection.concurrent.TrieMap

/**
 * [[K]]-indexed family of condition variables.
 */
class IndexedLocalConditionVariables[F[_], K](private val lockMap: concurrent.Map[K, Deferred[F, Boolean]])
                                             (implicit private val F: Concurrent[F]) {

  import cats.implicits._

  def apply(key: K): LocalConditionVariable[F] = KeyedLocalConditionVariable(key)

  private case class KeyedLocalConditionVariable(key: K) extends LocalConditionVariable[F] {
    override def beginLock: F[Unit] = F.uncancelable {
      for {
        promise <- Deferred[F, Boolean]
        _ <- F.delay {
          lockMap.putIfAbsent(key, promise)
        }
      } yield ()
    }

    override def signalEndOfLock(success: Boolean): F[Unit] = F.uncancelable {
      for {
        promise <- F.delay {
          lockMap.remove(key)
        }
        _ <- promise match {
          case Some(promise) => promise.complete(success)
          case None => F.unit
        }
      } yield ()
    }

    override def await: F[Unit] = F.uncancelable {
      for {
        promise <- F.delay {
          lockMap.get(key)
        }
        success <- promise match {
          case Some(promise) => promise.get
          case None => F.pure(true)
        }
        _ <- if (success) {
          F.unit
        } else {
          F.raiseError(LockReleasedExceptionally)
        }
      } yield ()
    }
  }

}

object IndexedLocalConditionVariables {

  /**
   * Unsafely allocate state and get an instance of [[IndexedLocalConditionVariables]].
   */
  def unsafe[F[_]: Concurrent, K]: IndexedLocalConditionVariables[F, K] = {
    new IndexedLocalConditionVariables(new TrieMap())
  }

}
