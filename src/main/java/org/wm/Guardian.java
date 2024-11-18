package org.wm;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;

public final class Guardian {

    public static Behavior<Void> create() {
        return Behaviors.setup(context -> {
            KafkaEventConsumer kafkaEventConsumer = new KafkaEventConsumer();
            kafkaEventConsumer.runConsumer(context.getSystem());

            OrderEventProjection projection = new OrderEventProjection();
            projection.initProjections(context.getSystem());
            return Behaviors.empty();
        });
    }
}