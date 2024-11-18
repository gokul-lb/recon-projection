import { createRequire } from 'module';
const require = createRequire(import.meta.url);
const {Kafka} = require('kafkajs')

const kafka = new Kafka({
    clientId: 'my-app',
    brokers: ['localhost:9092'],
})
const producer = kafka.producer()
await producer.connect()
await generateLoad(100_000);
await closeProducer();

// helper methods
async function generateLoad(n) {
    for (let i = 0; i < n; i++) {
        console.log("sending order-"+i);
        await producer.send(getCreateOrderPayload(i));
        await producer.send(getAddItemPayload(i));
        await producer.send(getAddItemPayload(i));
    }
}
function getCreateOrderPayload(i) {
    return {
        topic: 'order-topic',
        messages: [{
            key: 'order-' + i,
            value: '{ "id": "order-' + i + '", "name" : "order-' + i + '" }',
            headers: {
                'type': 'createOrder',
            }
        }]
    };
}

function getAddItemPayload(i) {
    return {
        topic: 'order-topic',
        messages: [{
            key: 'order-' + i,
            value: '{ "id": "order-' + i + '", "itemName" : "order-' + i + '" }',
            headers: {
                'type': 'AddItemToOrder',
            }
        }]
    };
}

function getCloseOrderPayload(i) {
    return {
        topic: 'order-topic',
        messages: [{
            key: 'order-' + i,
            value: '{ "id": "order-' + i + '" }',
            headers: {
                'type': 'CloseOrder',
            }
        }]
    };
}

async function closeProducer() {
    await producer.disconnect()
}

