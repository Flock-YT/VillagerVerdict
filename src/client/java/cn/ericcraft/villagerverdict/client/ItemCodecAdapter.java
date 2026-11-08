package cn.ericcraft.villagerverdict.client;

import cn.ericcraft.villagerverdict.model.*;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

public final class ItemCodecAdapter {
  private ItemCodecAdapter() {}

  private static Object level;
  private static final Map<String, ItemStack> CACHE =
      new LinkedHashMap<>(64, .75f, true) {
        protected boolean removeEldestEntry(Map.Entry<String, ItemStack> e) {
          return size() > 4096;
        }
      };

  public static String displayName(ItemData item) {
    var s = decode(item);
    return s.isEmpty() ? item.name() : s.getHoverName().getString();
  }

  public static String enchantmentText(ItemData item) {
    return new TreeMap<>(item.enchantments())
        .entrySet().stream()
            .map(
                e ->
                    net.minecraft.network.chat.Component.translatable(
                                "enchantment." + e.getKey().replace(':', '.'))
                            .getString()
                        + " "
                        + e.getValue())
            .collect(java.util.stream.Collectors.joining(", "));
  }

  public static ItemData encode(ItemStack stack) {
    var ops =
        Minecraft.getInstance().level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
    var full = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
    var value = stack.copyWithCount(1);
    value.remove(DataComponents.ENCHANTMENTS);
    value.remove(DataComponents.STORED_ENCHANTMENTS);
    var components = ItemStack.CODEC.encodeStart(ops, value).getOrThrow();
    var ench = new TreeMap<String, Integer>();
    for (var type : List.of(DataComponents.ENCHANTMENTS, DataComponents.STORED_ENCHANTMENTS)) {
      var e = stack.get(type);
      if (e != null)
        for (var entry : e.entrySet())
          ench.put(entry.getKey().getRegisteredName(), entry.getIntValue());
    }
    return new ItemData(
        BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
        stack.getCount(),
        canonical(full).toString(),
        canonical(components).toString(),
        ench,
        stack.getHoverName().getString());
  }

  public static ItemStack decode(ItemData item) {
    if (item == null) return ItemStack.EMPTY;
    if (level != Minecraft.getInstance().level) {
      level = Minecraft.getInstance().level;
      CACHE.clear();
    }
    var cached = CACHE.get(item.codecJson());
    if (cached != null) return cached;
    try {
      var decoded =
          ItemStack.CODEC
              .parse(
                  Minecraft.getInstance()
                      .level
                      .registryAccess()
                      .createSerializationContext(JsonOps.INSTANCE),
                  JsonParser.parseString(item.codecJson()))
              .getOrThrow();
      CACHE.put(item.codecJson(), decoded);
      return decoded;
    } catch (Exception e) {
      return ItemStack.EMPTY;
    }
  }

  private static JsonElement canonical(JsonElement e) {
    if (e.isJsonObject()) {
      var o = new JsonObject();
      new TreeSet<>(e.getAsJsonObject().keySet())
          .forEach(k -> o.add(k, canonical(e.getAsJsonObject().get(k))));
      return o;
    }
    if (e.isJsonArray()) {
      var a = new JsonArray();
      e.getAsJsonArray().forEach(v -> a.add(canonical(v)));
      return a;
    }
    return e;
  }
}
