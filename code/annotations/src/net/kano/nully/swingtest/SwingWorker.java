
package net.kano.nully.swingtest;

import javax.swing.SwingUtilities;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public abstract class SwingWorker<V> {
    private final Future<V> future;

    public SwingWorker() {
        this(Executors.newSingleThreadExecutor());
    }

    public SwingWorker(ExecutorService executor) {
        Callable<V> callable = new Callable<V>() {
            public V call() throws Exception {
                // compute the value
                final V value = computeValue();

                // and update the UI with this value
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        updateUI(value);
                    }
                });

                // and return the value so it's available through the Future
                return value;
            }
        };

        // run the computation in the executor thread
        future = executor.submit(callable);
    }

    public void interrupt() { future.cancel(true); }

    public final Future<V> getFutureValue() { return future; }

    protected abstract V computeValue();

    protected abstract void updateUI(V value);
}
