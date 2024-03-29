package com.productfoundry.akka.cqrs.project.domain

import akka.actor.{ActorLogging, ActorRefFactory, Props, ReceiveTimeout}
import akka.persistence._
import com.productfoundry.akka.cqrs.AggregateEventRecord
import com.productfoundry.akka.cqrs.project.{DirectProjection, ProjectionProvider, ProjectionRevision}

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}


object DomainAggregatorView {
  def apply[P <: DirectProjection[P]](actorRefFactory: ActorRefFactory, persistenceId: String)(initialState: P)(recoveryThreshold: FiniteDuration = 5.seconds) = {
    new DomainAggregatorView(actorRefFactory, persistenceId)(initialState)(recoveryThreshold)
  }
}

/**
 * Projects domain commits.
 */
class DomainAggregatorView[P <: DirectProjection[P]] private(actorRefFactory: ActorRefFactory, persistenceId: String)(initial: P)(recoveryThreshold: FiniteDuration) extends ProjectionProvider[P] {

  private val ref = actorRefFactory.actorOf(Props(new DomainView(persistenceId)))

  override def getWithRevision(minimum: ProjectionRevision): Future[StateWithRevision] = {
    val stateWithRevisionPromise = Promise[StateWithRevision]()
    ref ! DomainView.RequestState(minimum, stateWithRevisionPromise)
    stateWithRevisionPromise.future
  }

  class DomainView(val persistenceId: String) extends PersistentView with ActorLogging {

    import DomainView._

    override val viewId: String = s"$persistenceId-view"

    private var stateWithRevision: StateWithRevision = (initial, ProjectionRevision.Initial)

    private var promisesByRevision: Map[ProjectionRevision, Vector[Promise[StateWithRevision]]] = Map.empty

    override def preStart(): Unit = {
      context.become(recovering)
      context.setReceiveTimeout(recoveryThreshold)
      super.preStart()
    }

    private def updateState(revision: ProjectionRevision, eventRecord: AggregateEventRecord): Unit = {
      stateWithRevision = (stateWithRevision._1.project(eventRecord), revision)
    }

    private def savePromise(revision: ProjectionRevision, promise: Promise[StateWithRevision]): Unit = {
      val promises = promisesByRevision.get(revision).fold(Vector(promise))(promises => promises :+ promise)
      promisesByRevision = promisesByRevision.updated(revision, promises)
    }

    private def completePromises(revision: ProjectionRevision): Unit = {
      promisesByRevision.get(revision).foreach { promises =>
        promisesByRevision = promisesByRevision - revision
        promises.foreach(completePromise)
      }
    }

    private def completePromise(promise: Promise[StateWithRevision]): Unit = {
      promise.complete(util.Success(stateWithRevision))
    }

    def recovering: Receive = {

      case DomainCommit(revision, eventRecord) =>
        updateState(revision, eventRecord)

      case RequestState(minimum, promise) =>
        savePromise(minimum, promise)

      case ReceiveTimeout =>
        context.setReceiveTimeout(2.seconds)

        ProjectionRevision.Initial.value to stateWithRevision._2.value foreach { revision =>
          completePromises(ProjectionRevision(revision))
        }

        context.become(receive)
    }

    override def receive: Receive = {

      case DomainCommit(revision, eventRecord) =>
        updateState(revision, eventRecord)
        completePromises(revision)

      case RequestState(minimum, promise) =>
        if (minimum <= stateWithRevision._2) {
          completePromise(promise)
        } else {
          savePromise(minimum, promise)
          self ! Update()
        }

      case ReceiveTimeout =>
        if (promisesByRevision.nonEmpty) self ! Update()
    }
  }

  object DomainView {
    case class RequestState(minimum: ProjectionRevision, promise: Promise[StateWithRevision])
  }
}
