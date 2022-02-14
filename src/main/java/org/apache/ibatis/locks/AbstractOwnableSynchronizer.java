package org.apache.ibatis.locks;

import java.io.Serializable;

public class AbstractOwnableSynchronizer implements Serializable {
  public AbstractOwnableSynchronizer() {
  }

  /**
   * 当前拥有的线程
   */
  private transient Thread exclusiveOwnerThread;

  public Thread getExclusiveOwnerThread() {
    return exclusiveOwnerThread;
  }

  public void setExclusiveOwnerThread(Thread exclusiveOwnerThread) {
    this.exclusiveOwnerThread = exclusiveOwnerThread;
  }
}
