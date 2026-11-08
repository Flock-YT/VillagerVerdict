package cn.ericcraft.villagerverdict.model;

public record TradeKey(
    ItemData baseInput, ItemData actualInput, ItemData secondInput, ItemData output) {}
