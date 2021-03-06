package procul.studios;

import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;

public class AtomicDatabase {
    private static Logger LOG = LoggerFactory.getLogger(AtomicDatabase.class);
    public final DSLContext context;
    public LinkedBlockingQueue<FutureTask> queue;
    public Thread runner;

    public AtomicDatabase(DSLContext context){
        this.context = context;
        queue = new LinkedBlockingQueue<>();
        runner = new Thread(this::processQueue);
        runner.setDaemon(true);
        runner.start();
    }

    private void processQueue(){
        FutureTask task;
        while(true){
            try {
                task = queue.take();
            } catch (InterruptedException e) {
                LOG.warn("Atomic Database queue processor interrupted", e);
                continue;
            }
            try {
                task.run();
            } catch (Exception e){
                LOG.error("Atomic Database process threw error", e);
                task.cancel(true);
            }

        }
    }

    public <V> Future<V> addOperation(Task<V> task){
        FutureTask<V> future = new FutureTask<>(() -> task.execute(context));
        queue.add(future);
        return future;
    }

    public void addOperation(VoidTask task){
        FutureTask future = new FutureTask<Void>(() -> {task.execute(context); return null;});
        queue.add(future);
    }

    @FunctionalInterface
    public interface Task<V> {
        V execute(DSLContext toUse) throws Exception;
    }

    @FunctionalInterface
    public interface VoidTask {
        void execute(DSLContext toUse) throws Exception;
    }
}
