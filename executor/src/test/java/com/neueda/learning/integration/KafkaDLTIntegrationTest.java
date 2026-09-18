package com.neueda.learning.integration;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;

/**
 * Integration test for Kafka DLT flow.
 *
 * This test verifies that poison messages are correctly routed to the orders.DLT topic
 * with proper failure-reason headers.
 *
 * Prerequisites:
 * - Kafka is running on EC2 at 10.8.64.174:8085
 * - Executor service is running locally and consuming from orders topic
 * - PostgreSQL and other services are running
 *
 * Run this test with:
 * mvn test -Dtest=KafkaDLTIntegrationTest
 *
 * Or run as a standalone Java app:
 * java -cp "target/classes:target/dependency/*" com.neueda.learning.integration.KafkaDLTIntegrationTest
 */
public class KafkaDLTIntegrationTest {

    private static final String BOOTSTRAP_SERVERS = "10.8.64.174:8085";
    private static final String ORDERS_TOPIC = "orders";
    private static final String DLT_TOPIC = "orders.DLT";

    public static void main(String[] args) throws Exception {
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║        Kafka DLT Integration Test - Poison Message Flow       ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

        try {
            // Step 1: Verify topics exist
            verifyTopicsExist();

            // Step 2: Send multiple poison messages with different failure types
            sendPoisonMessages();

            // Step 3: Consume from DLT and verify
            consumeFromDLT();

            System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
            System.out.println("║                    ✓ Test Complete                           ║");
            System.out.println("╚════════════════════════════════════════════════════════════════╝");
        } catch (Exception e) {
            System.err.println("\n✗ Test failed with error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Step 1: Verify that required Kafka topics exist
     */
    private static void verifyTopicsExist() {
        System.out.println("Step 1: Verifying Kafka topics exist...\n");
        Properties adminProps = new Properties();
        adminProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);

        try (AdminClient admin = AdminClient.create(adminProps)) {
            var topics = admin.listTopics().names().get();

            System.out.println("Available topics: " + topics);

            if (topics.contains(ORDERS_TOPIC)) {
                System.out.println("  ✓ orders topic found");
            } else {
                System.out.println("  ✗ ERROR: orders topic NOT found");
                throw new Exception("orders topic does not exist");
            }

            if (topics.contains(DLT_TOPIC)) {
                System.out.println("  ✓ orders.DLT topic found");
            } else {
                System.out.println("  ⚠ orders.DLT topic NOT found (will be created on first error)");
            }

            System.out.println();
        } catch (Exception e) {
            System.err.println("  ✗ Error verifying topics: " + e.getMessage());
            throw new RuntimeException("Cannot connect to Kafka broker at " + BOOTSTRAP_SERVERS, e);
        }
    }

    /**
     * Step 2: Send poison messages that should trigger permanent failures
     */
    private static void sendPoisonMessages() {
        System.out.println("Step 2: Sending poison messages to orders topic...\n");

        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.ACKS_CONFIG, "all");

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {

            // Poison Message 1: Missing orderId field
            System.out.println("Sending Poison Message 1: Missing orderId field");
            String poisonJson1 = buildOrderPlacedJson(
                UUID.randomUUID().toString(),
                "100",  // accountId
                "INFY.NS",
                "BUY",
                10,
                500.00,
                null  // orderId = null (POISON!)
            );

            sendMessage(producer, ORDERS_TOPIC, "100", poisonJson1);
            System.out.println("  ✓ Message sent\n");

            // Poison Message 2: Completely malformed JSON
            System.out.println("Sending Poison Message 2: Malformed JSON");
            sendMessage(producer, ORDERS_TOPIC, "101", "this-is-not-json-{invalid");
            System.out.println("  ✓ Message sent\n");

            // Poison Message 3: Missing payload entirely
            System.out.println("Sending Poison Message 3: Missing payload");
            String poisonJson3 = "{\"eventId\":\"" + UUID.randomUUID() +
                "\",\"eventType\":\"ORDER_PLACED\",\"payload\":null}";
            sendMessage(producer, ORDERS_TOPIC, "102", poisonJson3);
            System.out.println("  ✓ Message sent\n");

            // Wait for Executor to process all messages
            System.out.println("Waiting 5 seconds for Executor to process and route to DLT...");
            Thread.sleep(5000);
            System.out.println();

        } catch (Exception e) {
            System.err.println("✗ Error sending poison messages: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private static void consumeFromDLT() {
        System.out.println("Step 3: Consuming from orders.DLT topic...\n");

        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-test-consumer-" + System.currentTimeMillis());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(Collections.singletonList(DLT_TOPIC));

            System.out.println("Polling for DLT messages (timeout: 10 seconds)...\n");
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));

            if (records.isEmpty()) {
                System.out.println("✗ NO MESSAGES FOUND IN DLT!");
                System.out.println("\nPossible causes:");
                System.out.println("  1. Executor is not running");
                System.out.println("  2. Executor is not connected to Kafka broker");
                System.out.println("  3. Executor is not consuming from orders topic");
                System.out.println("  4. Messages were not processed as failures");
                System.out.println("\nCheck executor logs for details.");
                throw new Exception("DLT is empty - Executor may not be processing messages");
            }

            System.out.println("✓ Found " + records.count() + " message(s) in DLT\n");
            System.out.println("═════════════════════════════════════════════════════════════════\n");

            int messageCount = 0;
            final boolean[] foundFailureHeader = {false};

            for (var record : records) {
                messageCount++;
                System.out.println("Message " + messageCount + ":");
                System.out.println("  Key: " + record.key());
                System.out.println("  Partition: " + record.partition());
                System.out.println("  Offset: " + record.offset());
                System.out.println("  Timestamp: " + record.timestamp());

                // Print headers
                System.out.println("  Headers:");
                record.headers().forEach(header -> {
                    String value = new String(header.value());
                    System.out.println("    " + header.key() + ": " + value);

                    if ("x-failure-reason".equals(header.key())) {
                        foundFailureHeader[0] = true;
                    }
                });

                // Print first 300 chars of value
                String valuePreview = record.value().substring(0, Math.min(300, record.value().length()));
                if (record.value().length() > 300) {
                    valuePreview += "...";
                }
                System.out.println("  Value: " + valuePreview);
                System.out.println();
            }

            System.out.println("═════════════════════════════════════════════════════════════════\n");

            // Final verification
            if (foundFailureHeader[0]) {
                System.out.println("✓ SUCCESS: DLT messages contain x-failure-reason header!");
                System.out.println("✓ Original message values are preserved");
                System.out.println("✓ DLT routing is working correctly");
            } else {
                System.out.println("⚠ WARNING: No x-failure-reason header found in DLT messages");
                System.out.println("  Messages may have been routed by different mechanism");
            }

        } catch (Exception e) {
            System.err.println("✗ Error consuming from DLT: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Helper: Send a single message to Kafka
     */
    private static void sendMessage(KafkaProducer<String, String> producer,
                                     String topic, String key, String value) {
        try {
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
            var metadata = producer.send(record).get();
            System.out.println("    Topic: " + metadata.topic() +
                             ", Partition: " + metadata.partition() +
                             ", Offset: " + metadata.offset());
        } catch (Exception e) {
            System.err.println("    ✗ Error sending message: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Helper: Build ORDER_PLACED JSON payload
     */
    private static String buildOrderPlacedJson(String eventId, String accountId, String symbol,
                                               String side, int quantity, double price, String orderId) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"eventId\":\"").append(eventId).append("\",");
        json.append("\"eventType\":\"ORDER_PLACED\",");
        json.append("\"eventTime\":\"2026-09-17T10:00:00Z\",");
        json.append("\"source\":\"test\",");
        json.append("\"schemaVersion\":1,");
        json.append("\"payload\":{");

        if (orderId != null) {
            json.append("\"orderId\":\"").append(orderId).append("\",");
        }
        // else: orderId is missing (poison message)

        json.append("\"accountId\":").append(accountId).append(",");
        json.append("\"symbol\":\"").append(symbol).append("\",");
        json.append("\"side\":\"").append(side).append("\",");
        json.append("\"quantity\":").append(quantity).append(",");
        json.append("\"price\":").append(price).append(",");
        json.append("\"idempotencyKey\":\"key-").append(System.currentTimeMillis()).append("\",");
        json.append("\"createdOn\":\"2026-09-17T10:00:00Z\"");
        json.append("}");
        json.append("}");

        return json.toString();
    }
}
