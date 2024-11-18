package org.wm;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.Adapter;
import akka.cluster.sharding.external.ExternalShardAllocationStrategy;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.kafka.ConsumerSettings;
import akka.kafka.cluster.sharding.KafkaClusterSharding;
import akka.persistence.typed.PersistenceId;
import akka.util.Timeout;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

public class KafkaShardingSettings {

    // https://doc.akka.io/libraries/alpakka-kafka/current/cluster-sharding.html#akka-cluster-sharding
    public static void init(ActorSystem<Void> system, EntityTypeKey<OrderPersistentBehavior.OrderCommand> typeKey) {
        kafkaClusterShardingMessageExtractor(system)
                .thenAccept(extractor -> {
                    ClusterSharding.get(system)
                            .init(Entity
                                    .of(typeKey, ctx -> OrderPersistentBehavior.create(
                                            ctx.getEntityId(),
                                            PersistenceId.ofUniqueId(ctx.getEntityId()))
                                    )
                                    .withAllocationStrategy(
                                            new ExternalShardAllocationStrategy(
                                                    system,
                                                    typeKey.name(),
                                                    Timeout.create(Duration.ofSeconds(10))
                                            )
                                    )
                                    .withMessageExtractor(extractor)
                            );
                });
    }

    // Step 1: To route Kafka messages to the correct user entity
    private static CompletionStage<KafkaClusterSharding
            .KafkaShardingNoEnvelopeExtractor<OrderPersistentBehavior.OrderCommand>>
    kafkaClusterShardingMessageExtractor(ActorSystem<Void> system) {
        return KafkaClusterSharding.get(system)
                .messageExtractorNoEnvelope(
                        "order-topic",
                        Duration.ofSeconds(10),
                        OrderPersistentBehavior.OrderCommand::getId,
                        ConsumerSettings.create(
                                Adapter.toClassic(system), new StringDeserializer(), new StringDeserializer()));
    }


    private static void setupClusterSharding(
            CompletionStage<KafkaClusterSharding
                    .KafkaShardingNoEnvelopeExtractor<OrderPersistentBehavior.OrderCommand>>
                    messageExtractor,
            ActorSystem<Void> system,
            EntityTypeKey<OrderPersistentBehavior.OrderCommand> typeKey) {
        messageExtractor.thenAccept(
                extractor ->
                        ClusterSharding.get(system)
                                .init(
                                        Entity
                                                .of(
                                                        typeKey, ctx ->
                                                                OrderPersistentBehavior.create(ctx.getEntityId(),
                                                                        PersistenceId.ofUniqueId(ctx.getEntityId()))
                                                )
                                                .withAllocationStrategy(
                                                        new ExternalShardAllocationStrategy(
                                                                system,
                                                                typeKey.name(),
                                                                Timeout.create(Duration.ofSeconds(10))
                                                        )
                                                )
                                                .withMessageExtractor(extractor)
                                )
        );
    }
}
