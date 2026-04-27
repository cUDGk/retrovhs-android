use jni::JNIEnv;
use jni::objects::{JByteArray, JClass};
use jni::sys::{jfloat, jint};
use ntsc_rs::settings::standard::NtscEffect;
use ntsc_rs::yiq_fielding::Rgbx;

/// Apply ntsc-rs (default settings) to an RGBA byte array in-place.
/// At intensity < 1.0 the result is alpha-blended with the original input.
///
/// JNI signature: (II[BIF)V
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_cudgk_retrovhs_rust_NtscRs_processRgba<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    width: jint,
    height: jint,
    rgba: JByteArray<'local>,
    frame_num: jint,
    intensity: jfloat,
) {
    let w = width as usize;
    let h = height as usize;
    let len = w * h * 4;
    let frame = frame_num.max(0) as usize;
    let strength = intensity.clamp(0.0, 1.0);

    if strength <= 0.0 {
        return;
    }

    // Copy in (Java byte = i8; reinterpret as u8 for ntsc-rs)
    let mut buf: Vec<u8> = vec![0; len];
    {
        let dst = unsafe { std::slice::from_raw_parts_mut(buf.as_mut_ptr() as *mut i8, len) };
        if env.get_byte_array_region(&rgba, 0, dst).is_err() {
            return;
        }
    }

    // Keep a copy for blending if intensity < 1.
    let original = if strength < 1.0 { Some(buf.clone()) } else { None };

    let effect = NtscEffect::default();
    effect.apply_effect_to_buffer::<Rgbx, u8>(
        (w, h),
        &mut buf,
        frame,
        [1.0, 1.0],
    );

    if let Some(orig) = original {
        let s = strength;
        let inv = 1.0 - s;
        for i in 0..len {
            buf[i] = (orig[i] as f32 * inv + buf[i] as f32 * s).round().clamp(0.0, 255.0) as u8;
        }
    }

    let src = unsafe { std::slice::from_raw_parts(buf.as_ptr() as *const i8, len) };
    let _ = env.set_byte_array_region(&rgba, 0, src);
}
