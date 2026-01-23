# H Industries – Item Pipes & Logistics

<img width="2409" height="1211" alt="image" src="https://github.com/user-attachments/assets/970e6973-a568-4c00-b247-48820a8d5608" />


## Overview

Hytale Industries adds configurable item pipes that move items between chests, furnaces, and other containers—supporting multiblock structures (e.g., furnaces with filler blocks). Pipes are direction-aware, configurable per face, and update visuals instantly.
Craftable in a Furniture Workbench in the Misc tab

![image](https://media.forgecdn.net/attachments/description/1435489/description_db73a7b4-4bef-4145-9d71-9be9a81ac175.png)

## Key Features
- **Power System**
  - Burning generator burns items with fuel value (logs/charcoal) into HE (HyEnergy)
  - Energy can be stored in batteries
  - Energy can be transferred into machines
    - Powered Furnace
    - Quarry 

- **Item Pipes**  
  - Connect containers in any direction; supports multiblock inventories via filler-origin resolution.
  - Per-face modes: **Default**, **Extract**, **None** (disable).
  - Moves up to 4 items per second per pipe (shared across extract faces).
    
- **Side Configuration UI**  
    - Right-click to open the pipe UI and set each face’s mode.
    - Visual model updates immediately to reflect connections.!

### Item Pipes

Item pipes are a powerful tool for moving items between containers.
You can place them where ever you want and open up their UI by interacting with them to configure their behavior.
![image](https://media.forgecdn.net/attachments/description/1435489/description_e083f186-5726-4808-9dad-3a33484cfa22.png)

In the UI you can see the blocks to the 6 directions of the pipe.
Clicking on a face will cycle through the 3 modes:
*   **Default**: Items will be moved to this face (Use this for other pipes or item containers you want items to flow into).
*   **Extract**: Items will be extracted from this face (at a rate of 4/s, higher tiers coming soon).
*   **None**: This face will not be used for item transfer (Can also be used to disable a connection between two pipes).

### Power Cables
Power cables are a new block that can be used to power machines.
They operate very similar to pipes, but they can only be used to power machines.
The UI is very similar to the item pipes UI.

Clicking on a face will cycle through the 3 modes:
*   **Default**: Power will be transferred to this face (Use this for other power cables or machines you want power to flow into).
*   **Extract**: Power will be extracted from this face (at a rate of 250/s, higher tiers coming soon).
*   **None**: This face will not be used for power transfer (Can also be used to disable a connection between two power cables).


### Burning Generator
The burning generator is a new block that can be used to generate HE from logs and charcoal (Or anything with a fuel value).
It can be placed anywhere and will burn items into HE.
It has an internal buffer of 10k HE and can be extracted with Power Cables.
You can open the internal storage slot from within the UI to add fuel, or pipe into it using item pipes.

### Small Battery
The small battery is a new block that can be used to store large amounts of HE.
HE can be extracted with Power Cables.
You can interact with it to open the UI to view the HE stored in it.

### Powered Furnace
The powered furnace is a new block that can be used to power a furnace.
It will consume HE from it's internal buffer to smelt items. Any default Furnace recipes will work with this.
It has an internal buffer of 10k HE and can be inserted to with Power Cables.
In the UI you can see the internal buffer and the HE stored in it.
The input item slot can be clicked to open the Item Container to insert items into the furnace.
The output item slot can be clicked to open the Item Container to extract items from the furnace.
(Annoyingly, I haven't found a way to have the player inventory open with custom UI yet)


### Chunk Loader
The chunk loader is a powerful new block that can be used to keep chunks loaded.
When the plugin is loaded, the internal chunk loader registry will locate and spawn all chunks with loaders present.
Chunk loaders have two modes:
*   **Background**: Hytale _will_ keep the chunk loaded but at a MUCH lower priority, tick rate may drop significantly (some performance impact)
*   **Active**: Hytale will actively keep this chunk loaded and tick at the same rate as normal. (Potentially large performance impact)

### Quarry (WIP)
The Quarry is a new machine that can be used to mine blocks in a given area.
It has an internal buffer of 10k HE and can be inserted to with Power Cables.
The quarry will auto-output the result of mining blocks into the Item Container directly above it.
In the UI you can see the internal buffer and the HE stored in it.
You can set the Width, Depth, and Y level start for the quarry.
When you press start it will start consuming HE from the internal buffer and mining blocks.
Air blocks are counted as 1/10th of a block, so if you leave y=WORLD_MAX_HEIGHT (319) it will move through the y levels relatively quickly.
The quarry will load the chunk it's working on when it's running, but I'd recommend placing a chunk loader above the quarry to keep it loaded.

This is still pretty WIP so expect bugs and missing features.


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
*   Solar panels
*   Automated farming


## Troubleshooting

*   If a face won’t connect, ensure that face isn’t set to **None**. (Otherwise pls add an issue)
*   For multiblocks, place the pipe on any filler or origin block—both extraction and insertion are supported.
