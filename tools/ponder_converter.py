#!/usr/bin/env python3
"""
╔══════════════════════════════════════════════════════════════════════════════╗
║     Ponderer DSL → Create Native Ponder Java Converter                    ║
║     Ponderer DSL → 机械动力原生 Ponder Java 代码转换脚本                   ║
╚══════════════════════════════════════════════════════════════════════════════╝

� Prerequisites / 使用前准备:
    A. Python 3.8+ (standard library only / 仅标准库, no extra dependencies)
    B. Ponderer scene JSON exported from your game / 从游戏中导出的思索JSON
       Located at / 位于: config/ponderer/scripts/<your_scene>.json
    C. Structure .nbt files from Ponderer / Ponderer的结构NBT文件
       Located at / 位于: config/ponderer/structures/
       Copy them to / 复制到: assets/<your_modid>/ponder/
    D. NeoForge mod project with Create Ponder API as dependency
       / 需要 NeoForge 模组项目并依赖 Create Ponder API
    E. Recommended: Provide Create + Ponderer source code for reference.
       Decompile Create's jar or check GitHub (Creators-of-Create/Create)
       and Ponderer GitHub (Nobodiiiii/Ponderer) to fill gaps.
       / 建议同时查阅 Create 和 Ponderer 源码以查漏补缺。

📋 Usage / 用法:
    python tools/ponder_converter.py <ponderer_scene.json> [output_dir]

📌 Examples / 示例:
    python tools/ponder_converter.py config/ponderer/scripts/my_scene.json
    python tools/ponder_converter.py scene.json src/main/java/.../ponder/

📦 Output / 输出:
    1. xxxScenes.java           — PonderStoryBoard methods (scene logic / 核心场景逻辑)
    2. xxxPonderPlugin.java     — PonderPlugin implementation (registration / 场景注册)
    3. Console hints for registration & structure placement / 控制台注册提示

🛠️ Integration Steps / 安装到模组的步骤:
    1. Move generated .java files to your ponder package / 移动到 ponder 包下
    2. Copy .nbt structure files to assets/<modid>/ponder/
    3. Register storyboards in your PonderPlugin / 在 PonderPlugin 中注册
    4. In FMLClientSetupEvent:
           PonderIndex.addPlugin(new YourPonderPlugin());
       (Do NOT call PonderIndex.reload() — avoid conflicts with other mods)

⚙️ Configuration / 按需配置:
    Before running, adjust these defaults in the script if needed:
    使用前根据需要修改脚本中的以下默认值:

    - self._mod_id = "aero_reformation"         → your mod id / 你的模组ID
    - package_name = "dev...aero_reformation.ponder" → your package / 你的包名
    - offset = 140 (in _convert_show_structure) → camera yaw offset
    - KNOWN_PROPERTY_MAP / KNOWN_VALUE_MAP     → block property mappings
       (Do NOT call PonderIndex.reload() — avoid conflicts with other mods)

⚠️ IMPORTANT NOTES / 重要注意事项:

    1. Block Properties / 方块属性:
       Ponderer scene may set block properties (e.g. face=floor, facing=east)
       that don't exist on the target block.
       Ponderer/Ponderer 场景中的方块属性可能不适用于目标方块。

    2. Missing Block Properties → Crash / 缺少的方块属性导致崩溃:
       If converted code sets a property that doesn't exist on the block,
       Minecraft throws IllegalArgumentException. E.g., create:analog_lever
       has no "face" property. Check set_block steps after conversion.
       转换后请检查 set_block 步骤的 blockProperties。

    3. Text Display & Lang Keys / 文本显示与语言键:
       Ponder's .text(String) and .title(String, String) AUTO-GENERATE
       lang keys internally. If no lang entry exists, the KEY PATH itself
       is displayed (e.g. "aero_reformation.ponder.my_scene.text_1").

       To fix this, add entries to your mod's lang files (en_us.json,
       zh_cn.json, etc.) using the following key format:

       For scene titles / 场景标题:
           "<modid>.ponder.<scene_path>.header": "Title"
           E.g.: "aero_reformation.ponder.sticky_piston.header": "Sticky Piston"

       For text content / 文本内容 (auto-numbered):
           "<modid>.ponder.<scene_path>.text_1": "First text"
           "<modid>.ponder.<scene_path>.text_2": "Second text"
           ...

       To find the exact scene_path, check the generated .java file:
           scene.title("scene_path", "fallback_title");
       
       Ponder 的 .text() 和 .title() 会自动生成 lang key。
       如果不添加 lang 条目，游戏会直接显示键名路径（如
       "aero_reformation.ponder.sticky_piston.text_1"）。
       请按上述格式在 en_us.json / zh_cn.json 中添加对应条目。

    4. KeyFrames / 关键帧:
       attachKeyFrame in the JSON is preserved as scene.addKeyframe() calls.

    5. Camera & View / 相机与视角:
       - show_structure's "rotation": setValue() instantly sets camera yaw.
         An offset is added to align Ponderer's default with Create Ponder's.
       - rotate_camera_y: uses standard rotateCameraY() API for smooth
         animated relative rotation. The Ponderer duration is preserved via
         scene.idle().
       - degreesX (pitch): set via xRotation.setValue() in addInstruction.
       - zoom_scene / camera center: NOT mappable to standard Ponder API.
         Ponder auto-centers on the schematic bounding box.
         scene.idle() is emitted for the duration instead.
       Ponderer 的相机视角设置会尽力转换，但 zoom_scene 的视角中心
       无法映射到 Create Ponder 标准 API，需手动调整 NBT 结构布局。

       ⚠️ Structure NBT files should NOT be pre-rotated!
       Camera rotation is handled entirely by the Ponder system via
       yRotation/xRotation transforms. The schematic .nbt files should
       be exported in their default orientation from Ponderer.
       结构 NBT 文件不要预旋转！相机旋转由 Ponder 系统通过
       yRotation/xRotation 独立控制，结构文件用 Ponderer 默认朝向即可。

    6. Block Properties / 方块属性（可能需要微调）:
       set_block and modify_block_entity_nbt steps may have blockProperties
       (e.g. facing, waterlogged, powered) that are converted to typed
       BlockStateProperties references. If a property doesn't exist on
       the target block, Minecraft will throw an IllegalArgumentException
       at runtime. Adjust the blockProperties in Ponderer or manually
       fix the generated code.
       Ponderer 中的方块属性会转为类型安全的 BlockStateProperties 引用。
       如果目标方块没有该属性，游戏运行时会崩溃，需在 Ponderer 中调整或
       手动修改生成的 Java 代码。

    7. Multiple Structure Files / 多结构文件:
       ✅ Supported! Each scene segment can reference a different structure
       from the "structures" pool. References can be numeric (0-based/1-based
       index) or resource IDs (e.g. "mod:path").
       Structure files go to assets/<modid>/ponder/.
       每个场景段可以引用结构池中的不同结构文件。
       结构文件放在 assets/<modid>/ponder/ 目录下。

    8. 🤖 AI Agent Recommended / 建议搭配 AI Agent 调试:
       Converted scenes may need manual adjustments. Use an AI coding assistant
       to review the generated Java code for correctness.
       转换后的代码可能需要人工检查，建议使用 AI 辅助调试。

🔧 Step Type Mapping / 步骤类型映射表 (24 + 1 types):
    See the mapping table below the StepConverter class.
    详见下方 StepConverter 类上方的映射表。

📦 Dependencies / 依赖: Python 3.8+ (standard library only / 标准库, 无额外依赖)
"""

import json
import os
import re
import sys
from pathlib import Path
from typing import Any, Optional


# ──────────────────────────────────────────────────────────
#  工具函数
# ──────────────────────────────────────────────────────────

def snake_to_pascal(name: str) -> str:
    """snake_case → PascalCase"""
    return "".join(word.capitalize() for word in re.split(r"[_/\s-]+", name))


def scene_id_to_class_name(scene_id: str) -> str:
    """ponderer:my_scene → MyScene"""
    parts = scene_id.split(":")
    path = parts[-1] if len(parts) > 1 else parts[0]
    return snake_to_pascal(path.replace("/", "_"))


def scene_id_to_method_name(scene_id: str, segment_index: int) -> str:
    """生成 Java 方法名"""
    parts = scene_id.split(":")
    path = parts[-1] if len(parts) > 1 else parts[0]
    base = re.sub(r"[^a-zA-Z0-9_]", "_", path.replace("/", "_"))
    return f"{base}_p{segment_index + 1}"


def js_string(text: str) -> str:
    """将字符串转义为 Java 字符串字面量（单行）"""
    escaped = (
        text.replace("\\", "\\\\")
        .replace('"', '\\"')
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
    )
    return f'"{escaped}"'


def triple_quote(text: str) -> str:
    """将多行文本转为 Java 块注释"""
    return f"/* {text} */"


def localized_text(localized: Any, fallback: str = "") -> str:
    """从 LocalizedText 结构中提取文本，优先中文，其次英文"""
    if isinstance(localized, dict):
        return (localized.get("zh_cn") or localized.get("zh_tw")
                or localized.get("zh") or localized.get("en_us")
                or localized.get("en_uk")
                or (list(localized.values())[0] if localized else fallback))
    if isinstance(localized, str):
        return localized
    return fallback


def format_vec3(point: Optional[list]) -> str:
    """[2.5, 1.5, 2.5] → new Vec3(2.5, 1.5, 2.5)"""
    if not point or len(point) < 3:
        return "util.vector().of(2.5, 1.5, 2.5)"
    return f"new Vec3({point[0]}, {point[1]}, {point[2]})"


def format_blockpos(pos: Optional[list]) -> str:
    """[1, 2, 3] → util.grid().at(1, 2, 3)"""
    if not pos or len(pos) < 3:
        return "util.grid().at(0, 0, 0)"
    return f"util.grid().at({int(pos[0])}, {int(pos[1])}, {int(pos[2])})"


def format_selection(pos1: Optional[list], pos2: Optional[list]) -> str:
    """生成 Selection 表达式"""
    p1 = format_blockpos(pos1)
    if pos2 and len(pos2) >= 3 and pos2 != pos1:
        # 检查是否 single block
        if pos1 and len(pos1) >= 3 and pos2[0] == pos1[0] and pos2[1] == pos1[1] and pos2[2] == pos1[2]:
            return f"util.select().position({p1})"
        p2 = format_blockpos(pos2)
        return f"util.select().fromTo({p1}, {p2})"
    if pos1 and len(pos1) >= 3:
        return f"util.select().position({p1})"
    return "util.select().everywhere()"


def format_direction(raw: Optional[str]) -> str:
    """解析方向字符串为 Direction 常量"""
    if not raw:
        return "Direction.DOWN"
    mapping = {
        "up": "Direction.UP",
        "down": "Direction.DOWN",
        "north": "Direction.NORTH",
        "south": "Direction.SOUTH",
        "west": "Direction.WEST",
        "east": "Direction.EAST",
    }
    return mapping.get(raw.lower(), "Direction.DOWN")


def format_pointing(raw: Optional[str]) -> str:
    """解析指向为 Pointing 常量"""
    if not raw:
        return "Pointing.DOWN"
    mapping = {
        "up": "Pointing.UP",
        "down": "Pointing.DOWN",
        "left": "Pointing.LEFT",
        "right": "Pointing.RIGHT",
    }
    return mapping.get(raw.lower(), "Pointing.DOWN")


def format_entrance_direction(raw: Optional[str]) -> str:
    """Map Ponderer entranceAnimation → Create Ponder Direction for showSection
    showSection(sel, dir) = blocks fly in FROM that direction (appear to move opposite)"""
    if not raw:
        return "Direction.DOWN"
    mapping = {
        "down": "Direction.DOWN",
        "up": "Direction.UP",
        "north": "Direction.NORTH",
        "south": "Direction.SOUTH",
        "east": "Direction.EAST",
        "west": "Direction.WEST",
    }
    return mapping.get(raw.lower(), "Direction.DOWN")


def format_palette(raw: Optional[str]) -> Optional[str]:
    """解析颜色为 PonderPalette 常量"""
    if not raw:
        return None
    mapping = {
        "white": "PonderPalette.WHITE",
        "black": "PonderPalette.BLACK",
        "red": "PonderPalette.RED",
        "green": "PonderPalette.GREEN",
        "blue": "PonderPalette.BLUE",
        "input": "PonderPalette.INPUT",
        "output": "PonderPalette.OUTPUT",
        "slow": "PonderPalette.SLOW",
        "medium": "PonderPalette.MEDIUM",
        "fast": "PonderPalette.FAST",
    }
    return mapping.get(raw.lower())


def format_sound_source(raw: Optional[str]) -> str:
    if not raw:
        return "SoundSource.MASTER"
    try:
        return f"SoundSource.{raw.upper()}"
    except Exception:
        return "SoundSource.MASTER"


def resolve_structure_ref(scene_data: dict, ref: Optional[str], index: int) -> str:
    """解析结构引用为 Java 字符串常量"""
    structures = scene_data.get("structures") or []
    if scene_data.get("structure") and not structures:
        structures = [scene_data["structure"]]

    if not ref:
        # 使用默认结构
        ref = structures[0] if structures else "ponder:debug/scene_1"

    # 数字索引
    if ref.isdigit():
        idx = int(ref)
        if 1 <= idx <= len(structures):
            ref = structures[idx - 1]
        elif 0 <= idx < len(structures):
            ref = structures[idx]

    if ":" not in ref:
        ref = f"ponder:{ref}"
    return js_string(ref)


# ──────────────────────────────────────────────────────────
#  步骤转换器
# ──────────────────────────────────────────────────────────

# ═══════════════════════════════════════════════════════════════════════════════
#  Step Type → Create Ponder API Mapping / 步骤类型映射表
# ═══════════════════════════════════════════════════════════════════════════════
#
#  Type / 类型          →  Ponder API Call
# ────────────────────────────────────────────────────────────────────────────
#  show_structure        →  scene.world().showSection() + scaleSceneView()
#                            + getTransform().yRotation.setValue()
#  idle                  →  scene.idle() / scene.idleSeconds()
#  text                  →  scene.overlay().showText().text().pointAt()
#                            .colored().placeNearTarget()
#  shared_text           →  scene.overlay().showText().sharedText().pointAt()
#                            .colored().placeNearTarget()
#  create_entity         →  scene.world().createEntity() + pos/lookAt/NoAI
#  create_item_entity    →  scene.world().createItemEntity(pos, motion, stack)
#  rotate_camera_y       →  scene.rotateCameraY(degrees)
#  show_controls         →  scene.overlay().showControls().leftClick()
#                            /rightClick()/scroll().withItem()
#                            .whileSneaking().whileCTRL()
#  encapsulate_bounds    →  scene.addInstruction(ps -> getBounds().encapsulate())
#  play_sound            →  addInstruction → SoundManager.play(SimpleSoundInstance)
#  set_block             →  scene.world().setBlock() / setBlocks()
#  destroy_block         →  scene.world().destroyBlock() / setBlock(AIR)
#  replace_blocks        →  scene.world().replaceBlocks()
#  hide_section          →  scene.world().hideSection()
#  show_section_and_merge→  showIndependentSection() / showSectionAndMerge()
#  rotate_section        →  scene.world().rotateSection(link, x, y, z, dur)
#  move_section          →  scene.world().moveSection(link, offset, dur)
#  toggle_redstone_power →  scene.world().toggleRedstonePower()
#  modify_block_entity_nbt→ scene.world().modifyBlockEntityNBT(nbt.merge())
#  indicate_redstone     →  scene.effects().indicateRedstone()
#  indicate_success      →  scene.effects().indicateSuccess()
#  clear_entities        →  scene.world().modifyEntities(Entity.class, ...)
#  clear_item_entities   →  scene.world().modifyEntities(ItemEntity.class, ...)
#  highlight_section     →  scene.overlay().showOutline()
#  next_scene            →  (skipped — Ponder handles segmentation automatically)
#
# ═══════════════════════════════════════════════════════════════════════════════

class StepConverter:
    """将单个 DSL 步骤转换为 Java 代码行"""

    # 已知方块属性到 BlockStateProperties 静态字段的映射
    KNOWN_PROPERTY_MAP = {
        "facing": "net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING",
        "face": "net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE",
        "axis": "net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS",
        "powered": "net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED",
        "waterlogged": "net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED",
        "double_face": "net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE",
    }

    # Properties with fallback alternatives (first tried, then fallback)
    PROPERTY_FALLBACKS = {
        "facing": "net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING",
    }

    # 已知属性值到 Minecraft 枚举常量的映射
    KNOWN_VALUE_MAP = {
        "north": "net.minecraft.core.Direction.NORTH", "south": "net.minecraft.core.Direction.SOUTH",
        "east": "net.minecraft.core.Direction.EAST", "west": "net.minecraft.core.Direction.WEST",
        "up": "net.minecraft.core.Direction.UP", "down": "net.minecraft.core.Direction.DOWN",
        "floor": "net.minecraft.world.level.block.state.properties.AttachFace.FLOOR",
        "wall": "net.minecraft.world.level.block.state.properties.AttachFace.WALL",
        "ceiling": "net.minecraft.world.level.block.state.properties.AttachFace.CEILING",
        "x": "net.minecraft.core.Direction.Axis.X", "y": "net.minecraft.core.Direction.Axis.Y", "z": "net.minecraft.core.Direction.Axis.Z",
        "true": "java.lang.Boolean.TRUE", "false": "java.lang.Boolean.FALSE",
    }

    def __init__(self, indent: str = "        "):
        self.indent = indent
        self._section_link_counter = 0
        self._text_counter = 0
        self._scene_path = ""
        self._mod_id = "aero_reformation"
        self.text_entries: dict[str, str] = {}  # key -> text
        self._current_y_rotation: Optional[float] = None  # Track absolute Y rotation

    def reset(self, scene_path: str = "", mod_id: str = "aero_reformation"):
        self._section_link_counter = 0
        self._text_counter = 0
        self._scene_path = scene_path
        self._mod_id = mod_id
        self._current_y_rotation = None

    def convert(self, step: dict, scene_data: dict, segment_index: int) -> str:
        step_type = step.get("type", "")
        method_name = f"_convert_{step_type}"
        converter = getattr(self, method_name, None)
        if converter:
            return converter(step, scene_data, segment_index)
        return f"{self.indent}// TODO: 未知步骤类型 '{step_type}'"

    def _emit_keyframe(self, step: dict) -> str:
        if step.get("attachKeyFrame"):
            return f"{self.indent}scene.addKeyframe();\n"
        return ""

    def _convert_show_structure(self, step: dict, scene_data: dict, seg_idx: int) -> str:
        lines = []
        lines.append(self._emit_keyframe(step))
        rotation = step.get("rotation")
        if rotation is not None:
            offset = 140  # 与 Create Ponder 默认视角的偏移
            total = rotation + offset
            # setValue for instant snap + chase to update chaseTarget for subsequent rotateCameraY
            lines.append(f"{self.indent}scene.addInstruction(ps -> {{")
            lines.append(f"{self.indent}    var yRot = ps.getTransform().yRotation;")
            lines.append(f"{self.indent}    yRot.setValue({total}f);")
            lines.append(f"{self.indent}    yRot.chase({total}f, 0.3f, net.createmod.catnip.animation.LerpedFloat.Chaser.EXP);")
            lines.append(f"{self.indent}}});")
        height = step.get("height")
        height = step.get("height")
        if height is not None and height >= 0:
            lines.append(f"{self.indent}{{")
            lines.append(f"{self.indent}    var sel = util.select().layersFrom({height});")
            lines.append(f"{self.indent}    scene.world().showSection(sel, Direction.UP);")
            lines.append(f"{self.indent}}}")
        else:
            lines.append(f"{self.indent}scene.world().showSection(util.select().everywhere(), Direction.UP);")
        scale = step.get("scale")
        if scale is not None:
            lines.append(f"{self.indent}scene.scaleSceneView({scale}f);")
        return "\n".join(lines)

    def _convert_idle(self, step: dict, scene_data: dict, seg_idx: int) -> str:
        lines = []
        lines.append(self._emit_keyframe(step))
        dur = step.get("duration", 20)
        sec = dur // 20
        if dur >= 20 and dur % 20 == 0 and sec > 0:
            lines.append(f"{self.indent}scene.idleSeconds({sec});")
        else:
            lines.append(f"{self.indent}scene.idle({dur});")
        return "\n".join(lines)

    def _convert_text(self, step: dict, scene_data: dict, seg_idx: int) -> str:
        lines = []
        lines.append(self._emit_keyframe(step))
        text = localized_text(step.get("text"), "")
        dur = step.get("duration", 60)
        point = format_vec3(step.get("point"))

        lines.append(f"{self.indent}{{")
        lines.append(f"{self.indent}    var textBuilder = scene.overlay().showText({dur})")
        lines.append(f"{self.indent}        .text({js_string(text)})")
        lines.append(f"{self.indent}        .pointAt({point});")

        palette = format_palette(step.get("color"))
        if palette:
            lines.append(f"{self.indent}    textBuilder.colored({palette});")
        if step.get("placeNearTarget"):
            lines.append(f"{self.indent}    textBuilder.placeNearTarget();")
        lines.append(f"{self.indent}}}")
        return "\n".join(lines)

    def _convert_shared_text(self, step: dict, scene_data: dict, seg_idx: int) -> str:
        lines = []
        lines.append(self._emit_keyframe(step))
        key = step.get("key", "")
        dur = step.get("duration", 60)
        point = format_vec3(step.get("point"))

        if ":" not in key:
            key = f"ponderer:{key}"

        lines.append(f"{self.indent}{{")
        lines.append(f"{self.indent}    var textBuilder = scene.overlay().showText({dur})")
        lines.append(f"{self.indent}        .sharedText(ResourceLocation.parse({js_string(key)}))")
        lines.append(f"{self.indent}        .pointAt({point});")

        palette = format_palette(step.get("color"))
        if palette:
            lines.append(f"{self.indent}    textBuilder.colored({palette});")
        if step.get("placeNearTarget"):
            lines.append(f"{self.indent}    textBuilder.placeNearTarget();")
        lines.append(f"{self.indent}}}")
        return "\n".join(lines)

    def _convert_create_entity(self, step: dict, scene_data: dict, seg_idx: int) -> str:
        lines = []
        lines.append(self._emit_keyframe(step))
        entity_id = step.get("entity", "minecraft:pig")
        raw_pos = step.get("pos") or step.get("point") or [2.5, 1.5, 2.5]
        pos = format_vec3(raw_pos)
        px, py, pz = raw_pos[0], raw_pos[1], raw_pos[2]

        lines.append(f"{self.indent}scene.world().createEntity(level -> {{")
        lines.append(f"{self.indent}    var type = BuiltInRegistries.ENTITY_TYPE.getOptional(")
        lines.append(f"{self.indent}        ResourceLocation.parse({js_string(entity_id)})).orElse(null);")
        lines.append(f"{self.indent}    if (type == null) return null;")
        lines.append(f"{self.indent}    var entity = type.create(level);")
        lines.append(f"{self.indent}    if (entity != null) {{")
        lines.append(f"{self.indent}        entity.setPosRaw({px}, {py}, {pz});")
        lines.append(f"{self.indent}        if (entity instanceof Mob mob) mob.setNoAi(true);")
        lines.append(f"{self.indent}        entity.setNoGravity(true);")
        lines.append(f"{self.indent}    }}")
        lines.append(f"{self.indent}    return entity;")
        lines.append(f"{self.indent}}});")
        return "\n".join(lines)

    def _convert_create_item_entity(self, step: dict, scene_data: dict, seg_idx: int) -> str:
        lines = []
        lines.append(self._emit_keyframe(step))
        item_id = step.get("item", "minecraft:stick")
        raw_pos = step.get("pos") or step.get("point") or [2.5, 1.5, 2.5]
        raw_mot = step.get("motion") or [0, 0.2, 0]
        count = step.get("count", 1)
        lines.append(f"{self.indent}scene.world().createItemEntity(")
        lines.append(f"{self.indent}    {format_vec3(raw_pos)},")
        lines.append(f"{self.indent}    {format_vec3(raw_mot)},")
        lines.append(f"{self.indent}    new ItemStack(BuiltInRegistries.ITEM.getOptional(")
        lines.append(f"{self.indent}        ResourceLocation.parse({js_string(item_id)})).orElse(Items.STICK), {count}));")
        return "\n".join(lines)

    def _convert_rotate_camera_y(self, step: dict, scene_data: dict, seg_idx: int) -> str:
        lines = []
        lines.append(self._emit_keyframe(step))
        deg = step.get("degrees", 0)
        duration = step.get("duration")
        # Use standard rotateCameraY API for smooth animated relative rotation
        lines.append(f"{self.indent}scene.rotateCameraY({deg}f);")
        if duration:
            lines.append(f"{self.indent}scene.idle({duration});")
        # degreesX is not directly mappable to Create Ponder API
        return "\n".join(lines)

    def _convert_show_controls(self, step: dict, scene_data: dict, seg_idx: int) -> str:
        lines = []
        lines.append(self._emit_keyframe(step))
        point = format_vec3(step.get("point"))
        dur = step.get("duration", 60)
        pointing = format_pointing(step.get("direction"))

        lines.append(f"{self.indent}{{")
        lines.append(f"{self.indent}    var inputBuilder = scene.overlay().showControls({point}, {pointing}, {dur});")

        action = step.get("action", "")
        if action == "left":
            lines.append(f"{self.indent}    inputBuilder.leftClick();")
        elif action == "right":
            lines.append(f"{self.indent}    inputBuilder.rightClick();")
        elif action == "scroll":
            lines.append(f"{self.indent}    inputBuilder.scroll();")

        item_id = step.get("item")
        if item_id:
            lines.append(f"{self.indent}    inputBuilder.withItem(new ItemStack(")
            lines.append(f"{self.indent}        BuiltInRegistries.ITEM.getOptional(")
            lines.append(f"{self.indent}            ResourceLocation.parse({js_string(item_id)})).orElse(Items.STICK)));")
        if step.get("whileSneaking"):
            lines.append(f"{self.indent}    inputBuilder.whileSneaking();")
        if step.get("whileCTRL"):
            lines.append(f"{self.indent}    inputBuilder.whileCTRL();")
        lines.append(f"{self.indent}}}")
        return "\n".join(lines)

    def _convert_encapsulate_bounds(self, step: dict, scene_data: dict, seg_idx: int) -> str:
        lines = []
        lines.append(self._emit_keyframe(step))
        bounds = step.get("bounds", [5, 4, 5])
        lines.append(f"{self.indent}scene.addInstruction(ps ->")
        lines.append(f"{self.indent}    ps.getWorld().getBounds().encapsulate(")
        lines.append(f"{self.indent}        new BlockPos({bounds[0]}, {bounds[1]}, {bounds[2]})));")
        return "\n".join(lines)

    def _convert_play_sound(self, step: dict, scene_data: dict, seg_idx: int) -> str:
        lines = []
        lines.append(self._emit_keyframe(step))
        sound_id = step.get("sound", "")
        volume = step.get("soundVolume", 1.0)
        pitch = step.get("pitch", 1.0)
        source = format_sound_source(step.get("source"))
        lines.append(f"{self.indent}scene.addInstruction(ps -> {{")
        lines.append(f"{self.indent}    var player = Minecraft.getInstance().player;")
        lines.append(f"{self.indent}    if (player == null) return;")
        lines.append(f"{self.indent}    var sound = BuiltInRegistries.SOUND_EVENT.getOptional(")
        lines.append(f"{self.indent}        ResourceLocation.parse({js_string(sound_id)})).orElse(null);")
        lines.append(f"{self.indent}    if (sound == null) return;")
        lines.append(f"{self.indent}    var inst = new SimpleSoundInstance(sound, {source}, {volume}f, {pitch}f,")
        lines.append(f"{self.indent}        SoundInstance.createUnseededRandom(), player.blockPosition());")
        lines.append(f"{self.indent}    Minecraft.getInstance().getSoundManager().play(inst);")
        lines.append(f"{self.indent}}});")
        return "\n".join(lines)

    def _convert_set_block(self, step: dict, scene_data: dict, seg_idx: int) -> str:
        lines = []
        lines.append(self._emit_keyframe(step))
        block_id = step.get("block", "minecraft:stone")
        pos1 = step.get("blockPos")
        pos2 = step.get("blockPos2")
        particles = step.get("spawnParticles", True)
        is_region = pos2 and len(pos2) >= 3 and (pos2[0] != pos1[0] or pos2[1] != pos1[1] or pos2[2] != pos1[2])

        # Entrance animation support
        entrance_anim = step.get("entranceAnimation")
        has_entrance = entrance_anim is not None and entrance_anim.lower() != "none"
        entrance_dir_str = step.get("direction", entrance_anim or "down")
        entrance_dir = format_entrance_direction(entrance_dir_str) if has_entrance else "Direction.DOWN"

        if has_entrance:
            # Erase position from base section to prevent overlap with animated section
            lines.append(f"{self.indent}// Erase original block from base section / 擦除原位方块防止重叠")
            lines.append(f"{self.indent}scene.addInstruction(ps -> {{")
            lines.append(f"{self.indent}    ps.getBaseWorldSection().erase({format_selection(pos1, pos2)});")
            lines.append(f"{self.indent}    ps.getBaseWorldSection().queueRedraw();")
            lines.append(f"{self.indent}}});")

        lines.append(f"{self.indent}{{")
        lines.append(f"{self.indent}    var state = BuiltInRegistries.BLOCK.getOptional(")
        lines.append(f"{self.indent}        ResourceLocation.parse({js_string(block_id)})).orElse(Blocks.STONE).defaultBlockState();")
        props = step.get("blockProperties")
        if props:
            # 先放置方块，再通过 modifyBlock 应用属性
            lines.append(f"{self.indent}    scene.world().setBlock({format_blockpos(pos1)}, state, {str(particles).lower()});")
            lines.append(f"{self.indent}    // Apply block properties with typed references / 应用方块属性")
            for prop_key, prop_val in props.items():
                prop_ref = self.KNOWN_PROPERTY_MAP.get(prop_key)
                val_ref = self.KNOWN_VALUE_MAP.get(prop_val.lower() if isinstance(prop_val, str) else prop_val)
                if prop_ref and val_ref:
                    fallback_ref = self.PROPERTY_FALLBACKS.get(prop_key)
                    if fallback_ref:
                        lines.append(f"{self.indent}    scene.world().modifyBlock({format_blockpos(pos1)}, s -> s.hasProperty({prop_ref}) ? s.setValue({prop_ref}, {val_ref}) : s.hasProperty({fallback_ref}) ? s.setValue({fallback_ref}, {val_ref}) : s, false);")
                    else:
                        lines.append(f"{self.indent}    scene.world().modifyBlock({format_blockpos(pos1)}, s -> s.hasProperty({prop_ref}) ? s.setValue({prop_ref}, {val_ref}) : s, false);")
                else:
                    lines.append(f"{self.indent}    scene.world().modifyBlock({format_blockpos(pos1)}, s -> {{")
                    lines.append(f"{self.indent}        net.minecraft.world.level.block.state.properties.Property rawP = s.getBlock().getStateDefinition().getProperty({js_string(prop_key)});")
                    lines.append(f"{self.indent}        if (rawP != null) {{")
                    lines.append(f"{self.indent}            var rawV = rawP.getValue({js_string(prop_val)});")
                    lines.append(f"{self.indent}            if (rawV.isPresent())")
                    lines.append(f"{self.indent}                return s.setValue(rawP, (java.lang.Comparable) rawV.get());")
                    lines.append(f"{self.indent}        }}")
                    lines.append(f"{self.indent}        return s;")
                    lines.append(f"{self.indent}    }}, false);")
            # 有属性时，region 模式也需要 setBlocks
            if is_region:
                lines.append(f"{self.indent}    scene.world().setBlocks({format_selection(pos1, pos2)}, state, {str(particles).lower()});")
        else:
            if is_region:
                lines.append(f"{self.indent}    scene.world().setBlocks({format_selection(pos1, pos2)}, state, {str(particles).lower()});")
            else:
                lines.append(f"{self.indent}    scene.world().setBlock({format_blockpos(pos1)}, state, {str(particles).lower()});")
        lines.append(f"{self.indent}}}")

        # Handle NBT data on set_block (e.g. analog lever State)
        nbt_str = step.get("nbt")
        if nbt_str and pos1 and len(pos1) >= 3:
            lines.append(f"{self.indent}scene.world().modifyBlockEntityNBT(util.select().position({format_blockpos(pos1)}), BlockEntity.class, nbt -> {{")
            lines.append(f"{self.indent}    try {{")
            lines.append(f"{self.indent}        nbt.merge(TagParser.parseTag({js_string(nbt_str)}));")
            lines.append(f"{self.indent}    }} catch (Exception ignored) {{}}")
            lines.append(f"{self.indent}}}, false);")

        if has_entrance:
            # Entrance animation: show as independent section with fly-in effect
            lines.append(f"{self.indent}// Entrance animation / 入场动画: 独立 section 从 {entrance_anim} 方向飞入")
            lines.append(f"{self.indent}scene.world().showIndependentSection({format_selection(pos1, pos2)}, {entrance_dir});")

        return "\n".join(lines)

    def _convert_destroy_block(self, step: dict, scene_data: dict, seg_idx: int) -> str:
        lines = []
        lines.append(self._emit_keyframe(step))
        pos = step.get("blockPos")
        particles = step.get("destroyParticles", True)
        if particles:
            lines.append(f"{self.indent}scene.world().destroyBlock({format_blockpos(pos)});")
        else:
            lines.append(f"{self.indent}scene.world().setBlock({format_blockpos(pos)}, Blocks.AIR.defaultBlockState(), false);")
        return "\n".join(lines)

    def _convert_replace_blocks(self, step: dict, scene_data: dict, seg_idx: int) -> str:
        lines = []
        lines.append(self._emit_keyframe(step))
        block_id = step.get("block", "minecraft:glass")
        pos1 = step.get("blockPos")
        pos2 = step.get("blockPos2")
        particles = step.get("spawnParticles", True)
        sel = format_selection(pos1, pos2)
        lines.append(f"{self.indent}{{")
        lines.append(f"{self.indent}    var state = BuiltInRegistries.BLOCK.getOptional(")
        lines.append(f"{self.indent}        ResourceLocation.parse({js_string(block_id)})).orElse(Blocks.GLASS).defaultBlockState();")
        lines.append(f"{self.indent}    scene.world().replaceBlocks({sel}, state, {str(particles).lower()});")
        lines.append(f"{self.indent}}}")
        return "\n".join(lines)

    def _convert_hide_section(self, step: dict, scene_data: dict, seg_idx: int) -> str:
        lines = []
        lines.append(self._emit_keyframe(step))
        pos1 = step.get("blockPos")
        pos2 = step.get("blockPos2")
        sel = format_selection(pos1, pos2)
        dir_str = format_direction(step.get("direction"))
        lines.append(f"{self.indent}scene.world().hideSection({sel}, {dir_str});")
        return "\n".join(lines)

    def _convert_show_section_and_merge(self, step: dict, scene_data: dict, seg_idx: int) -> str:
        lines = []
        lines.append(self._emit_keyframe(step))
        pos1 = step.get("blockPos")
        pos2 = step.get("blockPos2")
        sel = format_selection(pos1, pos2)
        dir_str = format_direction(step.get("direction"))
        link_id = step.get("linkId", "default")
        var_name = f"link_{link_id.replace('-', '_')}"

        lines.append(f"{self.indent}ElementLink<WorldSectionElement> {var_name};")
        lines.append(f"{self.indent}if (links.containsKey({js_string(link_id)})) {{")
        lines.append(f"{self.indent}    {var_name} = scene.world().showSectionAndMerge(")
        lines.append(f"{self.indent}        {sel}, {dir_str}, links.get({js_string(link_id)}));")
        lines.append(f"{self.indent}}} else {{")
        lines.append(f"{self.indent}    {var_name} = scene.world().showIndependentSection({sel}, {dir_str});")
        lines.append(f"{self.indent}    links.put({js_string(link_id)}, {var_name});")
        lines.append(f"{self.indent}}}")
        return "\n".join(lines)

    def _convert_rotate_section(self, step: dict, scene_data: dict, seg_idx: int) -> str:
        lines = []
        lines.append(self._emit_keyframe(step))
        link_id = step.get("linkId", "default")
        rx = step.get("rotX", 0)
        ry = step.get("rotY") if step.get("rotY") is not None else step.get("degrees", 0)
        rz = step.get("rotZ", 0)
        dur = step.get("duration", 20)
        lines.append(f"{self.indent}var link_{link_id.replace('-', '_')} = links.get({js_string(link_id)});")
        lines.append(f"{self.indent}if (link_{link_id.replace('-', '_')} != null)")
        lines.append(f"{self.indent}    scene.world().rotateSection(link_{link_id.replace('-', '_')}, {rx}, {ry}, {rz}, {dur});")
        return "\n".join(lines)

    def _convert_move_section(self, step: dict, scene_data: dict, seg_idx: int) -> str:
        lines = []
        lines.append(self._emit_keyframe(step))
        link_id = step.get("linkId", "default")
        offset = step.get("offset", [0, 1, 0])
        dur = step.get("duration", 20)
        lines.append(f"{self.indent}var link_{link_id.replace('-', '_')} = links.get({js_string(link_id)});")
        lines.append(f"{self.indent}if (link_{link_id.replace('-', '_')} != null)")
        lines.append(f"{self.indent}    scene.world().moveSection(link_{link_id.replace('-', '_')}, new Vec3({offset[0]}, {offset[1]}, {offset[2]}), {dur});")
        return "\n".join(lines)

    def _convert_toggle_redstone_power(self, step: dict, scene_data: dict, seg_idx: int) -> str:
        lines = []
        lines.append(self._emit_keyframe(step))
        pos1 = step.get("blockPos")
        pos2 = step.get("blockPos2")
        sel = format_selection(pos1, pos2)
        lines.append(f"{self.indent}scene.world().toggleRedstonePower({sel});")
        return "\n".join(lines)

    def _convert_modify_block_entity_nbt(self, step: dict, scene_data: dict, seg_idx: int) -> str:
        lines = []
        lines.append(self._emit_keyframe(step))
        pos1 = step.get("blockPos")
        pos2 = step.get("blockPos2")
        sel = format_selection(pos1, pos2)
        nbt_str = step.get("nbt", "{}")
        redraw = step.get("reDrawBlocks", False)
        # Apply block properties first (if any)
        props = step.get("blockProperties")
        if props and pos1 and len(pos1) >= 3:
            for prop_key, prop_val in props.items():
                prop_ref = self.KNOWN_PROPERTY_MAP.get(prop_key)
                val_ref = self.KNOWN_VALUE_MAP.get(prop_val.lower() if isinstance(prop_val, str) else prop_val)
                if prop_ref and val_ref:
                    fallback_ref = self.PROPERTY_FALLBACKS.get(prop_key)
                    if fallback_ref:
                        lines.append(f"{self.indent}scene.world().modifyBlock({format_blockpos(pos1)}, s -> s.hasProperty({prop_ref}) ? s.setValue({prop_ref}, {val_ref}) : s.hasProperty({fallback_ref}) ? s.setValue({fallback_ref}, {val_ref}) : s, false);")
                    else:
                        lines.append(f"{self.indent}scene.world().modifyBlock({format_blockpos(pos1)}, s -> s.hasProperty({prop_ref}) ? s.setValue({prop_ref}, {val_ref}) : s, false);")
                else:
                    lines.append(f"{self.indent}scene.world().modifyBlock({format_blockpos(pos1)}, s -> {{")
                    lines.append(f"{self.indent}    net.minecraft.world.level.block.state.properties.Property rawP = s.getBlock().getStateDefinition().getProperty({js_string(prop_key)});")
                    lines.append(f"{self.indent}    if (rawP != null) {{")
                    lines.append(f"{self.indent}        var rawV = rawP.getValue({js_string(prop_val)});")
                    lines.append(f"{self.indent}        if (rawV.isPresent())")
                    lines.append(f"{self.indent}            return s.setValue(rawP, (java.lang.Comparable) rawV.get());")
                    lines.append(f"{self.indent}    }}")
                    lines.append(f"{self.indent}    return s;")
                    lines.append(f"{self.indent}}}, false);")
        # Then modify block entity NBT
        lines.append(f"{self.indent}scene.world().modifyBlockEntityNBT({sel}, BlockEntity.class, nbt -> {{")
        lines.append(f"{self.indent}    try {{")
        lines.append(f"{self.indent}        nbt.merge(TagParser.parseTag({js_string(nbt_str)}));")
        lines.append(f"{self.indent}    }} catch (Exception ignored) {{}}")
        lines.append(f"{self.indent}}}, {str(redraw).lower()});")
        return "\n".join(lines)

    def _convert_indicate_redstone(self, step: dict, scene_data: dict, seg_idx: int) -> str:
        lines = []
        lines.append(self._emit_keyframe(step))
        pos = step.get("blockPos", [0, 0, 0])
        lines.append(f"{self.indent}scene.effects().indicateRedstone({format_blockpos(pos)});")
        return "\n".join(lines)

    def _convert_indicate_success(self, step: dict, scene_data: dict, seg_idx: int) -> str:
        lines = []
        lines.append(self._emit_keyframe(step))
        pos = step.get("blockPos", [0, 0, 0])
        lines.append(f"{self.indent}scene.effects().indicateSuccess({format_blockpos(pos)});")
        return "\n".join(lines)

    def _convert_zoom_scene(self, step: dict, scene_data: dict, seg_idx: int) -> str:
        lines = []
        lines.append(self._emit_keyframe(step))
        duration = step.get("duration", 20)
        point = step.get("point")
        if point and len(point) >= 3:
            px, py, pz = point[0], point[1], point[2]
            # Calculate base plate offset to shift camera center to desired point
            # Camera default center is at (basePlateSize/2, 0, basePlateSize/2)
            # Using reflection to modify PonderScene.basePlateOffsetX/Z at runtime
            lines.append(f"{self.indent}// Move camera center to ({px}, {py}, {pz}) via reflection")
            lines.append(f"{self.indent}scene.addInstruction(ps -> {{")
            lines.append(f"{self.indent}    try {{")
            lines.append(f"{self.indent}        var sceneClass = Class.forName(\"net.createmod.ponder.foundation.PonderScene\");")
            lines.append(f"{self.indent}        var offsetXField = sceneClass.getDeclaredField(\"basePlateOffsetX\");")
            lines.append(f"{self.indent}        var offsetZField = sceneClass.getDeclaredField(\"basePlateOffsetZ\");")
            lines.append(f"{self.indent}        offsetXField.setAccessible(true);")
            lines.append(f"{self.indent}        offsetZField.setAccessible(true);")
            # Calculate new offsets: camera center shifts opposite to plate offset
            new_ox = max(0, int(px - 2.5))  # rough: 2.5 is center of 5-wide plate
            new_oz = max(0, int(pz - 2.5))
            lines.append(f"{self.indent}        offsetXField.setInt(ps, {new_ox});")
            lines.append(f"{self.indent}        offsetZField.setInt(ps, {new_oz});")
            lines.append(f"{self.indent}    }} catch (Exception e) {{}}")
            lines.append(f"{self.indent}}});")
            lines.append(f"{self.indent}scene.setSceneOffsetY({py}f);")
        lines.append(f"{self.indent}scene.idle({duration});")
        return "\n".join(lines)

    def _convert_next_scene(self, step: dict, scene_data: dict, seg_idx: int) -> str:
        return f"{self.indent}// next_scene — 该步骤由 Create 自动处理分段跳转"

    def _convert_clear_entities(self, step: dict, scene_data: dict, seg_idx: int) -> str:
        lines = []
        lines.append(self._emit_keyframe(step))
        full = step.get("fullScene", False)
        entity_filter = step.get("entity")
        if entity_filter:
            lines.append(f"{self.indent}var filter = ResourceLocation.parse({js_string(entity_filter)});")
        if full:
            lines.append(f"{self.indent}scene.world().modifyEntities(Entity.class, entity -> {{")
        else:
            pos1 = step.get("blockPos")
            pos2 = step.get("blockPos2")
            sel = format_selection(pos1, pos2)
            lines.append(f"{self.indent}scene.world().modifyEntitiesInside(Entity.class, {sel}, entity -> {{")
        if entity_filter:
            lines.append(f"{self.indent}    if (EntityType.getKey(entity.getType()).equals(filter))")
            lines.append(f"{self.indent}        entity.discard();")
        else:
            lines.append(f"{self.indent}    if (!(entity instanceof ItemEntity))")
            lines.append(f"{self.indent}        entity.discard();")
        lines.append(f"{self.indent}}});")
        return "\n".join(lines)

    def _convert_highlight_section(self, step: dict, scene_data: dict, seg_idx: int) -> str:
        lines = []
        lines.append(self._emit_keyframe(step))
        pos1 = step.get("blockPos")
        pos2 = step.get("blockPos2")
        sel = format_selection(pos1, pos2)
        dur = step.get("duration", 40)
        color = format_palette(step.get("color")) or "PonderPalette.WHITE"
        lines.append(f"{self.indent}scene.overlay().showOutline({color}, "
                     f"ResourceLocation.parse({js_string('highlight_' + str(seg_idx))}), {sel}, {dur});")
        return "\n".join(lines)

    def _convert_clear_item_entities(self, step: dict, scene_data: dict, seg_idx: int) -> str:
        lines = []
        lines.append(self._emit_keyframe(step))
        full = step.get("fullScene", False)
        item_filter = step.get("item")
        if full:
            lines.append(f"{self.indent}scene.world().modifyEntities(ItemEntity.class, entity -> {{")
        else:
            pos1 = step.get("blockPos")
            pos2 = step.get("blockPos2")
            sel = format_selection(pos1, pos2)
            lines.append(f"{self.indent}scene.world().modifyEntitiesInside(ItemEntity.class, {sel}, entity -> {{")
        if item_filter:
            lines.append(f"{self.indent}    if (BuiltInRegistries.ITEM.getKey(entity.getItem().getItem()).equals(ResourceLocation.parse({js_string(item_filter)})))")
            lines.append(f"{self.indent}        entity.discard();")
        else:
            lines.append(f"{self.indent}    entity.discard();")
        lines.append(f"{self.indent}}});")
        return "\n".join(lines)


# ──────────────────────────────────────────────────────────
#  Java 代码生成器
# ──────────────────────────────────────────────────────────

class JavaCodeGenerator:
    """将完整的 Ponderer DSL JSON 生成为 Java 场景文件"""

    def __init__(self, scene_data: dict, package_name: str = "dev.simulated_team.aero_reformation.ponder"):
        self.data = scene_data
        self.package = package_name
        self.scene_id = scene_data.get("id", "unknown:scene")
        self.class_name = scene_id_to_class_name(self.scene_id)
        self.converter = StepConverter()

    def generate_scenes_class(self) -> str:
        """生成 xxxScenes.java - 场景故事板方法定义"""
        scenes = self.data.get("scenes", [])
        class_name = f"{self.class_name}Scenes"
        method_defs = []

        # 收集所有用到的 linkId
        has_section_ops = any(
            step.get("type") in ("show_section_and_merge", "rotate_section", "move_section")
            for seg in scenes for step in seg.get("steps", [])
        )

        for idx, segment in enumerate(scenes):
            method_defs.append(self._generate_method(segment, idx, has_section_ops))

        methods_joined = "\n\n".join(method_defs)

        return f"""package {self.package};

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * 由 Ponderer 场景自动生成的 Ponder 故事板
 * 源场景: {self.scene_id}
 * 源标题: {localized_text(self.data.get("title"), "")}
 * 生成时间: {__import__('datetime').datetime.now().strftime('%Y-%m-%d %H:%M:%S')}
 */
public class {class_name} {{

{methods_joined}
}}
"""

    def _generate_method(self, segment: dict, index: int, has_section_ops: bool) -> str:
        """生成单个场景段的方法"""
        method_name = scene_id_to_method_name(self.scene_id, index)
        seg_id = segment.get("id", f"page_{index + 1}")
        seg_title = localized_text(segment.get("title"), f"Page {index + 1}")
        steps = segment.get("steps", [])
        total = len(self.data.get("scenes", []))

        # 构建标题路径
        base_id = self.scene_id.split(":")[-1] if ":" in self.scene_id else self.scene_id
        scene_path = f"{base_id}_{seg_id}" if total > 1 else base_id

        lines = [f"    public static void {method_name}(SceneBuilder builder, SceneBuildingUtil util) {{"]
        lines.append(f"        // {seg_title}")
        lines.append(f"        var scene = new CreateSceneBuilder(builder);")
        lines.append(f"        scene.title({js_string(scene_path)}, {js_string(seg_title)});")
        lines.append(f"        scene.configureBasePlate(0, 0, 5);")

        # 检查是否以 show_structure 开头
        first_show = next((s for s in steps if s.get("type") != "idle"), None)
        if not first_show or first_show.get("type") != "show_structure":
            lines.append(f"        // 自动显示结构")
            lines.append(f"        scene.world().showSection(util.select().everywhere(), Direction.UP);")
            lines.append(f"        scene.idle(20);")

        # section links 管理
        if has_section_ops:
            lines.append(f"        Map<String, ElementLink<WorldSectionElement>> links = new HashMap<>();")

        self.converter.reset(scene_path, mod_id="aero_reformation")

        for step in steps:
            if step.get("type") == "next_scene":
                continue
            code = self.converter.convert(step, self.data, index)
            if code.strip():
                lines.append("")
                lines.append(code)

        lines.append(f"    }}")
        return "\n".join(lines)

    def generate_plugin_class(self, mod_id: str = "aero_reformation") -> str:
        """生成 xxxPonderPlugin.java - PonderPlugin 实现"""
        plugin_name = f"{self.class_name}PonderPlugin"
        scenes = self.data.get("scenes", [])
        total = len(scenes)
        class_name = f"{self.class_name}Scenes"

        reg_lines = []
        items = self.data.get("items", [])

        for idx, segment in enumerate(scenes):
            method_name = scene_id_to_method_name(self.scene_id, idx)
            schematic = resolve_structure_ref(self.data,
                next((s.get("structure") for s in segment.get("steps", []) if s.get("type") == "show_structure"), None),
                idx)

            for item in items:
                reg_lines.append(f"        helper.forComponents(ResourceLocation.parse({js_string(item)}))")
                reg_lines.append(f"            .addStoryBoard({schematic}, {class_name}::{method_name});")

        if not reg_lines:
            reg_lines.append(f"        // TODO: 注册场景到物品")
            for item in items:
                reg_lines.append(f"        // helper.forComponents(ResourceLocation.parse({js_string(item)}))")
                reg_lines.append(f"        //     .addStoryBoard(ResourceLocation.parse({js_string('ponder:debug/scene_1')}), {class_name}::{method_name}_0);")

        tags = self.data.get("tags", [])
        tag_var = ""
        if tags:
            tag_items = ", ".join(f"ResourceLocation.parse({js_string(t)})" for t in tags)
            tag_var = f"\n        ResourceLocation[] tags = new ResourceLocation[]{{{tag_items}}};"

        return f"""package {self.package};

import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.SharedTextRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * 由 Ponderer 场景自动生成的 PonderPlugin
 * 源场景: {self.scene_id}
 */
public class {plugin_name} implements PonderPlugin {{

    @Override
    public String getModId() {{
        return {js_string(mod_id)};
    }}

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {{
{chr(10).join(reg_lines)}
    }}

    @Override
    public void registerSharedText(SharedTextRegistrationHelper helper) {{
        // TODO: 如有 shared_text 步骤，在此注册共享文本
    }}
}}
"""

    def get_text_entries(self) -> dict:
        """获取所有转换过程中收集的文本条目"""
        return self.converter.text_entries

    def generate_lang_json(self, locale: str = "zh_cn") -> str:
        """生成语言文件 JSON 片段"""
        entries = self.get_text_entries()
        if not entries:
            return ""
        items = []
        for k, v in entries.items():
            escaped_v = v.replace("\\", "\\\\").replace('"', '\\"')
            items.append(f'  "{k}": "{escaped_v}"')
        return "{\n" + ",\n".join(items) + "\n}\n"

    def generate_all_scenes_class(self, plugin_names: list[str]) -> str:
        """生成 AllPonderScenes.java - 场景注册入口"""
        registration_calls = []
        for pn in plugin_names:
            registration_calls.append(f"        helper.forComponents(/* TODO: 添加物品 */)")
            registration_calls.append(f"            .addStoryBoard(ResourceLocation.parse(\"ponder:debug/scene_1\"), {pn}Scenes::/* TODO: 方法名 */);")

        return f"""package {self.package};

import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * All Ponder scenes registration.
 * 由 Ponderer 转换脚本生成的桩代码。
 * 请根据实际物品注册名补全 forComponents() 调用。
 */
public class AllPonderScenes {{

    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {{
{chr(10).join(registration_calls)}
    }}

    private AllPonderScenes() {{}}
}}
"""


# ──────────────────────────────────────────────────────────
#  主入口
# ──────────────────────────────────────────────────────────

def main():
    if len(sys.argv) < 2:
        print("用法: python tools/ponder_converter.py <ponderer_scene.json> [输出目录]")
        print("")
        print("示例:")
        print("    python tools/ponder_converter.py config/ponderer/scripts/my_scene.json")
        print("    python tools/ponder_converter.py config/ponderer/scripts/my_scene.json src/main/java/dev/simulated_team/aero_reformation/ponder")
        sys.exit(1)

    input_path = Path(sys.argv[1])
    if not input_path.exists():
        print(f"错误: 文件不存在: {input_path}")
        sys.exit(1)

    # 输出目录
    if len(sys.argv) >= 3:
        output_dir = Path(sys.argv[2])
    else:
        output_dir = input_path.parent / "generated_java"

    # 加载 JSON
    with open(input_path, "r", encoding="utf-8") as f:
        scene_data = json.load(f)

    scene_id = scene_data.get("id", "unknown:scene")
    print(f"📦 场景: {scene_id}")
    print(f"   ├─ 标题: {localized_text(scene_data.get('title'), 'N/A')}")
    print(f"   ├─ 物品: {', '.join(scene_data.get('items', []))}")
    print(f"   ├─ 结构池: {scene_data.get('structures', [])}")
    print(f"   └─ 场景段: {len(scene_data.get('scenes', []))}")

    # 确保输出目录存在
    output_dir.mkdir(parents=True, exist_ok=True)

    # 生成代码
    generator = JavaCodeGenerator(scene_data)
    class_name = scene_id_to_class_name(scene_id)

    # 1. 场景方法类
    scenes_file = output_dir / f"{class_name}Scenes.java"
    scenes_code = generator.generate_scenes_class()
    scenes_file.write_text(scenes_code, encoding="utf-8")
    print(f"\n✅ 生成: {scenes_file}")

    # 2. 插件类
    plugin_file = output_dir / f"{class_name}PonderPlugin.java"
    plugin_code = generator.generate_plugin_class()
    plugin_file.write_text(plugin_code, encoding="utf-8")
    print(f"✅ 生成: {plugin_file}")

    # 3. 提示注册方式
    items = scene_data.get("items", [])
    scenes = scene_data.get("scenes", [])
    print(f"\n📋 下一步:")
    print(f"   1) 将生成的 .java 文件移动到你的 mod 的 ponder 包下")
    print(f"   2) 在 CreatePonderPlugin 或你的 PonderPlugin 中注册:")
    for item in items:
        print(f"      helper.forComponents(ResourceLocation.parse(\"{item}\"))")
        for idx in range(len(scenes)):
            m = scene_id_to_method_name(scene_id, idx)
            print(f"          .addStoryBoard(ResourceLocation.parse(\"...\"), {class_name}Scenes::{m});")

    # 4. 提示结构文件
    structures = scene_data.get("structures", [])
    if structures:
        print(f"\n   📌 别忘了将 NBT 结构文件复制到:")
        print(f"      assets/<your_mod_id>/ponder/ 目录下")
        for s in structures:
            print(f"      - {s}.nbt")
        print(f"\n   🔍 结构文件通常在:")
        print(f"      config/ponderer/structures/ 或")
        print(f"      与 {input_path.name} 同目录下")

    print(f"\n✨ 完成!")


if __name__ == "__main__":
    main()
