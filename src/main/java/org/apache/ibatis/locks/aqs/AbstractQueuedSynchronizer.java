package org.apache.ibatis.locks.aqs;


import org.apache.ibatis.locks.AbstractOwnableSynchronizer;
import org.apache.ibatis.locks.LockSupport;
import sun.misc.Unsafe;

public class AbstractQueuedSynchronizer extends AbstractOwnableSynchronizer {

  public static final Unsafe unsafe = Unsafe.getUnsafe();
  public static final long spinForTimeoutThreshold = 1000L;
  public static final long stateOffset;
  public static final long headOffset;
  public static final long tailOffset;

  static {
    try {
      stateOffset = unsafe.objectFieldOffset(AbstractQueuedSynchronizer.class.getDeclaredField("state"));
      headOffset = unsafe.objectFieldOffset(AbstractQueuedSynchronizer.class.getDeclaredField("head"));
      tailOffset = unsafe.objectFieldOffset(AbstractQueuedSynchronizer.class.getDeclaredField("tail"));
    } catch (Exception e) {
      throw new Error(e);
    }
  }

  /**
   * CAS head field. Used only by enq.
   */
  public final boolean compareAndSetHead(Node update) {
    return unsafe.compareAndSwapObject(this, headOffset, null, update);
  }

  /**
   * CAS tail field. Used only by enq.
   */
  public final boolean compareAndSetTail(Node expect, Node update) {
    return unsafe.compareAndSwapObject(this, tailOffset, expect, update);
  }

  protected final boolean compareAndSetState(int expect, int update) {
    // See below for intrinsics setup to support this
    return unsafe.compareAndSwapInt(this, stateOffset, expect, update);
  }

  public transient volatile Node head;

  public transient volatile Node tail;

  public volatile int state;

  protected AbstractQueuedSynchronizer() {
  }

  public void setHead(Node head) {
    this.head = head;
  }

  public int getState() {
    return state;
  }

  public void setState(int state) {
    this.state = state;
  }

  /**
   * 将节点插入队尾,但是这样会虚拟出一个头节点??
   *
   * @param node
   * @return
   */
  public Node enq(final Node node) {
    while (true) {
      Node t = tail;
      if (t == null) {
        if (compareAndSetHead(new Node())) {
          tail = head;
        }
      } else {
        node.prev = t;
        if (compareAndSetTail(t, node)) {
          t.next = node;
          return t;
        }
      }
    }
  }

  /**
   * 首先尝试为末尾插入，如果失败，使用循环模式
   *
   * @param mode
   * @return
   */
  public Node addWaiter(Node mode) {
    Node node = new Node(Thread.currentThread(), mode);
    Node pred = tail;
    if (pred != null) {
      node.prev = pred;
      if (compareAndSetTail(pred, node)) {
        pred.next = node;
        return node;
      }
    }
    enq(node);
    return node;
  }

  /**
   * 从后往前找
   * 为什么不从前往后找??
   *
   * @param node
   */
  public void unparkSuccessor(Node node) {
    int ws = node.waitStatus;
    if (ws < 0) {
      Node.compareAndSetWaitStatus(node, ws, 0);//尝试更新状态
    }
    Node next = node.next;
    if (next == null || next.waitStatus > 0) {
      next = null;
      for (Node t = tail; t != null && t != node; t = t.prev) {
        if (t.waitStatus <= 0) {
          next = t;
        }
      }
    }
    if (next != null) {
      LockSupport.unpark(next.thread);
    }
  }

  public boolean releaseShared(int arg) {
    if (tryReleaseShared(arg)) {
      doReleaseShared();
      return true;
    }
    return false;
  }

  public boolean tryReleaseShared(int arg) {
    return false;
  }

  public void doReleaseShared() {
    while (true) {
      Node h = head;
      if (h != null && h != tail) {
        int ws = h.waitStatus;
        if (ws == Node.SIGNAL) {
          if (!Node.compareAndSetWaitStatus(h, Node.SIGNAL, 0)) {
            continue;
          }
          unparkSuccessor(h);
        } else if (ws == 0 && !(Node.compareAndSetWaitStatus(h, 0, Node.PROPAGATE))) {
          continue;
        }
      }
      if (h == head) {
        break;
      }
    }
  }

  public boolean transferForSignal(Node first) {
    return false;
  }

  public int fullyRelease(Node node) {
    return 0;
  }

  public boolean isOnSyncQueue(Node node) {
    return false;

  }

  public boolean acquireQueued(final Node node, int arg) {
    return false;
  }

  static void selfInterrupt() {
    Thread.currentThread().interrupt();
  }

  final boolean transferAfterCancelledWait(Node node) {
    if (Node.compareAndSetWaitStatus(node, Node.CONDITION, 0)) {
      enq(node);
      return true;
    }
    /*
     * If we lost out to a signal(), then we can't proceed
     * until it finishes its enq().  Cancelling during an
     * incomplete transfer is both rare and transient, so just
     * spin.
     */
    while (!isOnSyncQueue(node))
      Thread.yield();
    return false;
  }

  final boolean acquireQueued(final Node node, int arg) {
    boolean failed = true;
    try {
      boolean interrupted = false;
      for (; ; ) {
        final Node p = node.predecessor();
        if (p == head && tryAcquire(arg)) {
          setHead(node);
          p.next = null; // help GC
          failed = false;
          return interrupted;
        }
        if (shouldParkAfterFailedAcquire(p, node) &&
          parkAndCheckInterrupt())
          interrupted = true;
      }
    } finally {
      if (failed)
        cancelAcquire(node);
    }
  }
}
