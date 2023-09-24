import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

/**
 * Banking API Service
 */
public class Application {
    private static final String SUSPICIOUS_TRANSACTIONS_TOPIC = "suspicious-transactions";
    private static final String VALID_TRANSACTIONS_TOPIC = "valid-transactions";

    private static final String BOOTSTRAP_SERVERS = "localhost:9092,localhost:9093,localhost:9094";

    public static void main(String[] args) {
        Producer<String, Transaction> kafkaProducer = createKafkaProducer(BOOTSTRAP_SERVERS);

        try {
            processTransactions(new IncomingTransactionsReader(), new UserResidenceDatabase(), kafkaProducer);
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        } finally {
            kafkaProducer.flush();
            kafkaProducer.close();
        }
    }

    public static void processTransactions(IncomingTransactionsReader incomingTransactionsReader,
                                           UserResidenceDatabase userResidenceDatabase,
                                           Producer<String, Transaction> kafkaProducer) throws ExecutionException, InterruptedException {

        while (incomingTransactionsReader.hasNext()) {
            Transaction transaction = incomingTransactionsReader.next();

            String user = transaction.getUser();

            String userResidence = userResidenceDatabase.getUserResidence(user);
            if (Objects.equals(userResidence, transaction.getTransactionLocation())) {
                ProducerRecord<String, Transaction> validRecord = new ProducerRecord<>(VALID_TRANSACTIONS_TOPIC, user, transaction);
                kafkaProducer.send(validRecord);
            } else {
                ProducerRecord<String, Transaction> suspiciousRecord = new ProducerRecord<>(SUSPICIOUS_TRANSACTIONS_TOPIC, user, transaction);
                kafkaProducer.send(suspiciousRecord);
            }
        }
    }

    public static Producer<String, Transaction> createKafkaProducer(String bootstrapServers) {
        Properties properties = new Properties();

        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.CLIENT_ID_CONFIG, "banking-api-service");
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,  Transaction.TransactionSerializer.class.getName());

        return new KafkaProducer<>(properties);
    }

}
