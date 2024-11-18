package org.wm;

import akka.actor.typed.ActorSystem;
import akka.management.javadsl.AkkaManagement;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Hello world!
 */
public class App {
    public static void main(String[] args) {
        int port = Integer.parseInt(args[0]);
        int mgmt = Integer.parseInt(args[1]);
        init(port, mgmt);
    }

    private static void init(int remotingPort, int akkaManagementPort) {
        ActorSystem<Void> system =
                ActorSystem.create(Guardian.create(), "kafkaProjection", config(remotingPort, akkaManagementPort));
        AkkaManagement.get(system).start();
        // initialise ClusterSharding of persistence actor
        OrderPersistentBehavior.initSharding(system);
        // initialise the kafka sharding
        KafkaShardingSettings.init(system, OrderPersistentBehavior.ORDER_ENTITY_TYPE_KEY);
    }

    private static Config config(int port, int managementPort) {
        return ConfigFactory
                .parseString("akka.remote.artery.canonical.port = " + port + "\n" +
                        "akka.management.http.port = " + managementPort + "\n" +
                        "akka.management.http.bind-hostname = 0.0.0.0 \n" +
                        "akka.management.http.bind-port = " + managementPort + "\n"
                ).withFallback(ConfigFactory.load());
    }


}
