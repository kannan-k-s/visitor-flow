package ai.visitorflow.demo.data.transaction;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
class PostCommitExecutorImpl implements PostCommitExecutor {
  @Override
  public void execute(Runnable action) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()
      || !TransactionSynchronizationManager.isSynchronizationActive()) {
      action.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        action.run();
      }
    });
  }
}
