//! Shared signed-coordinate interpolation used by native gameplay consumers.

use crate::util::native_x87::{X87Chop53 as Fpu, X87Value};

/// `CoordStruct::Lerp75F540` and the matching inline Wave762AF0/7610F0
/// sequence: `ftol((1-t)*first + t*second)` for each coordinate.
/// The caller preserves its native argument store:75F540 receives binary32,
/// while the inline Wave paths retain a double. Intermediates use the shared
/// deterministic53-bit/chop FPU owner and conversion keeps the low dword.
/// Consumed native controls: procedural_drawing_oracle/magnetic_wave.json and
/// spatial_oracle/locomotor_track_residual.json.
pub(crate) fn interpolate<const N: usize>(
    first: [i32; N],
    second: [i32; N],
    t: X87Value,
) -> [i32; N] {
    let other = Fpu::sub(Fpu::load_i32(1), t);
    std::array::from_fn(|axis| {
        Fpu::ftol_i64(Fpu::add(
            Fpu::mul(Fpu::load_i32(first[axis]), other),
            Fpu::mul(Fpu::load_i32(second[axis]), t),
        ))
        .expect("finite signed-coordinate interpolation fits i64") as i32
    })
}

/// `ObjectClass::DistanceSquaredXY_To_Object @ 0x005F6500` and its
/// coordinate twin `0x005F6560`: `dx² + dy²` of two XY lepton points, Z
/// ignored, each difference a 32-bit SUB and the squares and sum 32-bit
/// IMUL/ADD that wrap. Ares 3.0 AbstractClass_Distance2DSquared_1/2
/// (1005C1A0, hook 0x5F6515; 1005C240, hook 0x5F6560) square the same
/// 32-bit differences in 64 bits and clamp the sum to `i32::MAX`, when Ares
/// plays (`ares`, [`RuleSet::ares`]).
///
/// [`RuleSet::ares`]: crate::rules::ruleset::RuleSet::ares
pub(crate) fn squared_xy_distance(from: [i32; 2], to: [i32; 2], ares: bool) -> i32 {
    let dx = from[0].wrapping_sub(to[0]);
    let dy = from[1].wrapping_sub(to[1]);
    if ares {
        let (dx, dy) = (i64::from(dx), i64::from(dy));
        i32::try_from(dy * dy + dx * dx).unwrap_or(i32::MAX)
    } else {
        dy.wrapping_mul(dy).wrapping_add(dx.wrapping_mul(dx))
    }
}

/// The native non-Ares `dx² + dy²` lepton square, kept as a signed, widened
/// result for consumers that pass it to `Sqrt_Approx`. Original-readable maps
/// preserve the wrapped 32-bit arithmetic. VERA-only maps use the true square
/// so extended coordinates do not change projectile or wave behavior through
/// overflow.
pub(crate) fn squared_xy_leptons_for_map(from: [i32; 2], to: [i32; 2], vera_only_map: bool) -> i64 {
    if !vera_only_map {
        let dx = from[0].wrapping_sub(to[0]);
        let dy = from[1].wrapping_sub(to[1]);
        return i64::from(dy.wrapping_mul(dy).wrapping_add(dx.wrapping_mul(dx)));
    }
    let dx = i64::from(from[0]) - i64::from(to[0]);
    let dy = i64::from(from[1]) - i64::from(to[1]);
    dx * dx + dy * dy
}

/// Three-axis counterpart for kernels such as Sonic-wave wobble, whose native
/// `dz² + dy² + dx²` also wraps on original-readable maps.
pub(crate) fn squared_xyz_leptons_for_map(
    from: [i32; 3],
    to: [i32; 3],
    vera_only_map: bool,
) -> i64 {
    if !vera_only_map {
        let dx = from[0].wrapping_sub(to[0]);
        let dy = from[1].wrapping_sub(to[1]);
        let dz = from[2].wrapping_sub(to[2]);
        return i64::from(
            dz.wrapping_mul(dz)
                .wrapping_add(dy.wrapping_mul(dy))
                .wrapping_add(dx.wrapping_mul(dx)),
        );
    }
    let dx = i64::from(from[0]) - i64::from(to[0]);
    let dy = i64::from(from[1]) - i64::from(to[1]);
    let dz = i64::from(from[2]) - i64::from(to[2]);
    (dz * dz + dy * dy) + dx * dx
}

#[cfg(test)]
mod tests {
    use super::{squared_xy_leptons_for_map, squared_xyz_leptons_for_map};

    #[test]
    fn map_distance_keeps_native_wrap_until_extended_map() {
        assert_eq!(
            squared_xy_leptons_for_map([46_341, 46_341], [0, 0], false),
            9_266
        );
        assert_eq!(
            squared_xy_leptons_for_map([46_341, 46_341], [0, 0], true),
            4_294_976_562
        );
    }

    #[test]
    fn three_axis_distance_keeps_native_wrap_until_extended_map() {
        let native = squared_xyz_leptons_for_map([46_341, 46_341, 46_341], [0; 3], false);
        let extended = squared_xyz_leptons_for_map([46_341, 46_341, 46_341], [0; 3], true);
        assert_eq!(native, -2_147_469_749);
        assert_eq!(extended, 6_442_464_843);
    }
}
