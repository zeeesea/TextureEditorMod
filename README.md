# 🎨 In-Game Texture Editor Mod

![Fabric](https://img.shields.io/badge/Loader-Fabric-beige?style=for-the-badge&logo=fabric)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.4-green?style=for-the-badge&logo=minecraft)
![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)

**Stop closing Minecraft to edit a single pixel.**

The **Texture Editor Mod** allows you to edit blocks, items, entities, and GUI elements directly inside the game. Whether you are tweaking a resource pack, fixing a stray pixel, or creating pixel art from scratch, you can look at a block, press a key, edit it, and see the changes instantly.

---

## ✨ Key Features

### 🧱 Block Editor
- **Look & Edit**: Aim at any block in the world and press **`R`** (default) to open the editor.
- **Face Selection**: Edit specific faces (Top, Bottom, North, East, etc.) independently.
- **Live Updates**: Changes apply to the world immediately—no resource reload required.

### ⚔️ Item & Entity Editor
- **3D Mob Editing**: Open the editor for any mob (Zombie, Creeper, etc.) to paint directly on its skin texture.
- **Item Textures**: Edit 2D item icons with pixel-perfect precision.
- **Spawn Egg Integration**: Clicking a spawn egg in the browser opens the corresponding mob entity for editing.

### 🖥️ GUI & HUD Editor
- **Full Interface Control**: Edit the Hotbar, Crosshair, Hearts, Hunger, and XP bar.
- **Container Customization**: Redesign the Inventory, Crafting Table, Furnace, Chests, and more.
- **Atlas Support**: Smart handling of both standalone textures and sprite atlases (HUD elements).

### 🛠️ Professional Toolset
- **Drawing Tools**: Pencil, Eraser, Flood Fill, Line Tool, Eyedropper.
- **Navigation**: Zoom (Mouse Wheel) and Pan (Right-Click Drag) for precise detailing.
- **Colors**:
  - Full RGB/HSV Color Picker.
  - Hex Code Input (`#FF0000`).
  - Color History & Palette.
- **Undo/Redo**: Never worry about making a mistake.

### 📦 Export to Resource Pack
- **One-Click Export**: generate a ready-to-use Resource Pack `.zip` containing all your edits.
- **Non-Destructive**: Your edits overlay the currently active resource pack. Resetting a texture restores the original file.

---

## 📸 Screenshots

| Block Editor | Mob Editor |
|:---:|:---:|
| *<img width="2560" height="1440" alt="image" src="https://github.com/user-attachments/assets/0aa63573-d4b9-4e4b-8c1e-6862e765c645" />
* | *<img width="2560" height="1440" alt="image" src="https://github.com/user-attachments/assets/76a166dd-89da-4c57-956b-388ef8a842b2" />
* |

| GUI Editor | Browse Menu |
|:---:|:---:|
| *<img width="2560" height="1440" alt="image" src="https://github.com/user-attachments/assets/cf06af3c-f185-4e50-b4ff-0c631f634945" />
* | *<img width="2560" height="1440" alt="image" src="https://github.com/user-attachments/assets/636f8987-02af-436e-b268-7f52ea4222dc" />
* |

---

## 🎮 How to Use

### Controls
- **`R` (Default)**: Opens the editor for the block or entity you are looking at.

### In the Editor
- **Left Click**: Draw / Use Tool.
- **Right Click + Drag**: Pan the canvas.
- **Scroll Wheel**: Zoom in/out.
- **`Ctrl + Z`**: Undo.
- **`Ctrl + Y`**: Redo.
- **`G`**: Toggle Grid.

### Applying Changes
1. **Apply Live**: Updates the texture in your current game session immediately.
2. **Export Pack**: Saves all your "Applied" textures into a folder/zip in your `.minecraft/resourcepacks` folder.
3. **Reset**: Reverts the texture to the default (Vanilla or your active Resource Pack).

---

## ⚙️ Compatibility

- **Minecraft Version**: 1.21.4
- **Mod Loader**: Fabric Loader
- **Dependencies**: Fabric API

### Resource Packs
This mod works seamlessly with other resource packs.
1. If you have a texture pack enabled, the editor will load *that* texture as the base.
2. When you save, your edit overrides the texture pack visually.
3. When you **Export**, you get a new pack that can be placed on top of your existing ones.

---

## 🚧 Current Limitations & Known Issues

- **Complex Models**: Blocks with complex non-cube models (Beds, Chests, Stairs, Slabs, Fences) are currently filtered out of the editor to prevent texture mapping errors. Support is planned for future updates.
- **Leaf Cull**: When editing leaves, verify "Fast/Fancy" graphics settings if transparency updates look unexpected.

---

## 📥 Installation

On Modrinth

---

Made by zeee
