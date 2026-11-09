package cn.ericcraft.villagerverdict.analysis;

import cn.ericcraft.villagerverdict.model.*;
import java.util.*;

/** The kept, currently available villagers that explain a discard verdict. */
public record ReplacementSelection(Set<UUID> targets, boolean partial) {
    public ReplacementSelection {
        targets = Set.copyOf(targets);
    }

    public static ReplacementSelection resolve(VillagerRecord source, AnalysisResult analysis,
            Map<UUID, VillagerRecord> available) {
        var verdict = analysis.verdicts().get(source.uuid());
        if (DisplayStatus.resolve(source, verdict == null ? null : verdict.status()) != DisplayStatus.DISCARD)
            return new ReplacementSelection(Set.of(), false);
        var signatureTrades = ProfessionHighlights.trades(source);
        var targets = new LinkedHashSet<UUID>();
        var covered = new HashSet<Trade>();
        for (var comparison : verdict.replacements()) {
            UUID id = comparison.replacement();
            var record = available.get(id);
            var replacementVerdict = analysis.verdicts().get(id);
            if (id.equals(source.uuid()) || record == null || !record.profession().equals(source.profession())
                    || !signatureTrades.contains(comparison.original())
                    || !ProfessionHighlights.trades(record).contains(comparison.alternative())
                    || !TradeComparison.replaces(comparison.alternative(), comparison.original())
                    || !analysis.recommended().contains(id)
                    || replacementVerdict == null || replacementVerdict.status() != AnalysisResult.Status.KEEP
                    || DisplayStatus.resolve(record, replacementVerdict.status()) != DisplayStatus.KEEP) continue;
            targets.add(id);
            covered.add(comparison.original());
        }
        boolean partial = signatureTrades.stream().anyMatch(t -> !covered.contains(t));
        return new ReplacementSelection(targets, partial);
    }
}
