package cn.ericcraft.villagerverdict.model;

import java.util.*;

/**
 * Registry-codec JSON is retained verbatim; valueComponents excludes only enchantments and count.
 */
public record ItemData(
    String item,
    int count,
    String codecJson,
    String valueComponents,
    Map<String, Integer> enchantments,
    String name) {
  public ItemData {
    Objects.requireNonNull(item);
    if (count < 1) throw new IllegalArgumentException("count");
    enchantments = Map.copyOf(enchantments);
    Objects.requireNonNull(codecJson);
    Objects.requireNonNull(valueComponents);
  }

  public String resource() {
    return item + "|" + valueComponents + "|" + new TreeMap<>(enchantments);
  }

  public boolean sameValue(ItemData b) {
    return item.equals(b.item) && valueComponents.equals(b.valueComponents);
  }

  public boolean dominates(ItemData b) {
    return sameValue(b)
        && enchantments.keySet().equals(b.enchantments.keySet())
        && b.enchantments.entrySet().stream()
            .allMatch(e -> enchantments.getOrDefault(e.getKey(), 0) >= e.getValue());
  }

  public boolean higherThan(ItemData b) {
    return dominates(b) && !enchantments.equals(b.enchantments);
  }

  @Override
  public boolean equals(Object object) {
    return object instanceof ItemData b
        && item.equals(b.item)
        && count == b.count
        && codecJson.equals(b.codecJson)
        && valueComponents.equals(b.valueComponents)
        && enchantments.equals(b.enchantments);
  }

  @Override
  public int hashCode() {
    return Objects.hash(item, count, codecJson, valueComponents, enchantments);
  }
}
