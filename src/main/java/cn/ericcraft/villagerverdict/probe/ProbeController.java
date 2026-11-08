package cn.ericcraft.villagerverdict.probe;

import java.util.*;
import java.util.function.LongSupplier;

/**
 * No Minecraft dependency. All calls on the client thread; clock is injectable for protocol tests.
 */
public final class ProbeController {
  public enum State {
    IDLE,
    QUEUED,
    REQUESTED,
    MENU_OPEN,
    OFFERS_RECEIVED,
    CLOSING,
    COMPLETE,
    TIMEOUT,
    REJECTED,
    CONFLICT,
    CANCELLED,
    BACKOFF
  }

  public enum Mode {
    MANUAL,
    AUTOMATIC
  }

  public record Context(
      long generation,
      String dimension,
      UUID uuid,
      int entityId,
      Mode mode,
      long started,
      int containerId,
      State state) {}

  private final LongSupplier clock;
  private final Map<UUID, Long> cooldown = new HashMap<>();
  private final Map<UUID, Integer> failures = new HashMap<>();
  private Context context;
  private long nextRequest, quarantine;
  private int timeouts;
  private boolean running;
  private long interval = 2000, timeout = 5000;
  private State last = State.IDLE;

  public ProbeController(LongSupplier clock) {
    this.clock = clock;
  }

  public void configure(long interval, long timeout) {
    this.interval = Math.max(2000, interval);
    this.timeout = Math.max(1000, timeout);
  }

  public Context context() {
    return context;
  }

  public State state() {
    return context == null ? last : context.state;
  }

  public boolean running() {
    return running;
  }

  public void run() {
    if (!running) timeouts = 0;
    running = true;
  }

  public void pause() {
    running = false;
    cancel(State.CANCELLED);
  }

  public boolean eligible(UUID id) {
    return context == null
        && clock.getAsLong() >= Math.max(nextRequest, quarantine)
        && clock.getAsLong() >= cooldown.getOrDefault(id, 0L);
  }

  public boolean begin(long generation, String dimension, UUID uuid, int entityId, Mode mode) {
    if (context != null) {
      stop(State.CONFLICT);
      return false;
    }
    if (clock.getAsLong() < quarantine || mode == Mode.AUTOMATIC && (!running || !eligible(uuid)))
      return false;
    context =
        new Context(
            generation, dimension, uuid, entityId, mode, clock.getAsLong(), -1, State.QUEUED);
    transition(State.REQUESTED, -1);
    nextRequest = clock.getAsLong() + interval;
    return true;
  }

  public boolean open(int container, boolean merchant) {
    if (context == null) return false;
    if (!merchant || context.state != State.REQUESTED) {
      stop(State.CONFLICT);
      return false;
    }
    transition(State.MENU_OPEN, container);
    return true;
  }

  public boolean offers(int container) {
    if (context == null) return false;
    if (context.state != State.MENU_OPEN || context.containerId != container) {
      stop(State.CONFLICT);
      return false;
    }
    transition(State.OFFERS_RECEIVED, container);
    timeouts = 0;
    failures.remove(context.uuid);
    cooldown.put(context.uuid, clock.getAsLong() + 30000);
    return true;
  }

  public void releaseManual() {
    if (context != null && context.mode == Mode.MANUAL && context.state == State.OFFERS_RECEIVED) {
      context = null;
      last = State.IDLE;
    }
  }

  public void closing() {
    if (context != null && context.state == State.OFFERS_RECEIVED)
      transition(State.CLOSING, context.containerId);
  }

  public void complete(boolean restored) {
    if (context != null && context.state == State.CLOSING && restored)
      finish(State.COMPLETE, false);
  }

  public void closed(int id) {
    if (context != null && context.containerId == id)
      finish(context.state == State.CLOSING ? State.COMPLETE : State.CANCELLED, false);
    else if (context != null) stop(State.CONFLICT);
  }

  public void tick() {
    if (context == null && running && (last == State.TIMEOUT || last == State.REJECTED))
      last = State.BACKOFF;
    if (context != null && clock.getAsLong() - context.started >= timeout) {
      boolean auto = context.mode == Mode.AUTOMATIC;
      if (auto && ++timeouts >= 3) running = false;
      finish(State.TIMEOUT, true);
    }
  }

  public void rejected() {
    if (context != null) finish(State.REJECTED, true);
  }

  public void stop(State reason) {
    running = false;
    cancel(reason);
  }

  public void cancel(State reason) {
    if (context != null) finish(reason, false);
    else {
      if (reason != State.TIMEOUT) timeouts = 0;
      last = reason;
    }
  }

  public void reset() {
    context = null;
    cooldown.clear();
    failures.clear();
    running = false;
    nextRequest = quarantine = 0;
    timeouts = 0;
    last = State.IDLE;
  }

  /** Clears local tracking without allowing an outstanding response to enter a new context. */
  public void resetPreservingQuarantine() {
    cancel(State.CANCELLED);
    long until = quarantine;
    reset();
    quarantine = until;
  }

  private void transition(State state, int id) {
    var c = context;
    context =
        new Context(c.generation, c.dimension, c.uuid, c.entityId, c.mode, c.started, id, state);
  }

  private void finish(State state, boolean failure) {
    if (context == null) return;
    var c = context;
    if (state != State.TIMEOUT) timeouts = 0;
    if (failure) {
      int n = failures.merge(c.uuid, 1, Integer::sum);
      cooldown.put(c.uuid, clock.getAsLong() + Math.min(300000, 30000L << (Math.min(n - 1, 4))));
    }
    if (state != State.COMPLETE) quarantine = clock.getAsLong() + timeout;
    last = state;
    context = null;
  }

  public long retryAt(UUID id) {
    return cooldown.getOrDefault(id, 0L);
  }

  public int failureCount(UUID id) {
    return failures.getOrDefault(id, 0);
  }
}
