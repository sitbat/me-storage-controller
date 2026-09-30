---
navigation:
  parent: ae2:items-blocks-machines/items-blocks-machines-index.md
  title: ME Storage Controller
  icon: me_storage_controller:controller
  position: 215
item_ids:
- me_storage_controller:controller
---

# ME Storage Controller

See where your ME storage is used, inspect individual cells, and move stored resources without opening each drive.

Open this page with the controller's Guide button, or hover its item and hold the guide key, **G** by default.

## Connect and open

Connect the controller to a powered AE2 network with an available channel, then right-click it. The controller uses **one channel** when channels are enabled. If the network is offline, check power, cables and available channels.

The controller shows its current ME network. It does not search every network in the world or recursively open subnetworks.

## Choose where to work

Expand the directory arrows to browse **ME Network → Dimension → Device → Cell**. Several devices can stay expanded.

- Select **ME Network** to view and use the network's available storage.
- Select a device to work with that device's storage.
- Select a cell to work with that cell alone.

Your selection controls both the displayed contents and where you insert or extract resources. A selected cell will not borrow space or resources from another cell. Back moves from cell to device, then to the network. Scrolling or folding the directory keeps the current selection.

Storage-bus filters, access modes, sided-container rules and interaction permissions still apply. An unavailable or read-only source may show contents without allowing transfers.

## Read capacity and amounts

Capacity summaries use **1024 B = 1 KB**, then MB, GB and larger units. Hover for exact byte values. Bytes measure cell storage usage, not item counts: different stored types also use capacity. The type count shows how many distinct entries occupy the cell.

Item icons show abbreviated quantities; select or hover an entry for its exact amount. Items with different stored data may be separate entries even when their names match.

For external containers, occupied slots count nonempty slots, including partial stacks. Tank capacity measures fluid volume. These are physical container measurements; storage-bus filters may make less of the contents accessible. **Unknown** means the device does not expose a usable value, not zero or unlimited capacity.

## AE2 Omni Cells

With AE2 Omni Cells 1.20.1-forge 1.1.6, browse Omni, Complex Omni and Quantum cells from 1k to 256M, as well as creative Long/BigInteger cells, through the same device tree. Mixed items, fluids and supported chemicals use the same scope and container controls.

OmniCells has no extra per-type byte overhead. Quantum cells have unlimited types, but their ordinary byte capacity remains finite. **∞** marks a known unlimited bound; **unknown** means a reliable value is unavailable.

Extremely large BigInteger amounts can exceed what the ME network reports. If a resource reaches that reporting limit, its displayed amount may be capped and used bytes remain unknown; the known unlimited bound still shows ∞. Do not treat those capped amounts as exact totals.

## Move ordinary items

These actions use the right-hand content grid, within your selected scope.

| Action | Empty cursor | Holding ordinary items |
|---|---|---|
| Left-click | Take up to one stack | Insert the carried stack |
| Right-click | Take half of the available stack, rounded up | Insert one item |
| Shift-left-click | Take up to one stack into available inventory space | Insert the carried stack |

You can insert carried items on an empty content tile. Shift-click a regular item in your inventory to insert it into the selected scope. Storage cells have separate slot controls, described below.

## Fill and empty containers

Hold a compatible container and **left-click a fluid or chemical entry** to fill it from the selected scope. **Right-click without Shift** to empty the container into that scope; an empty content tile also works. Shift-left-click fills and tries to move the result into your inventory. Shift-right-click does not empty a resource container.

One water bucket moves **1000 mB**. Less than a bucket of water, less than a bucket of free space, or insufficient transfer energy leaves the bucket unchanged. Other containers follow their own transfer limits: with default settings, a Mekanism basic chemical tank moves **1000 units per operation**, rather than filling its entire 64000-unit capacity at once.

With an empty cursor, left-clicking a bucketable fluid can use one empty bucket stored in the selected scope. It will not fetch a bucket from a different scope. Other resources normally require you to hold the appropriate container. Chemical support requires an addon that connects those resources and containers to AE2; displaying a resource does not guarantee every container can handle it.

A filled single container stays on the cursor if Shift cannot fit it into your inventory. When processing a stack of containers, the extra filled container follows its mod's normal overflow behavior and may drop if your inventory is full. Leave inventory space when using stacked containers.

## Manage whole storage cells

The directory selects a cell for inspection. The **actual cell slots** below the directory move whole cells: click to pick up or place a cell, or Shift-click to move it between the device and your inventory. When editable cell slots are available, Shift-clicking a storage cell from your inventory installs it into the device rather than storing it as an ordinary item.

There are ten cell controls per page. Selecting a later cell in the directory shows its page automatically; the cell-area arrows also change pages. Some addon devices expose cell details without editable slots. Cross-dimensional cell slots cannot be operated remotely.

## Browse comfortably

Use the wheel over the content grid, scrollbar or arrow buttons to browse contents. Drag the scrollbar or click its arrows to move through the list. In a short window, scrolling also reveals rows hidden below the visible grid. The wheel over the directory scrolls the directory instead.

The right search field filters contents by displayed name or identifier, such as `minecraft:iron_ingot`. The directory's search icon opens a separate device-and-cell search. Clear a query to remove its filter; hiding the directory search field keeps its query. The sort button switches between name and quantity.

The theme button switches light and dark appearances and remembers your choice. The controller keeps Minecraft's GUI scale. In narrow windows, the directory button switches between the tree and contents; resizing keeps your search and selection.

## Locate a device

Select a device and click Locate. A loaded target in the same dimension, within **256 blocks**, receives a temporary outline for **15 seconds**. For a storage bus, the target is its connected container. Otherwise, use the shown dimension and coordinates. Locate does not teleport you or load distant chunks.

If the directory reports that it is incomplete, some branches are not displayed. This does not mean their storage has disappeared from the network.
