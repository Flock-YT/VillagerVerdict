package cn.ericcraft.villagerverdict.client;

import static cn.ericcraft.villagerverdict.probe.ProbeController.*;

import cn.ericcraft.villagerverdict.analysis.*;
import cn.ericcraft.villagerverdict.model.*;
import cn.ericcraft.villagerverdict.probe.*;
import cn.ericcraft.villagerverdict.storage.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.*;
import org.slf4j.*;

public final class VerdictService {
  public static final VerdictService INSTANCE = new VerdictService();
  private static final Logger LOG = LoggerFactory.getLogger("VillagerVerdict");
  private final Minecraft mc = Minecraft.getInstance();
  private final Path root = FabricLoader.getInstance().getConfigDir().resolve("villagerverdict");
  private final JsonStore store =
      new JsonStore(
          e -> {
            LOG.error("Data operation failed; failed reads are write protected", e);
          });
  public Settings settings = Settings.defaults();
  public final ProbeController probe = new ProbeController(System::currentTimeMillis);
  private final java.util.concurrent.ExecutorService analyzer =
      java.util.concurrent.Executors.newSingleThreadExecutor(
          r -> {
            var t = new Thread(r, "VillagerVerdict analysis");
            t.setDaemon(true);
            return t;
          });
  private boolean analysisInFlight;
  private long analysisRevision;
  private final Map<UUID, Villager> loaded = new LinkedHashMap<>();
  private final Map<UUID, VillagerRecord> records = new LinkedHashMap<>();
  private final Map<UUID, CachedTrade> tradeCache = new LinkedHashMap<>();
  private final Map<UUID, String> notifications = new HashMap<>();
  public List<TargetTrade> targets = new ArrayList<>();
  public List<TradingHallRegion> regions = new ArrayList<>();
  public AnalysisResult analysis = AnalysisResult.empty();
  public Map<String, AnalysisResult> regional = Map.of();

  public record Marker(
      int color, List<Component> lines) {
    public Marker {
      lines = List.copyOf(lines);
    }
  }

  public record StoredWorld(WorldIdentity identity, int cachedTrades, boolean current) {}

  public Map<UUID, Marker> markers = Map.of();
  public Set<UUID> nearby = Set.of();
  public UUID selected;
  /** The villager currently shown by the single-target navigation mode, if any. */
  public UUID navigationTarget;
  public long scanNanos, analysisNanos, markerNanos;

  public long writeNanos() {
    return store.lastWriteNanos;
  }

  private ClientLevel level;
  private WorldIdentity identity;
  private long generation;
  private int ticks;
  private boolean ready, dirty, autoDispatch, restoring, contentSynced, settingsReady;
  private MerchantScreen suppressed;
  private WorldData persistedData;
  public String error = "";

  public void init() {
    store
        .read(root.resolve("settings.json"), Settings.class, Settings::defaults)
        .whenComplete(
            (v, e) ->
                mc.execute(
                    () -> {
                      settingsReady = true;
                      if (e == null) {
                        settings = v;
                        probe.configure(v.intervalMillis(), v.timeoutMillis());
                      } else error = "Settings read failed; file protected";
                    }));
  }

  public WorldIdentity identity() {
    return identity;
  }

  public Collection<VillagerRecord> records() {
    return List.copyOf(records.values());
  }

  public VillagerRecord record(UUID id) {
    return records.get(id);
  }

  public boolean isLoaded(UUID id) {
    return loaded.containsKey(id);
  }

  public boolean ready() {
    return ready;
  }

  public static boolean eligible(Villager v) {
    return VillagerRecord.tradable(v.isBaby(), v.getVillagerData().profession().getRegisteredName());
  }

  public boolean enabled(VillagerRecord record) {
    if (!record.tradable() || !settings.professionEnabled(record.profession())) return false;
    var villager = loaded.get(record.uuid());
    return villager != null && inRange(villager)
        && record.profession().equals(villager.getVillagerData().profession().getRegisteredName());
  }

  private boolean enabled(Villager villager) {
    return eligible(villager)
        && settings.professionEnabled(villager.getVillagerData().profession().getRegisteredName());
  }

  public DisplayStatus displayStatus(VillagerRecord record, AnalysisResult.Verdict verdict) {
    return DisplayStatus.resolve(record, verdict == null ? null : verdict.status());
  }

  private boolean inRange(Villager v) {
    return mc.player != null && v.level() == level && v.isAlive() && !v.isRemoved()
        && enabled(v) && v.distanceToSqr(mc.player) <= (double) settings.radius() * settings.radius();
  }

  /** Evict active state promptly, including with automatic discovery disabled. */
  private void removeIneligibleRecords() {
    boolean cacheChanged = false;
    for (var v : loaded.values()) {
      var cached = tradeCache.get(v.getUUID());
      if (v.isDeadOrDying() || !eligible(v)
          || cached != null && cached.forProfession(v.getVillagerData().profession().getRegisteredName()) == null) {
        cacheChanged |= tradeCache.remove(v.getUUID()) != null;
      }
    }
    var removed = records.keySet().stream().filter(id -> {
      var v = loaded.get(id);
      return v == null || !inRange(v);
    }).collect(java.util.stream.Collectors.toSet());
    removeActive(removed);
    if (cacheChanged) save();
  }

  private AnalysisResult without(AnalysisResult result, Set<UUID> removed) {
    var recommended = new LinkedHashSet<>(result.recommended());
    recommended.removeAll(removed);
    var verdicts = new LinkedHashMap<>(result.verdicts());
    removed.forEach(verdicts::remove);
    return new AnalysisResult(recommended, verdicts, result.gaps());
  }

  private void removeActive(Set<UUID> removed) {
    if (removed.isEmpty()) return;
    if (probe.context() != null && removed.contains(probe.context().uuid())) cancel(State.CANCELLED);
    removed.forEach(records::remove);
    removed.forEach(notifications::remove);
    // ImmutableCollections.Set12 rejects null in contains(); selection is optional during
    // entity unloads and level teardown.
    if (selected != null && removed.contains(selected)) selected = null;
    if (navigationTarget != null && removed.contains(navigationTarget)) navigationTarget = null;
    nearby = Set.copyOf(records.keySet());
    var remainingMarkers = new HashMap<>(markers);
    removed.forEach(remainingMarkers::remove);
    markers = Map.copyOf(remainingMarkers);
    // Keep published colors for remaining villagers until a fresh result is ready.
    analysis = without(analysis, removed);
    var remainingRegions = new LinkedHashMap<String, AnalysisResult>();
    regional.forEach((id, result) -> remainingRegions.put(id, without(result, removed)));
    regional = Map.copyOf(remainingRegions);
    analysisRevision++;
    dirty = true;
  }

  public void load(Entity e) {
    if (e instanceof Villager v) {
      loaded.put(v.getUUID(), v);
      dirty = true;
    }
  }

  public void unload(Entity e) {
    if (e instanceof Villager v) {
      if (v.isDeadOrDying() || !eligible(v)) tradeCache.remove(v.getUUID());
      loaded.remove(v.getUUID());
      removeActive(Set.of(v.getUUID()));
      save();
    }
  }

  private String server() {
    if (mc.hasSingleplayerServer())
      return "singleplayer:"
          + mc.getSingleplayerServer()
              .getWorldPath(LevelResource.ROOT)
              .toAbsolutePath()
              .normalize();
    return mc.getCurrentServer() == null
        ? "unknown"
        : mc.getCurrentServer().ip.toLowerCase(Locale.ROOT);
  }

  public void tick() {
    if (!settingsReady) return;
    String dimension = mc.level == null ? null : mc.level.dimension().identifier().toString();
    if (mc.level != level || identity != null && !identity.dataset().equals(settings.dataset()))
      switchWorld(dimension);
    if (level == null || !ready || clearingCurrentWorld()) return;
    removeIneligibleRecords();
    var before = probe.context();
    probe.tick();
    if (before != null && probe.context() == null) restore();
    if (probe.context() != null && !validContext()) cancel(State.CANCELLED);
    if (probe.context() != null
        && probe.context().mode() == Mode.AUTOMATIC
        && mc.player.isShiftKeyDown()) cancel(State.CONFLICT);
    if (++ticks % settings.scanTicks() == 0 && settings.autoScan()) scan();

    if (dirty && !analysisInFlight) {
      dirty = false;
      analyze();
    }
    long markerStart = System.nanoTime();
    prepareMarkers();
    markerNanos = System.nanoTime() - markerStart;
    var c = probe.context();
    if (c != null
        && c.mode() == Mode.AUTOMATIC
        && c.state() == State.OFFERS_RECEIVED
        && contentSynced) {
      if (owns(c) && emptyMenu() && mc.gui.screen() == null) {
        probe.closing();
        mc.player.closeContainer();
        suppressed = null;
        probe.complete(mc.player.containerMenu == mc.player.inventoryMenu);
        navigateNext();
      } else {
        cancel(State.CONFLICT);
      }
    }
    if (probe.running() && probe.context() == null && safeBaseline())
      for (var v :
          loaded.values().stream()
              .sorted(Comparator.<Villager>comparingLong(v -> {
                var record = records.get(v.getUUID());
                return record == null || record.snapshot() == null ? Long.MIN_VALUE : record.snapshot().checkedAt();
              }).thenComparingDouble(v -> v.distanceToSqr(mc.player)))
              .toList()) {
        if (!inRange(v)) continue;
        var r = records.get(v.getUUID());
        // Newly loaded workers must not wait for the periodic metadata scan.
        if (r == null || r.level() != v.getVillagerData().level()
            || !r.profession().equals(v.getVillagerData().profession().getRegisteredName())) {
          refreshRecord(v, System.currentTimeMillis());
          r = records.get(v.getUUID());
        }
        if (!r.tradable() || !probe.eligible(v.getUUID())) continue;
        Vec3 point = hitPoint(v);
        if (point == null) continue;
        if (probe.begin(generation, dimension, v.getUUID(), v.getId(), Mode.AUTOMATIC)) {
          contentSynced = false;
          autoDispatch = true;
          try {
            var result =
                mc.gameMode.interact(
                    mc.player, v, new EntityHitResult(v, point), probeHand());
            if (result == net.minecraft.world.InteractionResult.FAIL) probe.rejected();
          } finally {
            autoDispatch = false;
          }
        }
        break;
      }
  }

  private void switchWorld(String dimension) {
    save();
    generation++;
    cancel(State.CANCELLED);
    // Dataset/dimension changes and failed-clear reloads can share the outstanding connection.
    if (mc.getConnection() == null) probe.reset();
    else probe.resetPreservingQuarantine();
    loaded.clear();
    records.clear();
    tradeCache.clear();
    persistedData = null;
    targets = new ArrayList<>();
    regions = new ArrayList<>();
    analysis = AnalysisResult.empty();
    regional = Map.of();
    markers = Map.of();
    nearby = Set.of();
    notifications.clear();
    selected = null;
    navigationTarget = null;
    ready = false;
    level = mc.level;
    if (level == null) {
      identity = null;
      return;
    }
    identity = new WorldIdentity(server(), settings.dataset(), dimension);
    long request = generation;
    WorldIdentity requested = identity;
    for (var e : level.entitiesForRendering()) load(e);
    store
        .read(dataPath(requested), WorldData.class, () -> WorldData.empty(requested))
        .whenComplete(
            (data, e) ->
                mc.execute(
                    () -> {
                      if (request != generation || !requested.equals(identity)) return;
                      if (e != null) {
                        error = "World data read failed; original file protected";
                        return;
                      }
                      if (!requested.equals(data.identity())) {
                        error = "World identity mismatch";
                        return;
                      }
                      persistedData = data;
                      data.cachedTrades().forEach(v -> tradeCache.put(v.uuid(), v));
                      targets = new ArrayList<>(data.targets());
                      regions = new ArrayList<>(data.regions());
                      ready = true;
                      error = "";
                      scan();
                      if (!clearingCurrentWorld()
                          && settings.autoServers().getOrDefault(identity.server(), false)
                          && settings.acknowledged().contains(identity.server())) probe.run();
                    }));
  }

  private Path dataPath(WorldIdentity id) {
    return root.resolve("worlds").resolve(JsonStore.fileKey(id) + ".json");
  }

  public CompletableFuture<List<StoredWorld>> storedWorlds() {
    WorldData active = ready && identity != null ? currentWorldData() : null;
    save();
    return store.worlds(root.resolve("worlds")).thenApply(data -> {
      var result = new LinkedHashMap<WorldIdentity, StoredWorld>();
      for (var world : data)
        result.put(world.identity(), new StoredWorld(world.identity(), world.cachedTrades().size(),
            active != null && active.identity().equals(world.identity())));
      if (active != null)
        result.put(active.identity(), new StoredWorld(active.identity(), active.cachedTrades().size(), true));
      return result.values().stream()
          .sorted(Comparator.comparing((StoredWorld w) -> !w.current())
              .thenComparing(w -> w.identity().server())
              .thenComparing(w -> w.identity().dataset())
              .thenComparing(w -> w.identity().dimension()))
          .toList();
    });
  }

  public CompletableFuture<Integer> clearWorldVillagers(WorldIdentity target) {
    Objects.requireNonNull(target);
    return clearStoredVillagers(id -> id.equals(target));
  }

  public CompletableFuture<Integer> clearServerVillagers(String server) {
    Objects.requireNonNull(server);
    return clearStoredVillagers(id -> id.server().equals(server));
  }

  private java.util.function.Predicate<WorldIdentity> clearingWorlds;

  private boolean clearingCurrentWorld() {
    return identity != null && clearingWorlds != null && clearingWorlds.test(identity);
  }

  private CompletableFuture<Integer> clearStoredVillagers(
      java.util.function.Predicate<WorldIdentity> matches) {
    if (clearingWorlds != null)
      return CompletableFuture.failedFuture(new IllegalStateException("Data clear already in progress"));
    save();
    clearingWorlds = matches;
    if (clearingCurrentWorld()) {
      cancel(State.CANCELLED);
      probe.resetPreservingQuarantine();
      analysisRevision++;
    }
    var finished = new CompletableFuture<Integer>();
    store.clearVillagers(root.resolve("worlds"), matches).whenComplete((count, failure) ->
        mc.execute(() -> {
          boolean clearActive = clearingCurrentWorld();
          clearingWorlds = null;
          if (failure == null) {
            if (clearActive && ready) resetActiveAfterClear();
            finished.complete(count);
          } else {
            // A partial disk failure must not let stale memory overwrite already cleared files.
            if (clearActive) {
              ready = false;
              switchWorld(identity.dimension());
            }
            error = "Could not clear world data";
            finished.completeExceptionally(failure);
          }
        }));
    return finished;
  }

  private WorldData currentWorldData() {
    return new WorldData(4, identity, List.copyOf(tradeCache.values()), List.copyOf(targets), List.copyOf(regions));
  }

  private void resetActiveAfterClear() {
    cancel(State.CANCELLED);
    probe.resetPreservingQuarantine();
    analysisRevision++;
    records.clear();
    tradeCache.clear();
    notifications.clear();
    analysis = AnalysisResult.empty();
    regional = Map.of();
    markers = Map.of();
    nearby = Set.of();
    selected = null;
    navigationTarget = null;
    ticks = 0;
    dirty = false;
    // Configuration may have been edited after the clear began, while disk work was queued.
    persistedData = null;
    save();
  }

  private void refreshRecord(Villager v, long now) {
    if (!inRange(v)) return;
    var old = records.get(v.getUUID());
    var d = v.getVillagerData();
    boolean professionChanged =
        old != null && !old.profession().equals(d.profession().getRegisteredName());
    boolean levelChanged = old == null || old.level() != d.level();
    boolean metadataChanged =
        old == null
            || levelChanged
            || !old.profession().equals(d.profession().getRegisteredName())
            || old.baby() != v.isBaby();
    boolean positionChanged =
        old == null
            || Double.compare(old.x(), v.getX()) != 0
            || Double.compare(old.y(), v.getY()) != 0
            || Double.compare(old.z(), v.getZ()) != 0;
    var cached = tradeCache.get(v.getUUID());
    var snapshot = old == null
        ? cached == null ? null : cached.forProfession(d.profession().getRegisteredName())
        : professionChanged ? null : old.snapshot();
    if (professionChanged) {
      tradeCache.remove(v.getUUID());
      removeActive(Set.of(v.getUUID()));
    }
    var current =
        new VillagerRecord(
            identity,
            v.getUUID(),
            d.profession().getRegisteredName(),
            d.level(),
            v.isBaby(),
            positionChanged ? v.getX() : old.x(),
            positionChanged ? v.getY() : old.y(),
            positionChanged ? v.getZ() : old.z(),
            metadataChanged || positionChanged ? now : old.lastSeen(),
            snapshot,
            old == null ? VillagerRecord.Manual.AUTO : old.manual(),
            old == null ? "" : old.note(),
            old == null ? Set.of() : old.tags());
    if (!current.equals(old)) {
      records.put(v.getUUID(), current);
      nearby = Set.copyOf(records.keySet());
      analysisRevision++;
      dirty = true;
    }
  }

  public boolean scan() {
    if (mc.player == null || identity == null || !ready || clearingCurrentWorld()) return false;
    long start = System.nanoTime();
    removeIneligibleRecords();
    long now = System.currentTimeMillis();
    var near = new LinkedHashSet<UUID>();
    for (var v : loaded.values()) {
      if (!inRange(v)) continue;
      if (probe.context() != null && probe.context().uuid().equals(v.getUUID()) && !validContext())
        cancel(State.CANCELLED);
      refreshRecord(v, now);
      if (v.distanceToSqr(mc.player) <= settings.radius() * settings.radius())
        near.add(v.getUUID());
    }
    nearby = Set.copyOf(near);
    save();
    scanNanos = System.nanoTime() - start;
    return true;
  }

  public void save() {
    if (!ready || identity == null) return;
    // Keep configuration edits under their original identity, including when leaving mid-clear.
    // Empty records are safe whether this queued snapshot is written before or after the clear.
    var current =
        new WorldData(
            4,
            identity,
            clearingCurrentWorld() ? List.of() : List.copyOf(tradeCache.values()),
            List.copyOf(targets),
            List.copyOf(regions));
    if (current.equals(persistedData)) return;
    if (store.save(dataPath(identity), current)) persistedData = current;
  }

  public void settings(Settings s) {
    boolean professionsChanged = !settings.excludedProfessions().equals(s.excludedProfessions());
    settings = s;
    removeIneligibleRecords();
    if (professionsChanged) scan();
    probe.configure(s.intervalMillis(), s.timeoutMillis());
    store.save(root.resolve("settings.json"), s);
    dirty = true;
  }

  public void changed() {
    analysisRevision++;
    dirty = true;
    save();
  }

  public void edit(VillagerRecord v) {
    if (!records.containsKey(v.uuid()) || !enabled(v)) return;
    if (v.snapshot() != null) tradeCache.put(v.uuid(), CachedTrade.from(v));
    records.put(v.uuid(), v);
    changed();
  }

  public void analyze() {
    if (analysisInFlight) {
      dirty = true;
      return;
    }
    analysisInFlight = true;
    long request = ++analysisRevision;
    long epoch = generation;
    var input = records.values().stream().filter(this::enabled).toList();
    var targetCopy = List.copyOf(targets);
    var regionCopy = List.copyOf(regions);
    var options = settings;
    java.util.concurrent.CompletableFuture.supplyAsync(
            () -> {
              long start = System.nanoTime();
              var optimizer = new Optimizer();
              var global =
                  optimizer.analyze(
                        input, targetCopy, options.backups());
              var perRegion = new LinkedHashMap<String, AnalysisResult>();
              for (var r : regionCopy)
                perRegion.put(
                    r.id(),
                    optimizer.analyze(
                        input.stream().filter(r::contains).toList(),
                        r.targets(),
                        options.backups()));
              return new Computed(global, Map.copyOf(perRegion), System.nanoTime() - start);
            },
            analyzer)
        .whenComplete(
            (result, e) ->
                mc.execute(
                    () -> {
                      analysisInFlight = false;
                      if (epoch != generation || request != analysisRevision || dirty) return;
                      if (e != null) {
                        LOG.error("Analysis failed", e);
                        error = "Analysis failed";
                        return;
                      }
                      analysis = result.global();
                      regional = result.regional();
                      analysisNanos = result.nanos();
                    }));
  }

  private record Computed(
      AnalysisResult global, Map<String, AnalysisResult> regional, long nanos) {}

  public void humanAction() {
    if (!autoDispatch && probe.context() != null) cancel(State.CONFLICT);
  }

  public void manual(Entity entity) {
    if (autoDispatch) return;
    if (probe.context() != null) {
      cancel(State.CONFLICT);
      return;
    }
    if (ready && !clearingCurrentWorld() && probe.manualReady()
        && entity instanceof Villager v && inRange(v)) {
      // An interaction can precede the next scan, including when automatic scanning is disabled.
      // Profession refresh evicts old active state; do it before creating the new request.
      refreshRecord(v, System.currentTimeMillis());
      if (probe.begin(generation, identity.dimension(), v.getUUID(), v.getId(), Mode.MANUAL)) save();
    }
  }

  public void open(int container, boolean merchant) {
    if (probe.context() != null && !validContext()) {
      cancel(State.CONFLICT);
      return;
    }
    boolean ok = probe.open(container, merchant);
    if (!ok) restore();
    contentSynced = false;
  }

  public boolean screen(Screen screen) {
    if (restoring) return false;
    var c = probe.context();
    if (screen instanceof MerchantScreen m
        && c != null
        && c.mode() == Mode.AUTOMATIC
        && c.state() == State.MENU_OPEN
        && m.getMenu().containerId == c.containerId()
        && validContext()) {
      suppressed = m;
      return true;
    }
    if (c != null && c.mode() == Mode.AUTOMATIC && screen != null) {
      cancel(State.CONFLICT);
      suppressed = null;
    }
    return false;
  }

  public void content(int container) {
    var c = probe.context();
    if (c != null && c.containerId() == container && owns(c)) contentSynced = true;
  }

  public void offers(ClientboundMerchantOffersPacket packet) {
    var c = probe.context();
    if (c == null) return;
    if (!validContext() || !owns(c) || packet.getContainerId() != c.containerId()) {
      cancel(State.CONFLICT);
      return;
    }
    if (!probe.offers(packet.getContainerId())) {
      restore();
      return;
    }
    try {
      var ts = new ArrayList<Trade>();
      for (var o : packet.getOffers())
        ts.add(
            new Trade(
                ItemCodecAdapter.encode(o.getBaseCostA()),
                ItemCodecAdapter.encode(o.getCostA()),
                o.getCostB().isEmpty() ? null : ItemCodecAdapter.encode(o.getCostB()),
                ItemCodecAdapter.encode(o.getResult()),
                o.getUses(),
                o.getMaxUses()));
      var snap =
          new TradeSnapshot(ts, System.currentTimeMillis());
      var old = records.get(c.uuid());
      if (old == null) {
        var v = loaded.get(c.uuid());
        if (v != null) {
          var d = v.getVillagerData();
          old =
              new VillagerRecord(
                  identity,
                  v.getUUID(),
                  d.profession().getRegisteredName(),
                  d.level(),
                  v.isBaby(),
                  v.getX(),
                  v.getY(),
                  v.getZ(),
                  System.currentTimeMillis(),
                  null,
                  VillagerRecord.Manual.AUTO,
                  "",
                  Set.of());
          records.put(v.getUUID(), old);
        }
      }
      if (old != null) {
        records.put(c.uuid(), old.withSnapshot(snap));
        tradeCache.put(c.uuid(), CachedTrade.from(old.withSnapshot(snap)));
        notifyChanges(old, snap);
        changed();
      }
      if (c.mode() == Mode.MANUAL) {
        probe.releaseManual();
      }
    } catch (Exception e) {
      LOG.error("Cannot encode offers", e);
      cancel(State.CONFLICT);
    }
  }

  public void closed(int id) {
    probe.closed(id);
    suppressed = null;
  }

  private boolean validContext() {
    var c = probe.context();
    if (c == null
        || identity == null
        || mc.level != level
        || c.generation() != generation
        || !c.dimension().equals(identity.dimension())) return false;
    var v = loaded.get(c.uuid());
    var record = records.get(c.uuid());
    return v != null && v.getId() == c.entityId() && inRange(v)
        && (record == null
            || record.profession().equals(v.getVillagerData().profession().getRegisteredName()));
  }

  private boolean owns(Context c) {
    return mc.player != null
        && mc.player.containerMenu instanceof MerchantMenu
        && mc.player.containerMenu.containerId == c.containerId();
  }

  private boolean emptyMenu() {
    var m = mc.player.containerMenu;
    return m.getCarried().isEmpty()
        && m.getSlot(0).getItem().isEmpty()
        && m.getSlot(1).getItem().isEmpty();
  }

  private boolean safeBaseline() {
    return mc.player != null
        && mc.gameMode != null
        && !mc.player.isSpectator()
        && mc.gui.screen() == null
        && mc.player.containerMenu == mc.player.inventoryMenu
        && mc.player.containerMenu.getCarried().isEmpty()
        && probeHand() != null
        && !mc.player.isUsingItem()
        && !mc.player.isShiftKeyDown();
  }

  private InteractionHand probeHand() {
    for (var hand : InteractionHand.values()) {
      var item = mc.player.getItemInHand(hand);
      // These items run before trading in Mob.interact and can consume or alter entities.
      if (!item.is(Items.NAME_TAG) && !(item.getItem() instanceof SpawnEggItem)) return hand;
    }
    return null;
  }

  private Vec3 hitPoint(Villager v) {
    Vec3 eye = mc.player.getEyePosition();
    var box = v.getBoundingBox();
    Vec3 p =
        new Vec3(
            Math.clamp(eye.x, box.minX + .01, box.maxX - .01),
            Math.clamp(eye.y, box.minY + .01, box.maxY - .01),
            Math.clamp(eye.z, box.minZ + .01, box.maxZ - .01));
    if (eye.distanceToSqr(p)
        > mc.player.entityInteractionRange() * mc.player.entityInteractionRange()) return null;
    var hit =
        level.clip(
            new ClipContext(eye, p, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
    return hit.getType() == HitResult.Type.MISS ? p : null;
  }

  public void cancel(State state) {
    probe.cancel(state);
    if (state == State.CONFLICT) probe.stop(state);
    restore();
  }

  private void restore() {
    if (suppressed != null
        && mc.player != null
        && mc.gui.screen() == null
        && mc.player.containerMenu == suppressed.getMenu()) {
      restoring = true;
      try {
        mc.gui.setScreen(suppressed);
      } finally {
        restoring = false;
      }
    }
    suppressed = null;
  }

  public void pause() {
    probe.pause();
    restore();
  }

  public void enable(boolean enabled) {
    if (identity == null) return;
    settings(settings.auto(identity.server(), enabled, enabled));
    if (enabled) probe.run();
    else pause();
  }

  public void navigateNext() {
    if (mc.player == null) return;
    selected =
        records.values().stream()
            .filter(
                v ->
                    loaded.containsKey(v.uuid())
                        && enabled(v)
                        && v.snapshot() == null)
            .min(Comparator.comparingDouble(this::distanceSquared))
            .map(VillagerRecord::uuid)
            .orElse(null);
  }

  public boolean navigationActive() {
    return navigationTarget != null;
  }

  public void startNavigation(UUID target) {
    var record = record(target);
    if (record == null || !enabled(record)) return;
    navigationTarget = target;
    prepareMarkers();
  }

  public void cancelNavigation() {
    navigationTarget = null;
    prepareMarkers();
  }

  public double distanceSquared(VillagerRecord v) {
    return mc.player == null
        ? Double.POSITIVE_INFINITY
        : mc.player.distanceToSqr(v.x(), v.y(), v.z());
  }

  public Map<String, Long> stats() {
    long success = 0, failed = 0, pending = 0, none = 0;
    UUID active = probe.context() == null ? null : probe.context().uuid();
    for (var v : records.values())
      if (loaded.containsKey(v.uuid()) && enabled(v)) {
        if (!v.tradable()) none++;
        else if (v.uuid().equals(active)) {
        } else if (probe.failureCount(v.uuid()) > 0) failed++;
        else if (v.snapshot() != null) success++;
        else pending++;
      }
    return Map.of(
        "success",
        success,
        "pending",
        pending,
        "failed",
        failed,
        "active",
        active == null ? 0L : 1L,
        "nontrading",
        none);
  }

  private void notifyChanges(VillagerRecord old, TradeSnapshot snap) {
    String f = snap.fingerprint();
    String previous = notifications.put(old.uuid(), f);
    if (!settings.notifications()
        || f.equals(previous)
        || old.snapshot() != null && f.equals(old.snapshot().fingerprint())) return;
    boolean target =
        targets.stream()
                .anyMatch(t -> snap.trades().stream().anyMatch(a -> t.matches(a) && t.priceMet(a)))
            || regions.stream()
                .filter(r -> r.contains(old))
                .flatMap(r -> r.targets().stream())
                .anyMatch(t -> snap.trades().stream().anyMatch(a -> t.matches(a) && t.priceMet(a)));
    if (target || old.snapshot() != null) {
      SystemToast.add(
          mc.gui.toastManager(),
          new SystemToast.SystemToastId(),
          Component.translatable("vv.notification"),
          changeMessage(old.snapshot(), snap));
      mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1));
    }
  }

  private Component changeMessage(TradeSnapshot before, TradeSnapshot after) {
    var d = SnapshotDelta.between(before, after);
    var text = Component.translatable(before == null ? "vv.targetFound" : "vv.tradeChanged");
    if (before != null) {
      if (d.price()) text.append(" · ").append(Component.translatable("vv.priceChanged"));
      if (d.stock()) text.append(" · ").append(Component.translatable("vv.stockChanged"));
      if (d.unlocked()) text.append(" · ").append(Component.translatable("vv.unlockedChanged"));
    }
    return text;
  }

  private void prepareMarkers() {
    if (!settings.markers() || mc.player == null) {
      markers = Map.of();
      return;
    }
    var m = new LinkedHashMap<UUID, Marker>();
    for (var v :
        loaded.values().stream()
            .filter(this::enabled)
            .filter(v -> nearby.contains(v.getUUID()))
            .filter(v -> navigationTarget == null || v.getUUID().equals(navigationTarget))
            .filter(v -> records.containsKey(v.getUUID())
                && records.get(v.getUUID()).profession().equals(v.getVillagerData().profession().getRegisteredName()))
            .sorted(
                Comparator.<Villager>comparingInt(v -> v.getUUID().equals(selected) ? 0 : 1)
                    .thenComparingDouble(v -> v.distanceToSqr(mc.player)))
            .toList()) {
      var verdict = analysis.verdicts().get(v.getUUID());
      var record = records.get(v.getUUID());
      var status = displayStatus(record, verdict);
      int color =
          switch (status) {
            case KEEP -> settings.green();
            case UNSCANNED -> settings.yellow();
            case DISCARD -> settings.red();
          };
      var lines = MarkerText.lines(record, status, settings.statusMarks(), settings.summaries())
          .stream().map(line -> (Component) line.copy().withColor(color)).toList();
      m.put(v.getUUID(), new Marker(color, lines));
    }
    markers = Map.copyOf(m);
  }

  public void close() {
    save();
    generation++;
    analyzer.shutdownNow();
    store.close();
  }
}
