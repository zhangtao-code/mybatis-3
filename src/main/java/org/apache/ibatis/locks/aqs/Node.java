package org.apache.ibatis.locks.aqs;

import sun.misc.Unsafe;

import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * 对当前的节点进行cas 操作
 */
public class Node {
  public static final Node SHARED = new Node();
  public static final Node EXCLUSIVE = null;


  public static final int CANCELLED = 1;
  public static final int SIGNAL = -1;
  public static final int CONDITION = -2;
  public static final int PROPAGATE = -3;

  /**
   * unSafe相关
   */
  public static final Unsafe unsafe = Unsafe.getUnsafe();


  public static final long nextOffset;
  public static final long waitStatusOffset;

  static {
    try {

      nextOffset = unsafe.objectFieldOffset(Node.class.getDeclaredField("next"));
      waitStatusOffset = unsafe.objectFieldOffset(Node.class.getDeclaredField("waitStatus"));
    } catch (Exception e) {
      throw new Error(e);
    }
  }


  public Node() {
  }

  public Node(Thread thread, Node nextWaiter) {
    this.thread = thread;
    this.nextWaiter = nextWaiter;
  }

  public Node(Thread thread, int waitStatus) {
    this.waitStatus = waitStatus;
    this.thread = thread;
  }

  /**
   * 基础变量
   */
  public volatile int waitStatus;

  public volatile Node prev;

  public volatile Node next;

  public volatile Thread thread;

  public Node nextWaiter;




  boolean isShared() {
    return nextWaiter == SHARED;
  }

  public Node predecessor() {
    if (prev == null) {
      throw new NullPointerException();
    }
    return prev;
  }






  /**
   * CAS waitStatus field of a node.
   */
  public static final boolean compareAndSetWaitStatus(Node node,
                                                       int expect,
                                                       int update) {
    return unsafe.compareAndSwapInt(node, waitStatusOffset,
      expect, update);
  }

  /**
   * CAS next field of a node.
   */
  public static final boolean compareAndSetNext(Node node,
                                                 Node expect,
                                                 Node update) {
    return unsafe.compareAndSwapObject(node, nextOffset, expect, update);
  }
}
