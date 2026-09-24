#!/usr/bin/env python3
"""Render a binary STL to a PNG (orthographic, z-buffer, flat shading).

Development aid only: not part of the canonical vg3 pipeline.

    python3 tools/render_stl.py model.stl model.png [--size 512] [--az 35] [--el 25]
"""

import argparse
import math
import struct
import zlib


def read_stl(path):
    with open(path, "rb") as f:
        data = f.read()
    if len(data) < 84:
        raise ValueError("not a binary STL")
    count = struct.unpack_from("<I", data, 80)[0]
    triangles = []
    offset = 84
    for _ in range(count):
        values = struct.unpack_from("<12f", data, offset)
        offset += 50
        v0 = values[3:6]
        v1 = values[6:9]
        v2 = values[9:12]
        triangles.append((v0, v1, v2))
    return triangles


def sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def dot(a, b):
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def norm(a):
    length = math.sqrt(dot(a, a))
    if length == 0.0:
        return (0.0, 0.0, 0.0)
    return (a[0] / length, a[1] / length, a[2] / length)


def write_png(path, width, height, pixels):
    def chunk(kind, payload):
        body = kind + payload
        return struct.pack(">I", len(payload)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    raw = bytearray()
    for y in range(height):
        raw.append(0)
        raw += pixels[y * width * 3:(y + 1) * width * 3]
    header = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n")
        f.write(chunk(b"IHDR", header))
        f.write(chunk(b"IDAT", zlib.compress(bytes(raw), 6)))
        f.write(chunk(b"IEND", b""))


def render(triangles, width, height, azimuth, elevation):
    lo = [min(t[i][c] for t in triangles for i in range(3)) for c in range(3)]
    hi = [max(t[i][c] for t in triangles for i in range(3)) for c in range(3)]
    center = [(lo[c] + hi[c]) / 2.0 for c in range(3)]
    extent = max(hi[c] - lo[c] for c in range(3))
    scale = 0.9 * min(width, height) / extent if extent > 0 else 1.0

    az = math.radians(azimuth)
    el = math.radians(elevation)
    cam = (math.cos(el) * math.cos(az), math.cos(el) * math.sin(az), math.sin(el))
    right = norm(cross(cam, (0.0, 0.0, 1.0)))
    if right == (0.0, 0.0, 0.0):
        right = (1.0, 0.0, 0.0)
    up = cross(right, cam)

    depth = [-1e30] * (width * height)
    pixels = bytearray([245, 245, 245] * (width * height))

    def project(point):
        local = sub(point, center)
        x = dot(local, right) * scale + width / 2.0
        y = -dot(local, up) * scale + height / 2.0
        z = dot(local, cam)
        return (x, y, z)

    for v0, v1, v2 in triangles:
        p0 = project(v0)
        p1 = project(v1)
        p2 = project(v2)
        face = norm(cross(sub(v1, v0), sub(v2, v0)))
        facing = dot(face, cam)
        if abs(facing) < 1e-9:
            continue
        light = 0.25 + 0.75 * abs(dot(face, cam))
        red = min(255, int(60 * light + 40))
        green = min(255, int(130 * light + 40))
        blue = min(255, int(210 * light + 40))
        min_x = max(0, int(math.floor(min(p0[0], p1[0], p2[0]))))
        max_x = min(width - 1, int(math.ceil(max(p0[0], p1[0], p2[0]))))
        min_y = max(0, int(math.floor(min(p0[1], p1[1], p2[1]))))
        max_y = min(height - 1, int(math.ceil(max(p0[1], p1[1], p2[1]))))
        area = (p1[0] - p0[0]) * (p2[1] - p0[1]) - (p2[0] - p0[0]) * (p1[1] - p0[1])
        if area == 0.0:
            continue
        inv_area = 1.0 / area
        for y in range(min_y, max_y + 1):
            py = y + 0.5
            for x in range(min_x, max_x + 1):
                px = x + 0.5
                w0 = ((p1[0] - px) * (p2[1] - py) - (p2[0] - px) * (p1[1] - py)) * inv_area
                w1 = ((p2[0] - px) * (p0[1] - py) - (p0[0] - px) * (p2[1] - py)) * inv_area
                w2 = 1.0 - w0 - w1
                if w0 < 0.0 or w1 < 0.0 or w2 < 0.0:
                    continue
                z = w0 * p0[2] + w1 * p1[2] + w2 * p2[2]
                index = y * width + x
                if z > depth[index]:
                    depth[index] = z
                    base = index * 3
                    pixels[base] = red
                    pixels[base + 1] = green
                    pixels[base + 2] = blue

    return pixels


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("input")
    parser.add_argument("output")
    parser.add_argument("--size", type=int, default=512)
    parser.add_argument("--az", type=float, default=35.0)
    parser.add_argument("--el", type=float, default=25.0)
    arguments = parser.parse_args()

    triangles = read_stl(arguments.input)
    pixels = render(triangles, arguments.size, arguments.size, arguments.az, arguments.el)
    write_png(arguments.output, arguments.size, arguments.size, pixels)
    print(f"rendered {len(triangles)} triangles -> {arguments.output}")


if __name__ == "__main__":
    main()
