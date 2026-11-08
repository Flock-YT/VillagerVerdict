package cn.ericcraft.villagerverdict;

import static cn.ericcraft.villagerverdict.probe.ProbeController.*;
import static org.junit.jupiter.api.Assertions.*;

import cn.ericcraft.villagerverdict.probe.ProbeController;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;

class ProbeTest {
  AtomicLong time = new AtomicLong(10000);
  ProbeController p = new ProbeController(time::get);
  UUID a = new UUID(0, 1), b = new UUID(0, 2);

  void begin() {
    p.run();
    assertTrue(p.begin(1, "overworld", a, 5, Mode.AUTOMATIC));
  }

  @Test
  void disabledSendsNoRequest() {
    assertFalse(p.begin(1, "overworld", a, 5, Mode.AUTOMATIC));
    assertNull(p.context());
  }

  @Test
  void normalLifecycle() {
    begin();
    assertEquals(State.REQUESTED, p.state());
    assertTrue(p.open(3, true));
    assertTrue(p.offers(3));
    p.closing();
    p.complete(false);
    assertNotNull(p.context());
    p.complete(true);
    assertEquals(State.COMPLETE, p.state());
  }

  @Test
  void singleRequestOnly() {
    begin();
    assertFalse(p.begin(1, "overworld", b, 6, Mode.AUTOMATIC));
    assertEquals(State.CONFLICT, p.state());
    assertFalse(p.running());
  }

  @Test
  void offersNeedWindow() {
    begin();
    assertFalse(p.offers(3));
    assertEquals(State.CONFLICT, p.state());
  }

  @Test
  void mismatchedContainerStops() {
    begin();
    assertTrue(p.open(3, true));
    assertFalse(p.offers(4));
    assertFalse(p.running());
    assertNull(p.context());
  }

  @Test
  void unrelatedWindowStops() {
    begin();
    assertFalse(p.open(3, false));
    assertFalse(p.running());
  }

  @Test
  void lateResponseCannotCapture() {
    begin();
    time.addAndGet(5000);
    p.tick();
    assertEquals(State.TIMEOUT, p.state());
    assertFalse(p.offers(3));
  }

  @Test
  void cancelledResponseCannotCapture() {
    begin();
    p.cancel(State.CANCELLED);
    assertFalse(p.open(3, true));
    assertFalse(p.offers(3));
  }

  @Test
  void manualWorksWhileDisabled() {
    assertTrue(p.begin(1, "overworld", a, 5, Mode.MANUAL));
    assertTrue(p.open(3, true));
    assertTrue(p.offers(3));
  }

  @Test
  void cooldownAfterSuccess() {
    begin();
    p.open(3, true);
    p.offers(3);
    p.closing();
    p.complete(true);
    time.addAndGet(2000);
    assertFalse(p.eligible(a));
    assertTrue(p.eligible(b));
    time.addAndGet(28000);
    assertTrue(p.eligible(a));
  }

  @Test
  void failuresBackOff() {
    begin();
    time.addAndGet(5000);
    p.tick();
    assertEquals(time.get() + 30000, p.retryAt(a));
    time.set(p.retryAt(a));
    assertTrue(p.begin(1, "overworld", a, 5, Mode.AUTOMATIC));
    time.addAndGet(5000);
    p.tick();
    assertEquals(time.get() + 60000, p.retryAt(a));
  }

  @Test
  void threeTimeoutsDisable() {
    p.run();
    for (int i = 0; i < 3; i++) {
      time.addAndGet(100000);
      assertTrue(p.begin(1, "overworld", a, 5, Mode.AUTOMATIC));
      time.addAndGet(5000);
      p.tick();
    }
    assertFalse(p.running());
    assertEquals(State.TIMEOUT, p.state());
  }

  @Test
  void restartingResetsTimeoutStreak() {
    p.run();
    for (int i = 0; i < 3; i++) {
      time.addAndGet(100000);
      assertTrue(p.begin(1, "overworld", a, 5, Mode.AUTOMATIC));
      time.addAndGet(5000);
      p.tick();
    }
    assertFalse(p.running());
    time.addAndGet(300000);
    p.run();
    assertTrue(p.begin(1, "overworld", a, 5, Mode.AUTOMATIC));
    time.addAndGet(5000);
    p.tick();
    assertTrue(p.running());
  }

  @Test
  void rejectionResetsTimeoutStreak() {
    p.run();
    for (int i = 0; i < 2; i++) {
      time.addAndGet(100000);
      assertTrue(p.begin(1, "overworld", a, 5, Mode.AUTOMATIC));
      time.addAndGet(5000);
      p.tick();
    }
    time.addAndGet(300000);
    assertTrue(p.begin(1, "overworld", a, 5, Mode.AUTOMATIC));
    p.rejected();
    for (int i = 0; i < 2; i++) {
      time.addAndGet(300000);
      assertTrue(p.begin(1, "overworld", a, 5, Mode.AUTOMATIC));
      time.addAndGet(5000);
      p.tick();
    }
    assertTrue(p.running());
  }

  @Test
  void pauseCancels() {
    begin();
    p.pause();
    assertFalse(p.running());
    assertNull(p.context());
    assertEquals(State.CANCELLED, p.state());
  }

  @Test
  void worldResetDropsEntityIds() {
    begin();
    p.reset();
    assertNull(p.context());
    assertEquals(State.IDLE, p.state());
    assertFalse(p.running());
  }

  @Test
  void ambiguousManualInteractionQuarantines() {
    begin();
    assertFalse(p.begin(1, "overworld", b, 6, Mode.MANUAL));
    assertFalse(p.begin(1, "overworld", b, 6, Mode.MANUAL));
    time.addAndGet(5000);
    assertTrue(p.begin(1, "overworld", b, 6, Mode.MANUAL));
  }

  @Test
  void rejectedIsDistinctFromTimeout() {
    begin();
    p.rejected();
    assertEquals(State.REJECTED, p.state());
  }

  @Test
  void closingNeedsLocalRestoration() {
    begin();
    p.open(3, true);
    p.offers(3);
    p.complete(true);
    assertNotNull(p.context());
    p.closing();
    p.complete(false);
    assertNotNull(p.context());
    p.complete(true);
    assertNull(p.context());
  }

  @Test
  void timeoutDoesNotRequireServerCloseAck() {
    begin();
    time.addAndGet(5000);
    p.tick();
    assertEquals(State.TIMEOUT, p.state());
    assertEquals(1, p.failureCount(a));
  }
}
