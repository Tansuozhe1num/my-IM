package com.easychat.entity.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

@Component
public class ThreadPoolConfig {

    private static final Logger log = LoggerFactory.getLogger(ThreadPoolConfig.class);

    /**
     * 默认线程池
     */
    public static final String DEFAULT_POOL = "default";

    public static final String MESSAGE_SOLVE_POOL = "message_solve";

    /**
     * 所有线程池
     */
    private final Map<String, ManagedPool> pools = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        register(DEFAULT_POOL,
                PoolSpec.builder()
                        .corePoolSize(8)
                        .maximumPoolSize(32)
                        .queueCapacity(1000)
                        .keepAliveSeconds(60)
                        .fallbackCorePoolSize(2)
                        .fallbackMaximumPoolSize(8)
                        .fallbackQueueCapacity(100)
                        .build()
        );

        register(MESSAGE_SOLVE_POOL,
                PoolSpec.builder()
                        .corePoolSize(8)
                        .maximumPoolSize(32)
                        .queueCapacity(1000)
                        .keepAliveSeconds(60)
                        .fallbackCorePoolSize(2)
                        .fallbackMaximumPoolSize(8)
                        .fallbackQueueCapacity(100)
                        .build());

        log.info("thread pool initialized");
    }

    /**
     * 注册线程池
     */
    public void register(String name, PoolSpec spec) {
        Objects.requireNonNull(name, "pool name cannot be null");
        Objects.requireNonNull(spec, "pool spec cannot be null");

        ManagedPool newPool = new ManagedPool(name, spec);

        ManagedPool oldPool = pools.putIfAbsent(name, newPool);
        if (oldPool != null) {
            newPool.shutdown();
            throw new IllegalStateException("thread pool already exists: " + name);
        }

        log.info(
                "register thread pool, name={}, core={}, max={}, queue={}, fallbackCore={}, fallbackMax={}, fallbackQueue={}",
                name,
                spec.getCorePoolSize(),
                spec.getMaximumPoolSize(),
                spec.getQueueCapacity(),
                spec.getFallbackCorePoolSize(),
                spec.getFallbackMaximumPoolSize(),
                spec.getFallbackQueueCapacity()
        );
    }

    /**
     * 获取线程池
     */
    public ExecutorService getExecutor(String name) {
        ManagedPool pool = pools.get(name);
        if (pool == null) {
            throw new IllegalArgumentException("thread pool not found: " + name);
        }
        return pool.getPrimary();
    }

    /**
     * 默认线程池执行任务
     */
    public void execute(Runnable task) {
        execute(DEFAULT_POOL, task);
    }

    /**
     * 指定线程池执行任务
     */
    public void execute(String poolName, Runnable task) {
        Objects.requireNonNull(task, "task cannot be null");

        ManagedPool pool = getPool(poolName);

        Runnable safeTask = wrap(task, poolName);

        /**
         * 第一层：正常线程池
         */
        try {
            pool.getPrimary().execute(safeTask);
            return;
        } catch (RejectedExecutionException e) {
            pool.primaryRejected.increment();
        }

        /**
         * 第二层：应急线程池
         */
        try {
            pool.getFallback().execute(safeTask);
            return;
        } catch (RejectedExecutionException e) {
            pool.fallbackRejected.increment();
        }

        /**
         * 第三层：调用方执行
         *
         * 最终兜底。
         * 不让任务因为线程池满直接丢失。
         */
        pool.callerRuns.increment();

        log.warn("all thread pools are full, caller runs task, pool={}", poolName);

        safeTask.run();
    }

    /**
     * submit Callable
     */
    public <T> Future<T> submit(String poolName, Callable<T> task) {
        Objects.requireNonNull(task, "task cannot be null");

        ManagedPool pool = getPool(poolName);

        FutureTask<T> futureTask = new FutureTask<>(
                wrap(task, poolName)
        );

        /**
         * 第一层
         */
        try {
            pool.getPrimary().execute(futureTask);
            return futureTask;
        } catch (RejectedExecutionException e) {
            pool.primaryRejected.increment();
        }

        /**
         * 第二层
         */
        try {
            pool.getFallback().execute(futureTask);
            return futureTask;
        } catch (RejectedExecutionException e) {
            pool.fallbackRejected.increment();
        }

        /**
         * 第三层
         */
        pool.callerRuns.increment();

        log.warn(
                "all thread pools are full, caller runs submit task, pool={}",
                poolName
        );

        futureTask.run();

        return futureTask;
    }

    /**
     * submit Runnable
     */
    public Future<?> submit(String poolName, Runnable task) {
        return submit(
                poolName,
                ExecutorsHelper.callable(task, null)
        );
    }

    /**
     * 动态修改核心线程数
     */
    public void resize(String poolName, int corePoolSize, int maximumPoolSize) {
        ManagedPool pool = getPool(poolName);

        if (corePoolSize <= 0) {
            throw new IllegalArgumentException("corePoolSize must > 0");
        }

        if (maximumPoolSize < corePoolSize) {
            throw new IllegalArgumentException(
                    "maximumPoolSize must >= corePoolSize"
            );
        }

        pool.resize(corePoolSize, maximumPoolSize);
    }

    /**
     * 获取线程池状态
     */
    public PoolStats stats(String poolName) {
        ManagedPool pool = getPool(poolName);

        ThreadPoolExecutor primary = pool.getPrimary();
        ThreadPoolExecutor fallback = pool.getFallback();

        return new PoolStats(
                poolName,

                primary.getCorePoolSize(),
                primary.getMaximumPoolSize(),
                primary.getPoolSize(),
                primary.getActiveCount(),
                primary.getQueue().size(),
                primary.getQueue().remainingCapacity(),
                primary.getCompletedTaskCount(),
                primary.getTaskCount(),

                fallback.getPoolSize(),
                fallback.getActiveCount(),
                fallback.getQueue().size(),

                pool.primaryRejected.sum(),
                pool.fallbackRejected.sum(),
                pool.callerRuns.sum()
        );
    }

    /**
     * 获取线程池
     */
    private ManagedPool getPool(String name) {
        ManagedPool pool = pools.get(name);

        if (pool == null) {
            throw new IllegalArgumentException(
                    "thread pool not found: " + name
            );
        }

        return pool;
    }

    /**
     * Runnable 异常隔离
     */
    private Runnable wrap(Runnable task, String poolName) {
        return () -> {
            try {
                task.run();
            } catch (Exception e) {
                log.error(
                        "thread pool task execute failed, pool={}",
                        poolName,
                        e
                );
            }
        };
    }

    /**
     * Callable 异常隔离
     */
    private <T> Callable<T> wrap(
            Callable<T> task,
            String poolName
    ) {
        return () -> {
            try {
                return task.call();
            } catch (Exception e) {
                log.error(
                        "thread pool callable execute failed, pool={}",
                        poolName,
                        e
                );
                throw e;
            }
        };
    }

    /**
     * Spring 容器关闭时优雅关闭
     */
    @PreDestroy
    public void destroy() {

        log.info(
                "start shutting down {} thread pools",
                pools.size()
        );

        for (ManagedPool pool : pools.values()) {
            pool.shutdown();
        }

        log.info("all thread pools shutdown");
    }

    /**
     * 内部线程池
     */
    private static class ManagedPool {

        private final String name;

        /**
         * 主线程池
         */
        private final ThreadPoolExecutor primary;

        /**
         * 应急线程池
         */
        private final ThreadPoolExecutor fallback;

        /**
         * 主线程池拒绝次数
         */
        private final LongAdder primaryRejected = new LongAdder();

        /**
         * 应急线程池拒绝次数
         */
        private final LongAdder fallbackRejected = new LongAdder();

        /**
         * caller-runs 次数
         */
        private final LongAdder callerRuns = new LongAdder();

        ManagedPool(
                String name,
                PoolSpec spec
        ) {
            this.name = name;

            this.primary = new ThreadPoolExecutor(
                    spec.getCorePoolSize(),
                    spec.getMaximumPoolSize(),
                    spec.getKeepAliveSeconds(),
                    TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(
                            spec.getQueueCapacity()
                    ),
                    new NamedThreadFactory(
                            name + "-primary"
                    ),
                    new ThreadPoolExecutor.AbortPolicy()
            );

            this.fallback = new ThreadPoolExecutor(
                    spec.getFallbackCorePoolSize(),
                    spec.getFallbackMaximumPoolSize(),
                    spec.getKeepAliveSeconds(),
                    TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(
                            spec.getFallbackQueueCapacity()
                    ),
                    new NamedThreadFactory(
                            name + "-fallback"
                    ),
                    new ThreadPoolExecutor.AbortPolicy()
            );

            /**
             * 允许核心线程超时
             */
            this.primary.allowCoreThreadTimeOut(false);
            this.fallback.allowCoreThreadTimeOut(true);
        }

        ThreadPoolExecutor getPrimary() {
            return primary;
        }

        ThreadPoolExecutor getFallback() {
            return fallback;
        }

        /**
         * 动态调整线程数量
         */
        synchronized void resize(
                int newCore,
                int newMax
        ) {

            int oldCore = primary.getCorePoolSize();
            int oldMax = primary.getMaximumPoolSize();

            /**
             * 扩容 max
             */
            if (newMax > oldMax) {
                primary.setMaximumPoolSize(newMax);
            }

            /**
             * 修改 core
             */
            if (newCore != oldCore) {
                primary.setCorePoolSize(newCore);
            }

            /**
             * 缩容 max
             */
            if (newMax < oldMax) {
                primary.setMaximumPoolSize(newMax);
            }

            log.info(
                    "resize thread pool, name={}, core {} -> {}, max {} -> {}",
                    name,
                    oldCore,
                    newCore,
                    oldMax,
                    newMax
            );
        }

        /**
         * 优雅关闭
         */
        void shutdown() {

            shutdownExecutor(primary, name + "-primary");
            shutdownExecutor(fallback, name + "-fallback");
        }

        private void shutdownExecutor(
                ExecutorService executor,
                String executorName
        ) {
            executor.shutdown();

            try {
                if (!executor.awaitTermination(
                        30,
                        TimeUnit.SECONDS
                )) {
                    log.warn(
                            "thread pool graceful shutdown timeout, force shutdown, executor={}",
                            executorName
                    );

                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();

                log.warn(
                        "thread pool shutdown interrupted, executor={}",
                        executorName
                );
            }
        }
    }

    /**
     * 自定义线程工厂
     */
    private static class NamedThreadFactory
            implements ThreadFactory {

        private final String prefix;

        private final AtomicInteger counter =
                new AtomicInteger(0);

        NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable runnable) {

            Thread thread = new Thread(
                    runnable,
                    prefix + "-" + counter.incrementAndGet()
            );

            thread.setDaemon(false);

            thread.setUncaughtExceptionHandler(
                    (t, e) ->
                            log.error(
                                    "uncaught exception in thread {}",
                                    t.getName(),
                                    e
                            )
            );

            return thread;
        }
    }

    /**
     * 配置
     */
    public static class PoolSpec {

        private int corePoolSize;

        private int maximumPoolSize;

        private int queueCapacity;

        private long keepAliveSeconds;

        private int fallbackCorePoolSize;

        private int fallbackMaximumPoolSize;

        private int fallbackQueueCapacity;

        public static Builder builder() {
            return new Builder();
        }

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public int getMaximumPoolSize() {
            return maximumPoolSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public long getKeepAliveSeconds() {
            return keepAliveSeconds;
        }

        public int getFallbackCorePoolSize() {
            return fallbackCorePoolSize;
        }

        public int getFallbackMaximumPoolSize() {
            return fallbackMaximumPoolSize;
        }

        public int getFallbackQueueCapacity() {
            return fallbackQueueCapacity;
        }

        public static class Builder {

            private final PoolSpec spec = new PoolSpec();

            public Builder corePoolSize(int value) {
                spec.corePoolSize = value;
                return this;
            }

            public Builder maximumPoolSize(int value) {
                spec.maximumPoolSize = value;
                return this;
            }

            public Builder queueCapacity(int value) {
                spec.queueCapacity = value;
                return this;
            }

            public Builder keepAliveSeconds(long value) {
                spec.keepAliveSeconds = value;
                return this;
            }

            public Builder fallbackCorePoolSize(int value) {
                spec.fallbackCorePoolSize = value;
                return this;
            }

            public Builder fallbackMaximumPoolSize(int value) {
                spec.fallbackMaximumPoolSize = value;
                return this;
            }

            public Builder fallbackQueueCapacity(int value) {
                spec.fallbackQueueCapacity = value;
                return this;
            }

            public PoolSpec build() {

                if (spec.corePoolSize <= 0) {
                    throw new IllegalArgumentException(
                            "corePoolSize must > 0"
                    );
                }

                if (spec.maximumPoolSize < spec.corePoolSize) {
                    throw new IllegalArgumentException(
                            "maximumPoolSize must >= corePoolSize"
                    );
                }

                if (spec.queueCapacity <= 0) {
                    throw new IllegalArgumentException(
                            "queueCapacity must > 0"
                    );
                }

                if (spec.fallbackCorePoolSize <= 0) {
                    throw new IllegalArgumentException(
                            "fallbackCorePoolSize must > 0"
                    );
                }

                if (spec.fallbackMaximumPoolSize
                        < spec.fallbackCorePoolSize) {
                    throw new IllegalArgumentException(
                            "fallbackMaximumPoolSize must >= fallbackCorePoolSize"
                    );
                }

                if (spec.fallbackQueueCapacity <= 0) {
                    throw new IllegalArgumentException(
                            "fallbackQueueCapacity must > 0"
                    );
                }

                return spec;
            }
        }
    }

    /**
     * 线程池监控数据
     */
    public static class PoolStats {

        private final String name;

        private final int corePoolSize;
        private final int maximumPoolSize;
        private final int poolSize;
        private final int activeCount;
        private final int queueSize;
        private final int queueRemaining;
        private final long completedTaskCount;
        private final long taskCount;

        private final int fallbackPoolSize;
        private final int fallbackActiveCount;
        private final int fallbackQueueSize;

        private final long primaryRejected;
        private final long fallbackRejected;
        private final long callerRuns;

        public PoolStats(
                String name,
                int corePoolSize,
                int maximumPoolSize,
                int poolSize,
                int activeCount,
                int queueSize,
                int queueRemaining,
                long completedTaskCount,
                long taskCount,
                int fallbackPoolSize,
                int fallbackActiveCount,
                int fallbackQueueSize,
                long primaryRejected,
                long fallbackRejected,
                long callerRuns
        ) {
            this.name = name;
            this.corePoolSize = corePoolSize;
            this.maximumPoolSize = maximumPoolSize;
            this.poolSize = poolSize;
            this.activeCount = activeCount;
            this.queueSize = queueSize;
            this.queueRemaining = queueRemaining;
            this.completedTaskCount = completedTaskCount;
            this.taskCount = taskCount;
            this.fallbackPoolSize = fallbackPoolSize;
            this.fallbackActiveCount = fallbackActiveCount;
            this.fallbackQueueSize = fallbackQueueSize;
            this.primaryRejected = primaryRejected;
            this.fallbackRejected = fallbackRejected;
            this.callerRuns = callerRuns;
        }

        public String getName() {
            return name;
        }

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public int getMaximumPoolSize() {
            return maximumPoolSize;
        }

        public int getPoolSize() {
            return poolSize;
        }

        public int getActiveCount() {
            return activeCount;
        }

        public int getQueueSize() {
            return queueSize;
        }

        public int getQueueRemaining() {
            return queueRemaining;
        }

        public long getCompletedTaskCount() {
            return completedTaskCount;
        }

        public long getTaskCount() {
            return taskCount;
        }

        public int getFallbackPoolSize() {
            return fallbackPoolSize;
        }

        public int getFallbackActiveCount() {
            return fallbackActiveCount;
        }

        public int getFallbackQueueSize() {
            return fallbackQueueSize;
        }

        public long getPrimaryRejected() {
            return primaryRejected;
        }

        public long getFallbackRejected() {
            return fallbackRejected;
        }

        public long getCallerRuns() {
            return callerRuns;
        }

        @Override
        public String toString() {
            return "PoolStats{" +
                    "name='" + name + '\'' +
                    ", core=" + corePoolSize +
                    ", max=" + maximumPoolSize +
                    ", pool=" + poolSize +
                    ", active=" + activeCount +
                    ", queue=" + queueSize +
                    ", queueRemaining=" + queueRemaining +
                    ", completed=" + completedTaskCount +
                    ", taskCount=" + taskCount +
                    ", fallbackPool=" + fallbackPoolSize +
                    ", fallbackActive=" + fallbackActiveCount +
                    ", fallbackQueue=" + fallbackQueueSize +
                    ", primaryRejected=" + primaryRejected +
                    ", fallbackRejected=" + fallbackRejected +
                    ", callerRuns=" + callerRuns +
                    '}';
        }
    }

    /**
     * Runnable -> Callable
     */
    private static class ExecutorsHelper {

        static <T> Callable<T> callable(
                Runnable task,
                T result
        ) {
            return () -> {
                task.run();
                return result;
            };
        }
    }
}