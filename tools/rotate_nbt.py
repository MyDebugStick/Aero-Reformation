#!/usr/bin/env python3
"""
将 Minecraft Structure NBT 文件绕 Y 轴旋转指定角度。
使用 nbtlib 库处理 NBT 格式。

用法:
    python tools/rotate_nbt.py <输入.nbt> <角度> [输出.nbt]
    
角度: 90, 180, 270 (顺时针)
不指定输出文件则覆盖输入。
"""

import sys
from pathlib import Path

try:
    import nbtlib
except ImportError:
    print("请先安装 nbtlib: pip install nbtlib")
    sys.exit(1)


def rotate_pos(pos, degrees, size_x, size_z):
    """绕 Y 轴旋转方块位置"""
    x, y, z = pos
    if degrees == 90:
        return (z, y, size_x - 1 - x)
    elif degrees == 180:
        return (size_x - 1 - x, y, size_z - 1 - z)
    elif degrees == 270:
        return (size_z - 1 - z, y, x)
    return (x, y, z)


def main():
    if len(sys.argv) < 3:
        print("用法: python tools/rotate_nbt.py <输入.nbt> <角度> [输出.nbt]")
        sys.exit(1)

    input_path = Path(sys.argv[1])
    if not input_path.exists():
        print(f"错误: 文件不存在 {input_path}")
        sys.exit(1)

    try:
        degrees = int(sys.argv[2])
    except ValueError:
        print(f"错误: 无效角度 '{sys.argv[2]}'")
        sys.exit(1)

    if degrees not in (90, 180, 270):
        print(f"错误: 不支持的角度 {degrees}")
        sys.exit(1)

    output_path = Path(sys.argv[3]) if len(sys.argv) >= 4 else input_path

    print(f"NBT 结构旋转工具")
    print(f"  输入: {input_path}")
    print(f"  旋转: {degrees}°")
    print(f"  输出: {output_path}")

    nbt_file = nbtlib.load(input_path)

    blocks = nbt_file.get("blocks", [])
    if not blocks:
        print("  警告: 结构中无方块数据")
        return

    xs = [int(b["pos"][0]) for b in blocks if "pos" in b]
    zs = [int(b["pos"][2]) for b in blocks if "pos" in b]
    if not xs:
        print("  警告: 无有效方块")
        return

    size_x = max(xs) + 1
    size_z = max(zs) + 1

    size = nbt_file.get("size")
    if size and len(size) >= 3:
        size_x = max(size_x, int(size[0]))
        size_z = max(size_z, int(size[2]))

    print(f"  结构大小: {size_x} x {size_z}")
    print(f"  方块数: {len(blocks)}")

    for block in blocks:
        pos = block.get("pos")
        if pos and len(pos) >= 3:
            x, y, z = int(pos[0]), int(pos[1]), int(pos[2])
            nx, ny, nz = rotate_pos((x, y, z), degrees, size_x, size_z)
            pos[0] = nbtlib.tag.Int(nx)
            pos[1] = nbtlib.tag.Int(ny)
            pos[2] = nbtlib.tag.Int(nz)

    if size and len(size) >= 3 and degrees in (90, 270):
        old_x, old_z = int(size[0]), int(size[2])
        size[0] = nbtlib.tag.Int(old_z)
        size[2] = nbtlib.tag.Int(old_x)

    import gzip
    import io
    buf = io.BytesIO()
    nbt_file.save(buf)
    compressed = gzip.compress(buf.getvalue())
    with open(output_path, "wb") as f:
        f.write(compressed)
    print(f"\n✅ 完成! {len(blocks)} 个方块已旋转，已保存到 {output_path}")


if __name__ == "__main__":
    main()
