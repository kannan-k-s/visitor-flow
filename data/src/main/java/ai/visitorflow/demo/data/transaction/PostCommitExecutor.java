package ai.visitorflow.demo.data.transaction;

public interface PostCommitExecutor {
  void execute(Runnable action);
}
