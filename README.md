# Prismatic Shield Mod

A shield system mod for Minecraft 1.21.1 (NeoForge) that adds stunning energy shields and projectile defense capabilities to entities.

## Features

- Add visual shields to any entity (players, mobs, etc.)
- Multi-layer rendering: hexagonal grid, energy field, particle system, impact flash, shatter animation
- Automatically intercepts and deflects projectiles (arrows, fireballs, etc.)
- Real-time shield state synchronization across all clients

## Commands

### Basic Syntax
```
/shield <add|remove> <entity_selector> [radius] [strength]
```

### Usage Examples
```bash
# Add shield to yourself (radius 3.0, strength 100)
/shield add @s 3.0 100

# Add shields to nearby mobs
/shield add @e[type=!minecraft:player,distance=..10] 2.5 50

# Remove shield
/shield remove @s
```

**Strength Colors**: 75+ Blue, 50-74 Cyan, 25-49 Green, <25 Yellow

---

**Tech Stack**: Minecraft 1.21.1 | NeoForge | Java 21
