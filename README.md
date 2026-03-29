# Ghosty

![icon.png](src/main/resources/assets/ghosty/icon.png)

A server-side configurable Anti-EntityESP Fabric mod. 

Ghosty sits between the vanilla server's entity tracker and the client. Once the vanilla server starts tracking an entity for a player, Ghosty intelligently intercepts and filters the network packets (updates). It prevents clients getting updates from entities they shouldn't be able to see (e.g., behind walls or behind the player), effectively neutralizing Entity ESP and Wallhack cheats.

## Features

Ghosty applies the following checks to determine if a **tracked** entity should **update** to the client:
* **Range Check:** Stops updating tracked entities that are outside the configured visual range.
* **FOV Check:** Prevents updating entities outside of the player's Field of View, unless they are in extremely close proximity.
* **Occlusion Check (Raycast):** Intelligently blocks entity updates if they are fully obstructed by opaque blocks.

---

## Configuration

Run the server with this mod installed once to generate the default configuration file at `config/ghosty.json`.

### Default Config

```json
{
  "tracking": {
    "range": {
      "bypassFovCheckMaxDistance": 6.0,
      "enterDistance": 8.0,
      "exitDistance": 12.0
    },
    "fov": {
      "enterAngle": 45.0,
      "exitAngle": 60.0
    },
    "rayCastSamples": [
      [0.5, 0.5, 0.5],
      [0.1, 0.1, 0.1],
      [0.9, 0.1, 0.1],
      [0.1, 0.9, 0.9],
      [0.9, 0.9, 0.9],
      [0.5, 1.0, 0.5],
      [0.5, 0.0, 0.5],
      [0.5, 0.5, 0.1],
      [0.5, 0.5, 0.9]
    ]
  },
  "shouldDespawn": false,
  "cache": {
    "forceRefreshNTicks": 5,
    "byPlayer": {
      "yawChangedThreshold": 10.0,
      "pitchChangedThreshold": 10.0
    },
    "byTarget": {
      "positionMovedThreshold": 1.0
    }
  }
}
```

### Configuration Guide

| Configuration Key | Description |
| :--- | :--- |
| `bypassFovCheckMaxDistance` | Absolute distance threshold. Entities within this range bypass the FOV check entirely and will always update to the client if not occluded (prevents "turn-around" jump scares). |
| `enterDistance` | Distance threshold for a hidden entity to become visible (start updating to client). |
| `exitDistance` | Distance threshold for a visible entity to become hidden (stop updating to client). *(Note: Keeping exit higher than enter creates hysteresis, preventing entities from flickering when walking exactly on the border).* |
| `enterAngle` | FOV angle threshold for a hidden entity to become visible (start updating). |
| `exitAngle` | FOV angle threshold for a visible entity to become hidden (stop updating). |
| `rayCastSamples` | A matrix of relative coordinates applied to the entity's bounding box for occlusion checking. Increasing points improves accuracy (e.g., catching protruding weapons) but costs more CPU cycles. |
| `shouldDespawn` | Whether the server should actively send "Despawn" packets to clients when Ghosty stops updating an entity. Defaults to `false`. **Note:** Enabling this can lead to client-side visual glitches, such as cape physics resetting or flying upwards when the entity reappears. |
| `forceRefreshNTicks` | Time-based cache invalidation. Re-calculates visibility for all tracked entities for every player every N ticks. |
| `yawChangedThreshold` | Rotational cache invalidation. If a player's yaw (horizontal look) changes by more than this value, it triggers a visibility recalculation. |
| `pitchChangedThreshold` | Rotational cache invalidation. If a player's pitch (vertical look) changes by more than this value, it triggers a visibility recalculation. |
| `positionMovedThreshold` | Positional cache invalidation. If a target entity moves further than this distance squared since the last check, it triggers a recalculation for players tracking it. |

---

## Developer API (Programmable Filters)

Ghosty provides events to let other mods hook into and modify the visibility logic dynamically. 

### Setup

1. Add the dependency from JitPack by following the instructions at: [https://jitpack.io/#lightsing/ghosty](https://jitpack.io/#lightsing/ghosty)
2. Import the event class: `me.lightsing.minecraft.ghosty.api.EntityVisibilityEvents`
3. Register your event listeners to `EntityVisibilityEvents.PRE` and/or `EntityVisibilityEvents.POST`.

### `EntityVisibilityEvents.PRE`

This event is fired **before** any internal Ghosty checks (Range, FOV, Occlusion) occur. It is highly useful for whitelisting specific entities (like pets, glowing entities, or boss monsters) that should always be rendered to the client once the vanilla tracker picks them up.

> **Signature:** `(ServerPlayer player, Entity target) -> InteractionResult`
> * Return `InteractionResult.FAIL` to skip all subsequent Ghosty checks and **force the entity to be updated** to the client.
> * Return `InteractionResult.PASS` to do nothing and let Ghosty (or other hooks) process the visibility normally.

### `EntityVisibilityEvents.POST`

This event is fired **after** all internal checks have finished computing, containing Ghosty's final visibility decision. This allows you to modify the update behavior in-place before packets are intercepted or sent.

> **Signature:** `(EntityVisibilityCheckEvent event) -> InteractionResult`
> * Return any value other than `InteractionResult.PASS` to intercept the event and stop propagating it to other registered POST hooks.
