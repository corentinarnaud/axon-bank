package org.karamelsoft.axon.demo.libraries.service.command

import org.axonframework.commandhandling.CommandHandler
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.eventsourcing.EventSourcingHandler
import org.axonframework.modelling.command.*
import org.axonframework.spring.stereotype.Aggregate
import org.karamelsoft.research.axon.libraries.service.api.BadRequest
import org.karamelsoft.research.axon.libraries.service.api.Status
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

@Component
class AggregateConstraintStore(val commandGateway: CommandGateway) : ConstraintStore {
    override fun claimConstraint(id: String, duration: Duration) =
        commandGateway.sendAndWait<Status<Unit>>(ClaimConstraint(id, duration))

    override fun validateConstraint(id: String) =
        commandGateway.sendAndWait<Status<Unit>>(ValidateConstraint(id))

    override fun releaseConstraint(id: String) =
        commandGateway.sendAndWait<Status<Unit>>(ReleaseConstraint(id))
}

@Aggregate
internal class Constraint() {

    @AggregateIdentifier
    private lateinit var id: String

    private var claimedUntil: Instant? = null
    private var validated: Boolean = false

    @CommandHandler
    @CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
    fun handle(command: ClaimConstraint) = Status.of {
        AggregateLifecycle.apply(
            ConstraintClaimed(
                id = command.id,
                duration = command.duration,
                timestamp = command.timestamp
            )
        )
    }

    @CommandHandler
    @CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
    fun handle(command: ValidateConstraint) = when {
        validated -> alreadyValidated()
        claimedUntil?.isAfter(Instant.now()) ?: false -> alreadyClaimed()
        else -> Status.of {
            AggregateLifecycle.apply(
                ConstraintValidated(
                    id = command.id,
                    timestamp = command.timestamp
                )
            )
        }
    }

    @CommandHandler
    @CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
    fun handle(command: ReleaseConstraint) = Status.of {
        AggregateLifecycle.apply(
            ConstraintReleased(
                id = command.id,
                timestamp = command.timestamp
            )
        )
    }

    @EventSourcingHandler
    fun on(event: ConstraintClaimed) {
        id = event.id
        claimedUntil = Instant.now().plus(event.duration)
        validated = false
    }

    @EventSourcingHandler
    fun on(event: ConstraintValidated) {
        id = event.id
        claimedUntil = null
        validated = true
    }

    @EventSourcingHandler
    fun on(event: ConstraintReleased) {
        id = event.id
        claimedUntil = null
        validated = false
    }
}

fun alreadyClaimed() = BadRequest<String>(message = "Could not claim an already claimed constraint")
fun alreadyValidated() = BadRequest<String>(message = "Could not claim an already validated constraint")

interface ConstraintCommand {
    val id: String
    val timestamp: Instant
}

data class ClaimConstraint(
    @TargetAggregateIdentifier override val id: String,
    val duration: Duration,
    override val timestamp: Instant = Instant.now()
) : ConstraintCommand

data class ValidateConstraint(
    @TargetAggregateIdentifier override val id: String,
    override val timestamp: Instant = Instant.now()
) : ConstraintCommand

data class ReleaseConstraint(
    @TargetAggregateIdentifier override val id: String,
    override val timestamp: Instant = Instant.now()
) : ConstraintCommand

interface ConstraintEvent {
    val id: String
    val timestamp: Instant
}

data class ConstraintClaimed(
    override val id: String,
    val duration: Duration,
    override val timestamp: Instant = Instant.now()
) : ConstraintEvent

data class ConstraintValidated(override val id: String, override val timestamp: Instant = Instant.now()) :
    ConstraintEvent

data class ConstraintReleased(override val id: String, override val timestamp: Instant = Instant.now()) :
    ConstraintEvent