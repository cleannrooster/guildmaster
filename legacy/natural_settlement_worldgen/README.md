# Legacy natural settlements

Campaign Core no longer places Settlers frontier hubs through ordinary world
generation. Settlements are created by campaign acts instead.

This folder preserves the previous natural-worldgen structure set as a ready-to-use
datapack. To restore it, copy the entire `natural_settlement_worldgen` folder into a
world's `datapacks` directory before generating new chunks, then run `/reload` or
restart the server. It only affects newly generated terrain.

Natural frontier hubs are guaranteed settlement candidates and populate through the
legacy conversion adapter when discovered. Ordinary vanilla-village conversion is
separately opt-in through `config/settlers.json`:

```json
{
  "conversion": {
    "enableSettlementConversion": true,
    "conversionChance": 0.25
  }
}
```

Both legacy systems are disabled in a default installation.
