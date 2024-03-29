package com.productfoundry.akka.cqrs

/**
 * Defines the possible results of an aggregate command.
 */
object AggregateResult {

  sealed trait AggregateResult extends Serializable

  /**
   * Indicates a successful update to the aggregate.
   * @param tag after the update.
   * @param response of the aggregate.
   */
  case class Success(tag: AggregateTag, response: Any = Unit) extends AggregateResult

  /**
   * Indicates an update failure that can be corrected by the user.
   * @param cause of the failure
   */
  case class Failure(cause: DomainError) extends AggregateResult

}
