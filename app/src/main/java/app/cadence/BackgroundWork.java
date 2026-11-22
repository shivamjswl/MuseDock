package app.cadence;
import java.util.concurrent.*;
/** Image and recommendation work yields CPU priority to controls and audio. */
final class BackgroundWork {
    static ExecutorService pool(int count,String name){return Executors.newFixedThreadPool(count,task->new Thread(()->{android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);task.run();},name));}
    private BackgroundWork(){}
}
