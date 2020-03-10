package io.opentracing.contrib.specialagent;

import static java.lang.Thread.*;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThreadAncestry {
  private static final InheritableThreadLocal<Long> parentThreadId = new InheritableThreadLocal<Long>() {
    @Override
    protected Long childValue(final Long parentValue) {
      final Thread thread = currentThread();
      return thread.getId(); // This is called by the parent thread.
    }
  };

  static void spawnRecursively(final int remainingSpawns) {
    System.out.println("The ancestors of " + currentThread().getId() + " are " + parentThreadId.get());
    if (remainingSpawns > 0)
      new Thread() {
        @Override
        public void run() {
          spawnRecursively(remainingSpawns - 1);
        }
      }.start();
  }

  public static void main(final String[] args) {
    spawnRecursively(3);

//    for (int i = 0; i < 6; ++i) {
//      final int x = i;
//      new Thread() {
//        @Override
//        public void run() {
//          System.out.println(x + " ran on " + currentThread().getName() + " with ancestors " + ancestors.get());
//        }
//      }.start();
//    }

    final ExecutorService service = Executors.newSingleThreadExecutor();
    service.submit(new Runnable() {
      @Override
      public void run() {
        System.out.println(currentThread().getName() + " has ancestors " + parentThreadId.get() + "; it will now attempt to kill these.");
        System.gc(); // May not work on all systems.
        System.out.println(currentThread().getName() + " now has ancestors " + parentThreadId.get() + " after attempting to force GC.");
        service.shutdown();
      }
    });
  }
}