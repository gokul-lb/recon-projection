package org.wm;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.Adapter;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.kafka.*;
import akka.kafka.cluster.sharding.KafkaClusterSharding;
import akka.kafka.javadsl.Committer;
import akka.kafka.javadsl.Consumer;
import akka.pattern.Patterns;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbend.cinnamon.akka.stream.CinnamonAttributes;
import com.typesafe.config.Config;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class KafkaEventConsumer {

    private final String TOPIC = "order-topic";
    private final String kafkaBootstrapServers = "localhost:9092";
    private final static Duration askTimeout = Duration.ofSeconds(3);
    private final static ObjectMapper objectMapper = new ObjectMapper();

    public void runConsumer(ActorSystem<Void> system) {
        ClusterSharding sharding = ClusterSharding.get(system);
        Config config = system.settings().config().getConfig(CommitterSettings.configPath());
        Logger log = system.log();
        Consumer
                .committableSource(consumerSettings(system), autoSubscription(system))
                .mapAsync(20, r -> Patterns.retry(
                        () -> businessLogic(log, r, sharding),
                        3,
                        Duration.ofMillis(200),
                        system
                ))
                .toMat(Committer.sink(CommitterSettings.create(config)), Consumer::createDrainingControl)
                .addAttributes(CinnamonAttributes
                        .isInstrumented()
                        .withReportByName("kafka-consumer-stream")
                        .withPerFlow()
                        .attributes())
                .run(system);
    }

    private static CompletableFuture<ConsumerMessage.Committable> businessLogic(
            Logger log,
            ConsumerMessage.CommittableMessage<String, String> r,
            ClusterSharding sharding) throws JsonProcessingException {
        log.info("Key -> {} Value -> {}}", r.record().key(), r.record().value());
        Header type = r.record().headers().headers("type").iterator().next();
        String msgType = new String(((RecordHeader) type).value());
        EntityRef<OrderPersistentBehavior.OrderCommand> orderCommandEntityRef = sharding
                .entityRefFor(OrderPersistentBehavior.ORDER_ENTITY_TYPE_KEY, r.record().key());
        return getResponseCompletionStage(r, msgType, orderCommandEntityRef)
                .<ConsumerMessage.Committable>thenApply(done -> r.committableOffset())
                .toCompletableFuture();
    }

    // ugly
    private static CompletionStage<OrderPersistentBehavior.Response> getResponseCompletionStage(
            ConsumerMessage.CommittableMessage<String, String> r,
            String msgType,
            EntityRef<OrderPersistentBehavior.OrderCommand> orderCommandEntityRef) throws JsonProcessingException {
        CompletionStage<OrderPersistentBehavior.Response> ask;
        if (msgType.equalsIgnoreCase("createOrder")) {
            OrderPersistentBehavior.CreateOrder c = objectMapper.readValue(r.record().value(), OrderPersistentBehavior.CreateOrder.class);
            ask = orderCommandEntityRef.ask(replyTo -> new OrderPersistentBehavior.CreateOrder(c.getId(), c.name(), replyTo), askTimeout);
        } else if (msgType.equalsIgnoreCase("AddItemToOrder")) {
            OrderPersistentBehavior.AddItemToOrder c = objectMapper.readValue(r.record().value(), OrderPersistentBehavior.AddItemToOrder.class);
            ask = orderCommandEntityRef.ask(replyTo -> new OrderPersistentBehavior.AddItemToOrder(c.id(), c.itemName(), replyTo), askTimeout);
        } else if (msgType.equalsIgnoreCase("CloseOrder")) {
            OrderPersistentBehavior.CloseOrder c = objectMapper.readValue(r.record().value(), OrderPersistentBehavior.CloseOrder.class);
            ask = orderCommandEntityRef.ask(replyTo -> new OrderPersistentBehavior.CloseOrder(c.id(), replyTo), askTimeout);
        } else {
            System.out.println("invalid command!!!");
            throw new RuntimeException("Invalid command!");
        }
        return ask;
    }

    private AutoSubscription autoSubscription(ActorSystem<Void> system) {
        ActorRef<ConsumerRebalanceEvent> rebalancedListener = KafkaClusterSharding
                .get(system)
                .rebalanceListener(OrderPersistentBehavior.ORDER_ENTITY_TYPE_KEY);
        return Subscriptions
                .topics(TOPIC)
                .withRebalanceListener(Adapter.toClassic(rebalancedListener));
    }

    private ConsumerSettings<String, String> consumerSettings(ActorSystem<Void> system) {
        ConsumerSettings<String, String> consumerSettings = ConsumerSettings
                .create(Adapter.toClassic(system), new StringDeserializer(), new StringDeserializer())
                .withBootstrapServers(kafkaBootstrapServers)
                .withGroupId("order-topic-group-id")
                .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return consumerSettings;
    }
}
