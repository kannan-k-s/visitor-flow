package ai.visitorflow.demo.data.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class PostCommitExecutorImplTest {
  private final PostCommitExecutor executor = new PostCommitExecutorImpl();

  @AfterEach
  void tearDown() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
    TransactionSynchronizationManager.setActualTransactionActive(false);
  }

  @Test
  void defersActionUntilCommit() {
    beginTransaction();
    AtomicBoolean executed = new AtomicBoolean();

    executor.execute(() -> executed.set(true));

    assertThat(executed).isFalse();
    TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    assertThat(executed).isTrue();
  }

  @Test
  void doesNotExecuteActionAfterRollback() {
    beginTransaction();
    AtomicBoolean executed = new AtomicBoolean();

    executor.execute(() -> executed.set(true));
    TransactionSynchronizationManager.getSynchronizations().forEach(
      synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)
    );

    assertThat(executed).isFalse();
  }

  @Test
  void executesImmediatelyWithoutTransaction() {
    AtomicBoolean executed = new AtomicBoolean();

    executor.execute(() -> executed.set(true));

    assertThat(executed).isTrue();
  }

  private void beginTransaction() {
    TransactionSynchronizationManager.setActualTransactionActive(true);
    TransactionSynchronizationManager.initSynchronization();
  }
}
