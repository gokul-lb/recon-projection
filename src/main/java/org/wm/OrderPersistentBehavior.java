package org.wm;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandler;
import akka.persistence.typed.javadsl.Effect;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehavior;

import java.util.ArrayList;
import java.util.List;

public class OrderPersistentBehavior extends EventSourcedBehavior<
        OrderPersistentBehavior.OrderCommand,
        OrderPersistentBehavior.OrderEvent,
        OrderPersistentBehavior.OrderState> {

    public static final EntityTypeKey<OrderCommand> ORDER_ENTITY_TYPE_KEY =
            EntityTypeKey.create(OrderCommand.class, "order-topic-group-id");

    public OrderPersistentBehavior(ActorContext<OrderCommand> context, String entityId, PersistenceId persistenceId) {
        super(persistenceId);
        context.getLog().info("Starting Order Actor - {}", entityId);
    }

    public static Behavior<OrderCommand> create(String entityId, PersistenceId persistenceId) {
        return Behaviors.setup(context -> new OrderPersistentBehavior(context, entityId, persistenceId));
    }

    public static void initSharding(ActorSystem<?> system) {
        ClusterSharding.get(system).init(Entity.of(ORDER_ENTITY_TYPE_KEY, entityContext ->
                OrderPersistentBehavior.create(entityContext.getEntityId(), PersistenceId.ofUniqueId(entityContext.getEntityId()))
        ));
    }

    // commands
    public static interface OrderCommand extends CborSerializable {
        String getId();
    }
    public static record CreateOrder(String id, String name,  ActorRef<Response> replyTo) implements OrderCommand {
        @Override
        public String getId() {
            return id;
        }
    }
    public static record AddItemToOrder(String id, String itemName,  ActorRef<Response> replyTo) implements OrderCommand {
        @Override
        public String getId() {
            return id;
        }
    }
    public static record CloseOrder(String id, ActorRef<Response> replyTo) implements OrderCommand {
        @Override
        public String getId() {
            return id;
        }
    }
    public static record Response(String message) implements CborSerializable {}


    // events
    public interface OrderEvent extends CborSerializable {}
    public static record OrderCreated(String id, String name) implements OrderEvent {}
    public static record ItemAddedToOrder(String orderId, String itemName) implements OrderEvent {}
    public static record OrderClosed(String id) implements OrderEvent {}

    // state
    public static class OrderState implements CborSerializable {
        String id;

        String name;
        List<String> items;
        String status;
        public OrderState() {
        }

        public OrderState createOrder(String id, String name) {
            this.id = id;
            this.name = name;
            this.status = "Created";
            this.items = new ArrayList<>();
            return this;
        }
        public OrderState updateItems(String item) {
            if(this.items == null) return this;
            items.add(item);
            this.status = "InProgress";
            return this;
        }

        public OrderState closeOrder() {
            this.status = "Closed";
            return null;
        }
    }



    @Override
    public OrderState emptyState() {
        return new OrderState();
    }

    @Override
    public CommandHandler<OrderCommand, OrderEvent, OrderState> commandHandler() {
        return newCommandHandlerBuilder()
                .forAnyState()
                .onCommand(CreateOrder.class, this::onCreateOrder)
                .onCommand(AddItemToOrder.class, this::onAddItemToOrder)
                .onCommand(CloseOrder.class, this::onCloseOrder)
                .build();
    }

    private Effect<OrderEvent, OrderState> onCreateOrder(CreateOrder c) {
        System.out.println("Creating new Order!!!");
        return Effect()
                .persist(new OrderCreated(c.id, c.name))
                .thenRun(newState -> c.replyTo.tell(new Response(String.format("Order - %s created!", c.id))));
    }

    private Effect<OrderEvent, OrderState> onAddItemToOrder(AddItemToOrder c) {
        return Effect()
                .persist(new ItemAddedToOrder(c.id, c.itemName))
                .thenRun(newState -> c.replyTo.tell(new Response(String.format("Item - %s added to order!", c.itemName))));
    }

    private Effect<OrderEvent, OrderState> onCloseOrder(CloseOrder c) {
        return Effect()
                .persist(new OrderClosed(c.id))
                .thenRun(newState -> c.replyTo.tell(new Response(String.format("Order %s closed!", c.id))));
    }

    @Override
    public EventHandler<OrderState, OrderEvent> eventHandler() {
        return newEventHandlerBuilder()
                .forAnyState()
                .onEvent(OrderCreated.class, (state, e) -> state.createOrder(e.id, e.name))
                .onEvent(ItemAddedToOrder.class, (state, e) -> state.updateItems(e.itemName))
                .onEvent(OrderClosed.class, (state, e) -> state.closeOrder())
                .build();
    }

}
