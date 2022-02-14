package org.apache.ibatis.locks.aqs;

import org.apache.ibatis.locks.Condition;
import org.apache.ibatis.locks.LockSupport;

import java.io.Serializable;
import java.util.Date;
import java.util.concurrent.TimeUnit;


public class ConditionObject implements Condition, Serializable {
  public transient Node firstWaiter;
  public transient Node lastWaiter;
  public AbstractQueuedSynchronizer aqs;

  public ConditionObject() {

  }

  public Node addConditionWaiter() {
    Node t = lastWaiter;
    if (t != null && t.waitStatus != Node.CONDITION) {
      unlinkCancelledWaiters();
      t = lastWaiter;
    }
    Node node = new Node(Thread.currentThread(), Node.CONDITION);
    if (t == null) {
      firstWaiter = node;
    } else {
      t.nextWaiter = node;
    }
    lastWaiter = node;
    return node;
  }

  /**
   * 移除已经取消的链表节点
   */
  public void unlinkCancelledWaiters() {
    Node t = firstWaiter;
    Node trail = null;
    while (t != null) {
      Node next = t.nextWaiter;
      if (t.waitStatus != Node.CONDITION) {//如果当前出现了问题
        t.nextWaiter = null;//把他剥离出来
        if (trail == null) {//如果前置为空，则把next顶上
          trail = next;
        } else {//把前置的next处理调，相当于移除中间的节点
          trail.nextWaiter = next;
        }
        if (next == null) {
          lastWaiter = trail;
        }
      } else {
        trail = t;
      }
      t = next;
    }
  }

  public void signal() {
    if (false) {

    }
    Node first = firstWaiter;
    if (first != null) {
      doSignal(first);
    }
  }

  /**
   * 按照顺序，尝试唤醒链表中的节点
   * 有一说一，这个算法真的臭
   *
   * @param first
   */
  public void doSignal(Node first) {
    do {
      if ((firstWaiter = first.nextWaiter) == null) {
        lastWaiter = null;
      }
      first.nextWaiter = null;
    } while (aqs.transferForSignal(first) && (first = firstWaiter) != null);
  }

  public void doSignalAll(Node first) {
    lastWaiter = firstWaiter = null;
    do {
      Node next = first.nextWaiter;
      first.nextWaiter = null;
      aqs.transferForSignal(first);
      first = next;
    } while (first != null);
  }

  private static final int REINTERRUPT = 1;
  /**
   * Mode meaning to throw InterruptedException on exit from wait
   */
  private static final int THROW_IE = -1;

  private int checkInterruptWhileWaiting(Node node) {
    return Thread.interrupted() ?
      (transferAfterCancelledWait(node) ? THROW_IE : REINTERRUPT) :
      0;
  }

  /**
   * Throws InterruptedException, reinterrupts current thread, or
   * does nothing, depending on mode.
   */
  private void reportInterruptAfterWait(int interruptMode)
    throws InterruptedException {
    if (interruptMode == THROW_IE)
      throw new InterruptedException();
    else if (interruptMode == REINTERRUPT)
      AbstractQueuedSynchronizer.selfInterrupt();
  }

  public final void awaitUninterruptibly() {
    Node node = addConditionWaiter();//将当前线程加入队列中
    int saveState = aqs.fullyRelease(node);
    boolean interceptor = false;
    while (!aqs.isOnSyncQueue(node)) {
      LockSupport.park(this);
      if (Thread.interrupted()) {
        interceptor = true;
      }
    }
    if (aqs.acquireQueued(node, saveState) || interceptor) {
      AbstractQueuedSynchronizer.selfInterrupt();
    }
  }

  public void await() throws InterruptedException {
    if (Thread.interrupted()) {
      throw new InterruptedException();
    }
    Node node = addConditionWaiter();
    int savedState = aqs.fullyRelease(node);
    int interruptMode = 0;
    while (!aqs.isOnSyncQueue(node)) {
      java.util.concurrent.locks.LockSupport.park(this);
      if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
        break;
    }
    if (aqs.acquireQueued(node, savedState) && interruptMode != THROW_IE)
      interruptMode = REINTERRUPT;
    if (node.nextWaiter != null) // clean up if cancelled
      unlinkCancelledWaiters();
    if (interruptMode != 0)
      reportInterruptAfterWait(interruptMode);
  }

  @Override
  public long awaitNanos(long nanosTimeout) throws InterruptedException {
    return 0;
  }

  @Override
  public boolean await(long time, TimeUnit unit) throws InterruptedException {
    return false;
  }

  @Override
  public boolean awaitUntil(Date deadline) throws InterruptedException {
    return false;
  }

  @Override
  public void signalAll() {

  }
}
