package com.xiaomi.mone.log.agent.channel.wildcard.listen;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadFactory;

/**
 * @author wtt
 * @version 1.0
 * @description
 * @date 2023/5/16 15:40
 */
public class FileChangeMonitor implements Runnable {
    private final long interval;
    private Thread thread = null;
    private ThreadFactory threadFactory;
    private volatile boolean running = false;
    private List<FileChangeObserver> observers = new ArrayList<>();


    public FileChangeMonitor() {
        this(10000);
    }

    public FileChangeMonitor(long interval) {
        this.interval = interval;
    }

    public long getInterval() {
        return interval;
    }

    public synchronized void setThreadFactory(ThreadFactory threadFactory) {
        this.threadFactory = threadFactory;
    }

    public synchronized void stop() throws Exception {
        stop(interval);
    }

    public void addObserver(final FileChangeObserver observer) {
        if (observer != null) {
            observers.add(observer);
        }
    }

    /**
     * Remove a file system observer from this monitor.
     *
     * @param observer The file system observer to remove
     */
    public void removeObserver(final FileChangeObserver observer) {
        if (observer != null) {
            while (observers.remove(observer)) {
            }
        }
    }

    public synchronized void stop(long stopInterval) throws Exception {
        if (running == false) {
            throw new IllegalStateException("Monitor is not running");
        }
        running = false;
        try {
            thread.join(stopInterval);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public synchronized void start() throws Exception {
        if (running) {
            throw new IllegalStateException("Monitor is already running");
        }
        running = true;
        if (threadFactory != null) {
            thread = threadFactory.newThread(this);
        } else {
            thread = new Thread(this);
        }
        thread.start();
    }

    /**
     * Run.
     */
    public void run() {
        while (running) {
            for (FileChangeObserver observer : observers) {
                observer.checkAndNotify();
            }
            if (!running) {
                break;
            }
            try {
                Thread.sleep(interval);
            } catch (final InterruptedException ignored) {
            }
        }
    }
}
