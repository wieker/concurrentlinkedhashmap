package com.googlecode.concurrentlinkedhashmap;

import static com.google.common.collect.Lists.newArrayListWithCapacity;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * A testing harness for concurrency related executions.
 * <p/>
 * This harness will ensure that all threads execute at the same time, records
 * the full execution time, and optionally retrieves the responses from each
 * thread. This harness can be used for performance tests, investigations of
 * lock contention, etc.
 * <p/>
 * This code was adapted from <tt>Java Concurrency in Practice</tt>, using an
 * example of a {@link CountDownLatch} for starting and stopping threads in
 * timing tests.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class ConcurrentTestHarness {

  private ConcurrentTestHarness() {
    throw new IllegalStateException("Cannot instantiate static class");
  }

  /**
   * Executes a task, on N threads, all starting at the same time.
   *
   * @param nThreads the number of threads to execute
   * @param task the task to execute in each thread
   * @return the execution time for all threads to complete, in nanoseconds
   */
  public static long timeTasks(int nThreads, Runnable task) throws InterruptedException {
    return timeTasks(nThreads, task, "Thread");
  }

  /**
   * Executes a task, on N threads, all starting at the same time.
   *
   * @param nThreads the number of threads to execute
   * @param task the task to execute in each thread
   * @param baseThreadName the base name for each thread in this task set
   * @return the execution time for all threads to complete, in nanoseconds
   */
  public static long timeTasks(int nThreads, Runnable task, String baseThreadName)
      throws InterruptedException {
    return timeTasks(nThreads, Executors.callable(task), baseThreadName).getExecutionTime();
  }

  /**
   * Executes a task, on N threads, all starting at the same time.
   *
   * @param nThreads the number of threads to execute
   * @param task the task to execute in each thread
   * @return the result of each task and the full execution time, in nanoseconds
   */
  public static <T> TestResult<T> timeTasks(int nThreads, Callable<T> task)
      throws InterruptedException {
    return timeTasks(nThreads, task, "Thread");
  }

  /**
   * Executes a task, on N threads, all starting at the same time.
   *
   * @param nThreads the number of threads to execute
   * @param task the task to execute in each thread
   * @param baseThreadName the base name for each thread in this task set
   * @return the result of each task and the full execution time, in
   *     nanoseconds
   */
  @SuppressWarnings("deprecation")
  public static <T> TestResult<T> timeTasks(int nThreads, final Callable<T> task,
      final String baseThreadName)
      throws InterruptedException {
    final CountDownLatch startGate = new CountDownLatch(1);
    final CountDownLatch endGate = new CountDownLatch(nThreads);
    final AtomicReferenceArray<T> results = new AtomicReferenceArray<T>(nThreads);

    List<Thread> threads = newArrayListWithCapacity(nThreads);
    for (int i = 0; i < nThreads; i++) {
      final int index = i;
      Thread thread = new Thread(baseThreadName + "-" + i) {
        @Override public void run() {
          try {
            startGate.await();
            try {
              results.set(index, task.call());
            } finally {
              endGate.countDown();
            }
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        }
      };
      thread.setDaemon(true);
      thread.start();
      threads.add(thread);
    }

    long start = System.nanoTime();
    startGate.countDown();
    try {
      endGate.await();
    } finally {
      for (Thread thread : threads) {
        thread.stop();
      }
    }
    long end = System.nanoTime();
    return new TestResult<T>(end - start, toList(results));
  }

  /**
   * Migrates the data from the atomic array to a {@link List} for easier
   * consumption.
   *
   * @param data the per-thread results from the test
   * @return the per-thread results as a standard collection
   */
  private static <T> List<T> toList(AtomicReferenceArray<T> data) {
    List<T> list = newArrayListWithCapacity(data.length());
    for (int i = 0; i < data.length(); i++) {
      list.add(data.get(i));
    }
    return list;
  }

  /**
   * The results of the test harness's execution.
   *
   * @param <T> the data type produced by the task
   */
  public static final class TestResult<T> {
    private final long executionTime;
    private final List<T> results;

    public TestResult(long executionTime, List<T> results) {
      this.executionTime = executionTime;
      this.results = results;
    }

    /**
     * The test's execution time, in nanoseconds.
     *
     * @return The time to complete the test.
     */
    public long getExecutionTime() {
      return executionTime;
    }

    /**
     * The results from executing the tasks.
     *
     * @return The outputs from the tasks.
     */
    public List<T> getResults() {
      return results;
    }
  }
}
