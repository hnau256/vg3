use std::io::Write;
use std::path::Path;

use flate2::write::ZlibEncoder;
use flate2::Compression;

use crate::error::Result;

#[derive(Clone, Copy, Debug)]
pub struct RenderOptions {
    pub size: u32,
    pub azimuth: f64,
    pub elevation: f64,
    /// DEFLATE level for the PNG IDAT stream (`0` = stored, `9` = best).
    pub compression: u32,
}

impl Default for RenderOptions {
    fn default() -> Self {
        RenderOptions {
            size: 512,
            azimuth: 35.0,
            elevation: 25.0,
            compression: 6,
        }
    }
}

/// One shape to draw: its triangles, plus an optional RGB color (`0..1`). `None` uses the default.
pub struct Item {
    pub color: Option<[f64; 3]>,
    pub triangles: Vec<f64>,
}

const DEFAULT_COLOR: [f64; 3] = [0.24, 0.51, 0.82];

pub fn render_png(items: &[Item], path: &Path, options: &RenderOptions) -> Result<()> {
    let size = options.size.clamp(1, 4096);
    let pixels = rasterize(items, size, options.azimuth, options.elevation);
    let bytes = encode_png(size, size, &pixels, options.compression);
    std::fs::write(path, bytes)?;
    Ok(())
}

fn sub(a: [f64; 3], b: [f64; 3]) -> [f64; 3] {
    [a[0] - b[0], a[1] - b[1], a[2] - b[2]]
}

fn cross(a: [f64; 3], b: [f64; 3]) -> [f64; 3] {
    [
        a[1] * b[2] - a[2] * b[1],
        a[2] * b[0] - a[0] * b[2],
        a[0] * b[1] - a[1] * b[0],
    ]
}

fn dot(a: [f64; 3], b: [f64; 3]) -> f64 {
    a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
}

fn normalized(a: [f64; 3]) -> [f64; 3] {
    let length = dot(a, a).sqrt();
    if length == 0.0 {
        [0.0, 0.0, 0.0]
    } else {
        [a[0] / length, a[1] / length, a[2] / length]
    }
}

fn rasterize(items: &[Item], size: u32, azimuth: f64, elevation: f64) -> Vec<u8> {
    let pixel_count = (size as usize) * (size as usize);
    let mut pixels = vec![245u8; pixel_count * 3];

    // Bounding box of the whole scene.
    let mut low = [f64::INFINITY; 3];
    let mut high = [f64::NEG_INFINITY; 3];
    for item in items {
        for point in item.triangles.chunks_exact(3) {
            for axis in 0..3 {
                low[axis] = low[axis].min(point[axis]);
                high[axis] = high[axis].max(point[axis]);
            }
        }
    }
    if !low[0].is_finite() {
        return pixels;
    }

    let center = [
        (low[0] + high[0]) / 2.0,
        (low[1] + high[1]) / 2.0,
        (low[2] + high[2]) / 2.0,
    ];
    let extent = (0..3)
        .map(|axis| high[axis] - low[axis])
        .fold(0.0_f64, f64::max)
        .max(1e-9);
    let scale = 0.9 * size as f64 / extent;

    let azimuth = azimuth.to_radians();
    let elevation = elevation.to_radians();
    let camera = [
        elevation.cos() * azimuth.cos(),
        elevation.cos() * azimuth.sin(),
        elevation.sin(),
    ];
    let mut right = normalized(cross(camera, [0.0, 0.0, 1.0]));
    if right == [0.0, 0.0, 0.0] {
        right = [1.0, 0.0, 0.0];
    }
    let up = cross(right, camera);

    let project = |point: [f64; 3]| -> [f64; 3] {
        let local = sub(point, center);
        [
            dot(local, right) * scale + size as f64 / 2.0,
            -dot(local, up) * scale + size as f64 / 2.0,
            dot(local, camera),
        ]
    };

    let mut depth = vec![f64::NEG_INFINITY; pixel_count];
    for item in items {
        let color = item.color.unwrap_or(DEFAULT_COLOR);
        for triangle in item.triangles.chunks_exact(9) {
            let v0 = [triangle[0], triangle[1], triangle[2]];
            let v1 = [triangle[3], triangle[4], triangle[5]];
            let v2 = [triangle[6], triangle[7], triangle[8]];
            let p0 = project(v0);
            let p1 = project(v1);
            let p2 = project(v2);

            let face = normalized(cross(sub(v1, v0), sub(v2, v0)));
            let facing = dot(face, camera);
            if facing.abs() < 1e-12 {
                continue;
            }
            let light = 0.4 + 0.6 * facing.abs();
            let red = (color[0] * light * 255.0).clamp(0.0, 255.0) as u8;
            let green = (color[1] * light * 255.0).clamp(0.0, 255.0) as u8;
            let blue = (color[2] * light * 255.0).clamp(0.0, 255.0) as u8;

            let min_x = (p0[0].min(p1[0]).min(p2[0]).floor() as i64).max(0);
            let max_x = (p0[0].max(p1[0]).max(p2[0]).ceil() as i64).min(size as i64 - 1);
            let min_y = (p0[1].min(p1[1]).min(p2[1]).floor() as i64).max(0);
            let max_y = (p0[1].max(p1[1]).max(p2[1]).ceil() as i64).min(size as i64 - 1);
            if min_x > max_x || min_y > max_y {
                continue;
            }

            let area = (p1[0] - p0[0]) * (p2[1] - p0[1]) - (p2[0] - p0[0]) * (p1[1] - p0[1]);
            if area == 0.0 {
                continue;
            }
            let inverse_area = 1.0 / area;

            for y in min_y..=max_y {
                let py = y as f64 + 0.5;
                for x in min_x..=max_x {
                    let px = x as f64 + 0.5;
                    let w0 =
                        ((p1[0] - px) * (p2[1] - py) - (p2[0] - px) * (p1[1] - py)) * inverse_area;
                    let w1 =
                        ((p2[0] - px) * (p0[1] - py) - (p0[0] - px) * (p2[1] - py)) * inverse_area;
                    let w2 = 1.0 - w0 - w1;
                    if w0 < 0.0 || w1 < 0.0 || w2 < 0.0 {
                        continue;
                    }
                    let z = w0 * p0[2] + w1 * p1[2] + w2 * p2[2];
                    let index = (y as usize) * (size as usize) + (x as usize);
                    if z > depth[index] {
                        depth[index] = z;
                        let base = index * 3;
                        pixels[base] = red;
                        pixels[base + 1] = green;
                        pixels[base + 2] = blue;
                    }
                }
            }
        }
    }
    pixels
}

fn encode_png(width: u32, height: u32, rgb: &[u8], compression: u32) -> Vec<u8> {
    let mut raw = Vec::with_capacity(((width * 3 + 1) as usize) * (height as usize));
    for y in 0..height {
        raw.push(0);
        let start = (y * width * 3) as usize;
        raw.extend_from_slice(&rgb[start..start + (width * 3) as usize]);
    }

    let mut output = vec![0x89, b'P', b'N', b'G', 0x0D, 0x0A, 0x1A, 0x0A];
    let mut header = Vec::with_capacity(13);
    header.extend_from_slice(&width.to_be_bytes());
    header.extend_from_slice(&height.to_be_bytes());
    header.extend_from_slice(&[8, 2, 0, 0, 0]);
    write_chunk(&mut output, b"IHDR", &header);
    write_chunk(&mut output, b"IDAT", &zlib_compress(&raw, compression));
    write_chunk(&mut output, b"IEND", &[]);
    output
}

fn write_chunk(output: &mut Vec<u8>, kind: &[u8; 4], data: &[u8]) {
    output.extend_from_slice(&(data.len() as u32).to_be_bytes());
    output.extend_from_slice(kind);
    output.extend_from_slice(data);
    let mut crc_input = Vec::with_capacity(4 + data.len());
    crc_input.extend_from_slice(kind);
    crc_input.extend_from_slice(data);
    output.extend_from_slice(&crc32(&crc_input).to_be_bytes());
}

/// Wraps `data` in a zlib stream (DEFLATE + Adler-32) at the given level (`0..=9`).
fn zlib_compress(data: &[u8], level: u32) -> Vec<u8> {
    let mut encoder = ZlibEncoder::new(Vec::new(), Compression::new(level));
    encoder
        .write_all(data)
        .expect("writing to an in-memory buffer cannot fail");
    encoder
        .finish()
        .expect("flushing to an in-memory buffer cannot fail")
}

fn crc32(data: &[u8]) -> u32 {
    let mut crc: u32 = 0xFFFF_FFFF;
    for &byte in data {
        crc ^= byte as u32;
        for _ in 0..8 {
            let mask = (crc & 1).wrapping_neg();
            crc = (crc >> 1) ^ (0xEDB8_8320 & mask);
        }
    }
    !crc
}
