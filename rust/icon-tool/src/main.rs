use std::path::{Path, PathBuf};

use image::{imageops::FilterType, GenericImageView, ImageBuffer, Rgba, RgbaImage};
use ntsc_rs::settings::standard::NtscEffect;
use ntsc_rs::yiq_fielding::Rgbx;

const DENSITIES: &[(&str, u32)] = &[
    ("mdpi", 48),
    ("hdpi", 72),
    ("xhdpi", 96),
    ("xxhdpi", 144),
    ("xxxhdpi", 192),
];

fn main() {
    let args: Vec<String> = std::env::args().collect();
    if args.len() != 3 {
        eprintln!("usage: {} <input.png> <output_dir>", args[0]);
        std::process::exit(2);
    }
    let input = Path::new(&args[1]);
    let out_dir = PathBuf::from(&args[2]);

    let img = image::open(input).expect("failed to open input image");
    let (w, h) = img.dimensions();
    println!("loaded {}x{} from {}", w, h, input.display());

    let side = w.min(h);
    let x = (w - side) / 2;
    let y = (h - side) / 2;
    let cropped = img.crop_imm(x, y, side, side);

    let base: RgbaImage = cropped
        .resize_exact(1024, 1024, FilterType::Lanczos3)
        .to_rgba8();
    println!("resized to 1024x1024");

    let mut buf: Vec<u8> = base.into_raw();
    let effect = NtscEffect::default();
    effect.apply_effect_to_buffer::<Rgbx, u8>((1024, 1024), &mut buf, 0, [1.0, 1.0]);
    println!("applied ntsc-rs effect at 1024x1024");

    let mut processed: RgbaImage =
        ImageBuffer::<Rgba<u8>, _>::from_raw(1024, 1024, buf).expect("buffer size mismatch");
    for px in processed.pixels_mut() {
        px.0[3] = 255;
    }

    let mut rounded = processed.clone();
    apply_rounded_corners(&mut rounded, 0.22);

    for (density, size) in DENSITIES {
        let dir = out_dir.join(format!("mipmap-{}", density));
        std::fs::create_dir_all(&dir).expect("create mipmap dir");
        let out_path = dir.join("ic_launcher.png");
        let resized = image::imageops::resize(&rounded, *size, *size, FilterType::Lanczos3);
        resized.save(&out_path).expect("save mipmap png");
        println!("wrote {}", out_path.display());
    }

    let mut round = processed.clone();
    apply_circular_mask(&mut round);
    for (density, size) in DENSITIES {
        let dir = out_dir.join(format!("mipmap-{}", density));
        let out_path = dir.join("ic_launcher_round.png");
        let resized = image::imageops::resize(&round, *size, *size, FilterType::Lanczos3);
        resized.save(&out_path).expect("save round mipmap png");
        println!("wrote {}", out_path.display());
    }
}

fn apply_rounded_corners(img: &mut RgbaImage, radius_ratio: f32) {
    let (w, h) = img.dimensions();
    let r = (w.min(h) as f32 * radius_ratio) as u32;
    if r == 0 {
        return;
    }
    let r_f = r as f32;
    for y in 0..h {
        for x in 0..w {
            let center = corner_center(x, y, w, h, r);
            if let Some((cx, cy)) = center {
                let dx = x as f32 + 0.5 - cx;
                let dy = y as f32 + 0.5 - cy;
                let d = (dx * dx + dy * dy).sqrt();
                if d > r_f {
                    img.get_pixel_mut(x, y).0[3] = 0;
                } else if d > r_f - 1.0 {
                    let edge = ((r_f - d).clamp(0.0, 1.0) * 255.0) as u16;
                    let p = img.get_pixel_mut(x, y);
                    p.0[3] = (p.0[3] as u16 * edge / 255) as u8;
                }
            }
        }
    }
}

fn corner_center(x: u32, y: u32, w: u32, h: u32, r: u32) -> Option<(f32, f32)> {
    let in_top = y < r;
    let in_bottom = y >= h - r;
    let in_left = x < r;
    let in_right = x >= w - r;
    match (in_left, in_right, in_top, in_bottom) {
        (true, _, true, _) => Some((r as f32, r as f32)),
        (_, true, true, _) => Some(((w - r) as f32, r as f32)),
        (true, _, _, true) => Some((r as f32, (h - r) as f32)),
        (_, true, _, true) => Some(((w - r) as f32, (h - r) as f32)),
        _ => None,
    }
}

fn apply_circular_mask(img: &mut RgbaImage) {
    let (w, h) = img.dimensions();
    let cx = w as f32 / 2.0;
    let cy = h as f32 / 2.0;
    let r = (w.min(h) as f32 / 2.0) - 1.0;
    for y in 0..h {
        for x in 0..w {
            let dx = x as f32 + 0.5 - cx;
            let dy = y as f32 + 0.5 - cy;
            let d = (dx * dx + dy * dy).sqrt();
            if d > r {
                img.get_pixel_mut(x, y).0[3] = 0;
            } else if d > r - 1.0 {
                let edge = ((r - d).clamp(0.0, 1.0) * 255.0) as u16;
                let p = img.get_pixel_mut(x, y);
                p.0[3] = (p.0[3] as u16 * edge / 255) as u8;
            }
        }
    }
}
