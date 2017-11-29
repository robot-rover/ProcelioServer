package procul.studios;

import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.function.Function;

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
            task.run();
        }
    }

    public <V> Future<V> addOperation(Function<DSLContext, V> task){
        FutureTask<V> future = new FutureTask<V>(() -> task.apply(context));
        queue.add(future);
        return future;
    }

    public void addOperation(Consumer<DSLContext> task){
        FutureTask future = new FutureTask<Void>(() -> task.accept(context), null);
        queue.add(future);
    }
}
