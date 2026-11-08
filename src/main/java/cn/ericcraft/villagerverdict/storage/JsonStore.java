package cn.ericcraft.villagerverdict.storage;

import com.google.gson.*;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** A single writer, coalesced immutable snapshots, and write protection after failed reads. */
public final class JsonStore implements AutoCloseable {
  public volatile long lastWriteNanos;
  private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
  private final ExecutorService worker =
      Executors.newSingleThreadExecutor(
          r -> {
            var t = new Thread(r, "VillagerVerdict data");
            t.setDaemon(true);
            return t;
          });
  private final Set<Path> blocked = ConcurrentHashMap.newKeySet();
  private final Map<Path, Object> pending = new LinkedHashMap<>();
  private final Consumer<Throwable> errors;

  public JsonStore(Consumer<Throwable> errors) {
    this.errors = errors;
  }

  public <T> CompletableFuture<T> read(Path path, Class<T> type, Supplier<T> missing) {
    return CompletableFuture.supplyAsync(
        () -> {
          if (!Files.exists(path)) return missing.get();
          try (var r = Files.newBufferedReader(path)) {
            T v = gson.fromJson(r, type);
            if (v == null) throw new IOException("null JSON " + path);
            return v;
          } catch (Exception e) {
            blocked.add(path);
            errors.accept(e);
            throw new CompletionException(e);
          }
        },
        worker);
  }

  public synchronized void save(Path path, Object immutable) {
    if (blocked.contains(path)) return;
    boolean schedule = pending.isEmpty();
    pending.put(path, immutable);
    if (schedule) worker.submit(this::drain);
  }

  private void drain() {
    while (true) {
      Path path;
      Object data;
      synchronized (this) {
        if (pending.isEmpty()) return;
        var e = pending.entrySet().iterator().next();
        path = e.getKey();
        data = e.getValue();
        pending.remove(path);
      }
      if (blocked.contains(path)) continue;
      try {
        long start = System.nanoTime();
        atomic(path, gson.toJson(data));
        lastWriteNanos = System.nanoTime() - start;
      } catch (Exception e) {
        errors.accept(e);
      }
    }
  }

  private void atomic(Path path, String text) throws IOException {
    Files.createDirectories(path.getParent());
    Path temp = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");
    try {
      Files.writeString(temp, text, StandardCharsets.UTF_8);
      try (var channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
        channel.force(true);
      }
      Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  public boolean blocked(Path path) {
    return blocked.contains(path);
  }

  public CompletableFuture<Void> flush() {
    return CompletableFuture.runAsync(() -> {}, worker);
  }

  public void close() {
    worker.shutdown();
    try {
      if (!worker.awaitTermination(10, TimeUnit.SECONDS))
        errors.accept(new IOException("writer shutdown timed out"));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  public static String fileKey(Object identity) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(
                      (identity instanceof cn.ericcraft.villagerverdict.model.WorldIdentity w
                              ? w.server().length()
                                  + ":"
                                  + w.server()
                                  + w.dataset().length()
                                  + ":"
                                  + w.dataset()
                                  + w.dimension().length()
                                  + ":"
                                  + w.dimension()
                              : identity.toString())
                          .getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }
}
