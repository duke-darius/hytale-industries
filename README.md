# H Industries – Item Pipes & Logistics

<img width="2409" height="1211" alt="image" src="https://github.com/user-attachments/assets/970e6973-a568-4c00-b247-48820a8d5608" />


## Overview

Hytale Industries adds configurable item pipes that move items between chests, furnaces, and other containers—supporting multiblock structures (e.g., furnaces with filler blocks). Pipes are direction-aware, configurable per face, and update visuals instantly.
Craftable in a Furniture Workbench in the Misc tab

![image](https://media.forgecdn.net/attachments/description/1435489/description_db73a7b4-4bef-4145-9d71-9be9a81ac175.png)

## Key Features
*   **Power System**
    * Burning generator burns items with fuel value (logs/charcoal) into HE (HyEnergy)
    * Energy can be stored in batteries
    * Energy can be transferred into machines
         * Currently Powered Furnace is implemented

*   **Item Pipes**  
    *   Connect containers in any direction; supports multiblock inventories via filler-origin resolution.
    *   Per-face modes: **Default**, **Extract**, **None** (disable).
    *   Moves up to 4 items per second per pipe (shared across extract faces).
*   **Side Configuration UI**  
    *   Right-click to open the pipe UI and set each face’s mode.
    *   Visual model updates immediately to reflect connections.!

![image](https://media.forgecdn.net/attachments/description/1435489/description_f791048f-d4d2-4fb0-bac0-95d39fe90104.png)

![image](https://media.forgecdn.net/attachments/description/1435489/description_e083f186-5726-4808-9dad-3a33484cfa22.png)
*   **Multiblock Support**  
    *   Pipes detect filler blocks and route to the true origin block, so furnaces/chests built from multiple blocks work for both extraction and insertion.
*   **Smart Extraction for Furnaces/Processing Benches**  
    *   Only pulls from output slots; leaves inputs and fuel untouched.
*   **Persistence & Robustness**  
    *   Side configs persist across chunk reloads but clear when a pipe is broken/replaced.
    *   Connection masks and visuals stay in sync even when neighbors change.

## Roadmap
*   Automated machines
*   Quarry
*   Solar panels
*   Automated farming

## How To Use

1.  Place pipes adjacent to containers.
2.  Right-click a pipe to open the UI. Click a face to cycle: **Default → Extract → None → …**
3.  To automate a furnace: set the pipe touching the output face to **Extract**; place another pipe/input on the desired destination.


## Troubleshooting

*   If a face won’t connect, ensure that face isn’t set to **None**. (Otherwise pls add an issue)
*   For multiblocks, place the pipe on any filler or origin block—both extraction and insertion are supported.
