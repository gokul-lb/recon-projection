package org.wm;

import akka.Done;
import akka.persistence.query.typed.EventEnvelope;
import akka.projection.r2dbc.javadsl.R2dbcHandler;
import akka.projection.r2dbc.javadsl.R2dbcSession;
import io.r2dbc.spi.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;

public class OrderEventProjectionHandler extends R2dbcHandler<EventEnvelope<OrderPersistentBehavior.OrderEvent>> {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public CompletionStage<Done> process(
            R2dbcSession session,
            EventEnvelope<OrderPersistentBehavior.OrderEvent> orderEventEventEnvelope) {
        OrderPersistentBehavior.OrderEvent event = orderEventEventEnvelope.getEvent();
        logger.info("Processing event at the projection");
        Statement stmt =
                session
                        .createStatement("UPDATE total_order_events SET total_events = total_events + 1  WHERE id = 1");
        return session.updateOne(stmt).thenApply(rowsUpdated -> Done.getInstance());
    }
}
