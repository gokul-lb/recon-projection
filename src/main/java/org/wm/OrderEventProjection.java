package org.wm;
import akka.actor.typed.ActorSystem;
import akka.cluster.sharding.typed.ShardedDaemonProcessSettings;
import akka.cluster.sharding.typed.javadsl.ShardedDaemonProcess;
import akka.japi.Pair;
import akka.persistence.query.Offset;
import akka.persistence.r2dbc.query.javadsl.R2dbcReadJournal;
import akka.projection.ProjectionBehavior;
import akka.persistence.query.typed.EventEnvelope;
import akka.projection.Projection;
import akka.projection.ProjectionId;
import akka.projection.eventsourced.javadsl.EventSourcedProvider;
import akka.projection.javadsl.SourceProvider;
import akka.projection.r2dbc.R2dbcProjectionSettings;
import akka.projection.r2dbc.javadsl.R2dbcProjection;

import java.util.List;
import java.util.Optional;

public class OrderEventProjection {

   public void initProjections(ActorSystem<Void> system) {
       system.log().info("Starting projection!!!");
       int numberOfSliceRanges = 4;
        ShardedDaemonProcess.get(system)
                .initWithContext(
                        ProjectionBehavior.Command.class,
                        "orderProjection",
                        4,
                        daemonContext -> {
                            List<Pair<Integer, Integer>> sliceRanges =
                                    EventSourcedProvider.sliceRanges(
                                            system, R2dbcReadJournal.Identifier(), 4);
                            Pair<Integer, Integer> sliceRange = sliceRanges.get(daemonContext.processNumber());
                            return ProjectionBehavior.create(createProjection(system, sliceRange));
                        },
                        ShardedDaemonProcessSettings.create(system),
                        Optional.of(ProjectionBehavior.stopMessage()));
    }

    private Projection<EventEnvelope<OrderPersistentBehavior.OrderEvent>> createProjection(
            ActorSystem<Void> system,
            Pair<Integer, Integer> sliceRanges) {
        int minSlice = sliceRanges.first();
        int maxSlice = sliceRanges.second();

        String entityType = OrderPersistentBehavior.ORDER_ENTITY_TYPE_KEY.name();
        SourceProvider<Offset, EventEnvelope<OrderPersistentBehavior.OrderEvent>> sourceProvider =
                EventSourcedProvider.eventsBySlices(
                        system, R2dbcReadJournal.Identifier(), "", minSlice, maxSlice);

        ProjectionId projectionId =
                ProjectionId.of("Orders", "orders-" + minSlice + "-" + maxSlice);
        Optional<R2dbcProjectionSettings> settings = Optional.empty();

        return R2dbcProjection.atLeastOnce(
                projectionId, settings, sourceProvider, OrderEventProjectionHandler::new, system);
    }
}
