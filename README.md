# Hytale Industries – Item Pipes & Logistics



![](https://media.forgecdn.net/attachments/description/null/description_74bef4c9-90c5-4c18-b82d-cc073bcd8526.png)

## Overview

Hytale Industries adds configurable item pipes that move items between chests, furnaces, and other containers—supporting multiblock structures (e.g., furnaces with filler blocks). Pipes are direction-aware, configurable per face, and update visuals instantly.

Only Pipes are in so far. Craftable in a Furniture Workbench in the Misc tab

![image](https://media.forgecdn.net/attachments/description/1435489/description_b17a38cb-5f08-4608-85f4-33354f2cdca9.png)

## Key Features

*   **Item Pipes**  
    *   Connect containers in any direction; supports multiblock inventories via filler-origin resolution.
    *   Per-face modes: **Default**, **Extract**, **None** (disable).
    *   Moves up to 4 items per second per pipe (shared across extract faces).
*   **Side Configuration UI**  
    *   Right-click to open the pipe UI and set each face’s mode.
    *   Visual model updates immediately to reflect connections.![](https://media.forgecdn.net/attachments/description/null/description_3a5bd5e8-8f1d-4695-9056-f11606d0e0be.png)
*   **Multiblock Support**  
    *   Pipes detect filler blocks and route to the true origin block, so furnaces/chests built from multiple blocks work for both extraction and insertion.
*   **Smart Extraction for Furnaces/Processing Benches**  
    *   Only pulls from output slots; leaves inputs and fuel untouched.
*   **Persistence & Robustness**  
    *   Side configs persist across chunk reloads but clear when a pipe is broken/replaced.
    *   Connection masks and visuals stay in sync even when neighbors change.
*   **Chest UI Fixes**  
    *   Normalizes item IDs to avoid “Invalid Item” when showing multiblock chests.

## Roadmap

*   Energy System
*   Better models lol
*   Automated machines

## How To Use

1.  Place pipes adjacent to containers.
2.  Right-click a pipe to open the UI. Click a face to cycle: **Default → Extract → None → …**
3.  To automate a furnace: set the pipe touching the output face to **Extract**; place another pipe/input on the desired destination.

## Compatibility Notes

*   Designed for Hytale server environments with multiblock containers (e.g., Bench\_Furnace).
*   Uses filler-aware pathing; no special placement rules needed.

## Troubleshooting

*   If a face won’t connect, ensure that face isn’t set to **None**. (Otherwise pls add an issue)
*   For multiblocks, place the pipe on any filler or origin block—both extraction and insertion are supported.
