package cn.ericcraft.villagerverdict.model;

import java.util.*;

public record ComparisonResult(
    UUID replacement,
    double x,
    double y,
    double z,
    Trade original,
    Trade alternative,
    Map<String, Double> baseDifference,
    Map<String, Double> actualDifference,
    boolean higherLevel) {
  public ComparisonResult {
    baseDifference = Map.copyOf(baseDifference);
    actualDifference = Map.copyOf(actualDifference);
  }
}
