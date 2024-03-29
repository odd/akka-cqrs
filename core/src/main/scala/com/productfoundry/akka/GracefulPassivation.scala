package com.productfoundry.akka

import akka.actor.{PoisonPill, Actor, ReceiveTimeout}

import scala.concurrent.duration._

/**
 * Sent to the parent upon receive timeout.
 * @param stopMessage to receive back from parent.
 */
case class Passivate(stopMessage: Any)

/**
 * Configures passivation behavior.
 * @param passivationMessage to send after inactivity timeout.
 * @param inactivityTimeout after which to send the passivation message.
 */
case class PassivationConfig(passivationMessage: Any = PoisonPill, inactivityTimeout: Duration = 30.minutes)

/**
 * Allows graceful passivation of actors.
 */
trait GracefulPassivation extends Actor {

  /**
   * Passivation config is determined by the actor.
   */
  val passivationConfig: PassivationConfig

  /**
   * Registers a receive timeout to trigger sending the configured passivation message.
   */
  override def preStart(): Unit = {
    context.setReceiveTimeout(passivationConfig.inactivityTimeout)
    super.preStart()
  }

  /**
   * Sends the configured passivation message to the parent actor on receive timeout.
   * @param message unhandled message.
   */
  override def unhandled(message: Any): Unit = {
    message match {
      case ReceiveTimeout => context.parent ! Passivate(passivationConfig.passivationMessage)
      case _ => super.unhandled(message)
    }
  }
}
