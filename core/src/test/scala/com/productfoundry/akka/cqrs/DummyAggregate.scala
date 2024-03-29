package com.productfoundry.akka.cqrs

import com.productfoundry.akka.PassivationConfig
import com.productfoundry.akka.cqrs.DummyAggregate._

class DummyAggregate(val passivationConfig: PassivationConfig) extends Aggregate {

  type S = DummyState

  override def handleCommand: Receive = {
    case Create(aggregateId) =>
      tryCommit {
        Right(Changes(Created(aggregateId)))
      }

    case Count(aggregateId) =>
      tryCommit {
        Right(Changes(Counted(aggregateId, state.count + 1)))
      }

    case CountWithRequiredRevisionCheck(aggregateId) =>
      tryCommit {
        Right(Changes(Counted(aggregateId, state.count + 1)))
      }

    case CountWithPayload(aggregateId) =>
      tryCommit {
        Right(Changes(Counted(aggregateId, state.count + 1)).withResponse(state.count))
      }

    case Increment(aggregateId, amount) =>
      tryCommit {
        if (amount > 0) {
          Right(Changes(Incremented(aggregateId, amount)))
        } else {
          Left(ValidationError(InvalidIncrement(amount)))
        }
      }

    case Delete(aggregateId) =>
      tryCommit {
        Right(Changes(Deleted(aggregateId)))
      }

    case GetCount(_) =>
      sender() ! state.count
  }

  override val factory: StateModifications = {
    case Created(_) => DummyState(0)
  }

  case class DummyState(count: Int) extends AggregateState {
    override def update: StateModifications = {
      case Counted(_, _count) => copy(count = _count)
      case Incremented(_, amount) => copy(count = count + amount)
    }
  }

}

object DummyAggregate {

  sealed trait DummyMessage extends AggregateMessage {
    override type Id = DummyId
  }

  sealed trait DummyAggregateCommand extends DummyMessage with AggregateCommand

  case class Create(id: DummyId) extends DummyAggregateCommand

  case class Count(id: DummyId) extends DummyAggregateCommand

  case class CountWithRequiredRevisionCheck(id: DummyId) extends DummyAggregateCommand with RequiredRevisionCheck

  case class CountWithPayload(id: DummyId) extends DummyAggregateCommand

  case class Increment(id: DummyId, amount: Int) extends DummyAggregateCommand

  case class Delete(id: DummyId) extends DummyAggregateCommand

  sealed trait DummyEvent extends DummyMessage with AggregateEvent

  case class Created(id: DummyId) extends DummyEvent

  case class Counted(id: DummyId, count: Int) extends DummyEvent

  case class Incremented(id: DummyId, amount: Int) extends DummyEvent

  case class Deleted(id: DummyId) extends DummyEvent with AggregateDeleteEvent

  sealed trait TestValidationMessage extends ValidationMessage

  case class InvalidIncrement(value: Int) extends TestValidationMessage

  case class GetCount(id: DummyId) extends DummyMessage

}
