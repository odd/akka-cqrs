package com.productfoundry.akka.cqrs.project.domain

import com.productfoundry.akka.cqrs.AggregateEventRecord
import com.productfoundry.akka.cqrs.project.ProjectionRevision
import com.productfoundry.akka.serialization.Persistable

/**
 * A successful aggregated event record.
 *
 * @param revision of the domain aggregator.
 * @param eventRecord that was aggregated.
 */
case class DomainCommit(revision: ProjectionRevision, eventRecord: AggregateEventRecord) extends Persistable