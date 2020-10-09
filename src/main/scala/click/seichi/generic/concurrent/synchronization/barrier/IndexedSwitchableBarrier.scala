package click.seichi.generic.concurrent.synchronization.barrier

import cats.effect.Concurrent
import cats.effect.concurrent.Deferred

import scala.collection.concurrent
import scala.collection.concurrent.TrieMap

/**
 * [[K]]-indexed family of switchable barriers.
 */
class IndexedSwitchableBarrier[F[_], K](private val lockMap: concurrent.Map[K, Deferred[F, Boolean]])
                                       (implicit private val F: Concurrent[F]) {

  import cats.implicits._

  def apply(key: K): SwitchableBarrier[F] = KeyedSwitchableBarrier(key)

  private case class KeyedSwitchableBarrier(key: K) extends SwitchableBarrier[F] {
    override def beginBlock: F[Unit] = F.uncancelable {
      for {
        promise <- Deferred[F, Boolean]
        _ <- F.delay {
          lockMap.putIfAbsent(key, promise)
        }
      } yield ()
    }

    override def unblock(success: Boolean): F[Unit] = F.uncancelable {
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
          F.raiseError(BarrierUnblockedExceptionally)
        }
      } yield ()
    }
  }

}

object IndexedSwitchableBarrier {

  /**
   * Unsafely allocate state and get an instance of [[IndexedSwitchableBarrier]].
   */
  def unsafe[F[_]: Concurrent, K]: IndexedSwitchableBarrier[F, K] = {
    new IndexedSwitchableBarrier(new TrieMap())
  }

}
