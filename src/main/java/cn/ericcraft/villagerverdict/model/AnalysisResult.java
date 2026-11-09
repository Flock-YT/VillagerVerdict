package cn.ericcraft.villagerverdict.model;

import java.util.*;

public record AnalysisResult(
    Set<UUID> recommended, Map<UUID, Verdict> verdicts, List<String> gaps) {
  public enum Status {
    KEEP,
    DISCARD
  }

  public record Verdict(
      Status status,
      String reason,
      List<ComparisonResult> replacements,
      int uniqueTrades,
      int coverage) {
    public Verdict {
      replacements = List.copyOf(replacements);
    }
  }

  public AnalysisResult {
    recommended = Set.copyOf(recommended);
    verdicts = Map.copyOf(verdicts);
    gaps = List.copyOf(gaps);
  }

  public static AnalysisResult empty() {
    return new AnalysisResult(Set.of(), Map.of(), List.of());
  }
}
