# Applied Cooking — Minecraft 1.12.2 backport

This branch (`1.12.2`) backports Applied Cooking to Minecraft 1.12.2 / Forge.
It is based on the `1.18.x` branch, with changes limited to what the older
loader and dependencies require.

## Development environment

The project is built with Nix (flake-parts + direnv). The shell provides
`jdk8` (OpenJDK 8), which Minecraft 1.12.2 / ForgeGradle 2.3 require, plus the
native X11/GL/audio libraries the LWJGL2 client needs to launch.

```sh
direnv allow        # or: nix develop
./gradlew setupDecompWorkspace
./gradlew build     # jar ends up in build/libs/
```

## Dependency mapping

| 1.18.2                              | 1.12.2                                              |
| ----------------------------------- | --------------------------------------------------- |
| Minecraft 1.18.2                    | Minecraft 1.12.2                                    |
| Forge 40.1.60 / ForgeGradle 5.1     | Forge 14.23.5.2847 / ForgeGradle 2.3                |
| Java 17, official mappings          | Java 8, `stable_39` mappings                        |
| AE2 11.5.0                          | AE2 rv6 (`appliedenergistics2:rv6-stable-7`)        |
| Cooking for Blockheads 12.2.0       | Cooking for Blockheads 6.5.0                        |
| Balm (required)                     | not required on 1.12 (Balm is 1.16+)                |

## What changed

The mod is intentionally kept under the same identity (`appliedcooking`,
`dev.smolinacadena.appliedcooking`, same block/item ids and textures).

- **Balm removed.** 1.12 does not use Balm. The `BalmBlockEntityContract`
  capability-provider plumbing is dropped; the kitchen item provider is exposed
  directly through Cooking for Blockheads' Forge capability.
- **Cooking for Blockheads integration.** On 1.12 CfB discovers item providers
  via the `CapabilityKitchenItemProvider` Forge capability on the tile entity
  (the data-driven `cookingforblockheads/compat` JSON does not exist on 1.12),
  so `KitchenStationTileEntity` exposes the provider through `hasCapability` /
  `getCapability`.
- **Linking.** On 1.18 the station is linked at an ME Wireless Access Point via
  `GridLinkables`. On 1.12 the Wireless Access Point GUI only holds range
  boosters; linkable items are encoded at the **ME Security Station** through
  `INetworkEncodable`. `KitchenStationBlockItem` implements `INetworkEncodable`
  and stores the security station's locatable serial, which is resolved back to
  the station with `ILocatableRegistry#getLocatableBy`.
- **Network storage access.** AE2 rv6 exposes the network inventory through
  `IGrid#getCache(IStorageGrid.class)#getInventory(IItemStorageChannel.class)`
  (an `IMEMonitor<IAEItemStack>`) instead of the newer `MEStorage` API.
- **Block.** `KitchenStationBlock` extends CfB's `BlockKitchen` as before. The
  `connected` property is encoded into block metadata (bit 8) alongside the
  horizontal `facing`, since 1.12 has no extended block states.
- **Item drops.** 1.12 has no loot tables, so the block produces its drop
  directly. Because the block is removed before `harvestBlock` runs, the tile
  entity (which holds the linked grid key) is taken from the `harvestBlock`
  parameter rather than looked up from the world; `getDrops` covers
  explosion/other paths where the tile entity still exists.
- **Registration.** Forge 1.12 registries/`GameRegistry` replace the 1.18
  `DeferredRegister` / `RegistryEvent` for block entities. Client model
  registration moves to a `@SidedProxy` client proxy.
- **Compatibility addons.** The Jade and The One Probe tooltip addons are not
  ported; they are optional and use entirely different APIs on 1.12.
- **Resources.** Lang is `en_us.lang`, recipes live under `assets/<modid>/recipes`
  and use the 1.12 JSON format, and 1.12 block tags (`minecraft:tags`) are
  replaced with `setHarvestLevel("pickaxe", 1)`. The blockstate follows the 1.12
  convention where the `model` value omits the `block/` prefix (the loader adds
  it) and includes an `inventory` variant for the item; the connected model
  parent is namespaced (`appliedcooking:block/kitchen_station`).
