//! Visible terrain sprite-instance construction for the base terrain pass.
//!
//! Render-owned (F05): culling, UV lookup, lighting, fog, and bridge-aware
//! instance emission over the immutable `map::terrain` grid. Map keeps
//! parsing and static projection; the app render pass is the only caller.

use crate::map::lighting::CellLightGrid;
use crate::map::terrain::{
    HEIGHT_STEP, TILE_HEIGHT, TILE_WIDTH, TerrainCell, TerrainGrid, TilePlacement, UvLookupFn,
};
use crate::render::batch::SpriteInstance;
use crate::render::draw_state::DrawState;
use crate::render::native_z;

/// Cull margin in screen pixels beyond the viewport on every side.
const CULL_MARGIN: f32 = 120.0;

/// Visible ordinary terrain instances drawn in the base terrain pass.
pub struct TerrainInstances {
    /// Normal terrain — drawn in the base terrain pass.
    pub normal: Vec<SpriteInstance>,
}

fn visible_cell_slice(grid: &TerrainGrid, view_top: f32, view_bottom: f32) -> &[TerrainCell] {
    let start = grid
        .cells
        .partition_point(|cell| cell.screen_y + TILE_HEIGHT < view_top);
    let end = grid
        .cells
        .partition_point(|cell| cell.screen_y <= view_bottom);
    &grid.cells[start..end]
}

/// Generate SpriteInstance data for all tiles visible in the current viewport.
///
/// Single-layer rendering: each cell draws exactly one tile. LAT transition
/// tiles are fully opaque inside the diamond shape (confirmed via diagnostics),
/// so no base clear-ground layer is needed. Missing tiles are skipped —
/// the caller's UV lookup should provide fallbacks if desired.
///
/// The instances go into `buffer`, emptied first: a draw hands back the list
/// of the draw before, so its allocation is reused.
pub fn build_visible_instances(
    grid: &TerrainGrid,
    lighting_grid: Option<&CellLightGrid>,
    camera_x: f32,
    camera_y: f32,
    screen_width: f32,
    screen_height: f32,
    uv_fn: UvLookupFn<'_>,
    live_terrain: Option<&crate::map::resolved_terrain::ResolvedTerrainGrid>,
    mut buffer: Vec<SpriteInstance>,
) -> TerrainInstances {
    let view_left = camera_x - CULL_MARGIN;
    let view_right = camera_x + screen_width + CULL_MARGIN;
    let view_top = camera_y - CULL_MARGIN;
    let view_bottom = camera_y + screen_height + CULL_MARGIN;
    buffer.clear();
    for cell in visible_cell_slice(grid, view_top, view_bottom) {
        if let Some(instance) = build_instance(cell, grid, lighting_grid, live_terrain, uv_fn,
            view_left, view_right, view_top, view_bottom) {
            buffer.push(instance);
        }
    }
    TerrainInstances { normal: buffer }
}

/// Android's CPU-side terrain preparation, preserving the source cell order.
#[cfg(not(all(feature = "wasm", target_arch = "wasm32", target_os = "unknown")))]
pub fn build_visible_instances_parallel(
    grid: &TerrainGrid,
    lighting_grid: Option<&CellLightGrid>,
    camera_x: f32,
    camera_y: f32,
    screen_width: f32,
    screen_height: f32,
    uv_fn: Option<&(dyn Fn(u16, u8, u8) -> Option<TilePlacement> + Sync)>,
    live_terrain: Option<&crate::map::resolved_terrain::ResolvedTerrainGrid>,
    mut buffer: Vec<SpriteInstance>,
) -> TerrainInstances {
    use rayon::prelude::*;
    let view_left = camera_x - CULL_MARGIN;
    let view_right = camera_x + screen_width + CULL_MARGIN;
    let view_top = camera_y - CULL_MARGIN;
    let view_bottom = camera_y + screen_height + CULL_MARGIN;
    let visible = visible_cell_slice(grid, view_top, view_bottom);
    if visible.len() < 128 {
        buffer.clear();
        for cell in visible {
            if let Some(instance) = build_instance(cell, grid, lighting_grid, live_terrain, uv_fn,
                view_left, view_right, view_top, view_bottom) {
                buffer.push(instance);
            }
        }
        return TerrainInstances { normal: buffer };
    }
    let prepared: Vec<Option<SpriteInstance>> = visible.par_iter().map(|cell| {
        build_instance(cell, grid, lighting_grid, live_terrain, uv_fn,
            view_left, view_right, view_top, view_bottom)
    }).collect();
    buffer.clear();
    buffer.extend(prepared.into_iter().flatten());
    TerrainInstances { normal: buffer }
}

fn build_instance<F>(
    cell: &TerrainCell,
    grid: &TerrainGrid,
    lighting_grid: Option<&CellLightGrid>,
    live_terrain: Option<&crate::map::resolved_terrain::ResolvedTerrainGrid>,
    uv_fn: Option<&F>,
    view_left: f32,
    view_right: f32,
    view_top: f32,
    view_bottom: f32,
) -> Option<SpriteInstance>
where
    F: Fn(u16, u8, u8) -> Option<TilePlacement> + ?Sized,
{
    // Native `CellOverlay_TileDraw @ 0x00480350` has no shroud gate; the
    // curtain pass hides unexplored ground after terrain instances are drawn.
    let right = cell.screen_x + TILE_WIDTH;
    let bottom = cell.screen_y + TILE_HEIGHT;
    if right < view_left || cell.screen_x > view_right || bottom < view_top || cell.screen_y > view_bottom {
        return None;
    }
    let place = |tile_id: u16, sub_tile: u8, variant: u8| match uv_fn {
        Some(f) => f(tile_id, sub_tile, variant),
        None => Some(TilePlacement {
            uv_origin: [0.0, 0.0], uv_size: [1.0, 1.0],
            pixel_size: [TILE_WIDTH, TILE_HEIGHT], draw_offset: [0.0, 0.0],
        }),
    };
    // Bridge publication and collapse can change the live cell after loading,
    // so its resolved tile and level take precedence over the static grid.
    let live = live_terrain.and_then(|terrain| {
        let live = terrain.cell(cell.rx, cell.ry)?;
        let (tile_id, sub_tile) = terrain.presentation_tile(live);
        let variant = terrain.tactical_draw_variant(cell.rx, cell.ry).unwrap_or(live.variant);
        Some((place(tile_id, sub_tile, variant)?, live.level))
    });
    let (p, level) = live.or_else(|| Some((place(cell.tile_id, cell.sub_tile, cell.variant)?, cell.z)))?;
    let level = i32::from(level as i8);
    // Match native `TMP_TileBlitter @ 0x00547CF0` row/depth placement.
    let iso_row = cell.screen_y + f32::from(cell.z as i8) * HEIGHT_STEP;
    let screen_y = iso_row - level as f32 * HEIGHT_STEP;
    let depth = native_z::depth_for_row(iso_row, grid.origin_y, grid.world_height);
    let z_adjust = p.draw_offset[1] - native_z::TILE_HEIGHT_ROWS as f32
        - ((native_z::TILE_HEIGHT_ROWS * level) / 2) as f32;
    let tint = lighting_grid.map(|lights| lights.terrain_tile_tint_at((cell.rx, cell.ry))).unwrap_or(cell.tint);
    Some(SpriteInstance {
        position: [cell.screen_x + p.draw_offset[0], screen_y + p.draw_offset[1]],
        size: p.pixel_size, uv_origin: p.uv_origin, uv_size: p.uv_size,
        depth, tint,
        palette_light: lighting_grid.map(|lights| {
            crate::render::palette_light::PaletteLight::cell(lights, (cell.rx, cell.ry), false)
        }).unwrap_or_default(),
        alpha: 1.0, draw_state: DrawState::default(), z_adjust,
        ..Default::default()
    })
}
#[cfg(all(test, tests_render_s_z))]
#[path = "bridge_pavement_caller_tests.rs"]
mod pavement_caller_tests;

#[cfg(all(test, tests_render_s_z))]
mod tests {
    use super::*;

    /// A draw hands back the list of the draw before; none of it may reach
    /// this draw's tiles.
    #[test]
    fn a_handed_back_buffer_holds_only_this_draws_tiles() {
        let grid = TerrainGrid {
            cells: vec![TerrainCell {
                screen_x: 0.0,
                screen_y: 0.0,
                tile_id: 0,
                sub_tile: 0,
                z: 0,
                rx: 1,
                ry: 1,
                variant: 0,
                tint: [1.0; 3],
                radar_left: [0; 3],
                radar_right: [0; 3],
            }],
            world_width: TILE_WIDTH,
            world_height: TILE_HEIGHT,
            origin_x: 0.0,
            origin_y: 0.0,
            bridge_middle_tiles: None,
            deformation_tiles: Vec::new(),
        };
        let draw = |buffer| {
            build_visible_instances(&grid, None, 0.0, 0.0, 800.0, 600.0, None, None, buffer).normal
        };
        let fresh = draw(Vec::new());
        assert_eq!(fresh.len(), 1);
        let stale = vec![SpriteInstance::default(); 3];
        assert_eq!(
            bytemuck::cast_slice::<_, u8>(&draw(stale)),
            bytemuck::cast_slice::<_, u8>(&fresh)
        );
    }

    #[test]
    fn parallel_terrain_preparation_preserves_serial_cell_order_and_bytes() {
        let cells: Vec<_> = (0..160)
            .map(|index| TerrainCell {
                screen_x: index as f32 * TILE_WIDTH,
                screen_y: 0.0,
                tile_id: index,
                sub_tile: 0,
                z: 0,
                rx: index,
                ry: 0,
                variant: 0,
                tint: [1.0; 3],
                radar_left: [0; 3],
                radar_right: [0; 3],
            })
            .collect();
        let grid = TerrainGrid {
            cells,
            world_width: 160.0 * TILE_WIDTH,
            world_height: TILE_HEIGHT,
            origin_x: 0.0,
            origin_y: 0.0,
            bridge_middle_tiles: None,
            deformation_tiles: Vec::new(),
        };
        let no_uv: UvLookupFn<'_> = None;
        let sync_no_uv: Option<&(dyn Fn(u16, u8, u8) -> Option<TilePlacement> + Sync)> = None;
        let parallel = build_visible_instances_parallel(
            &grid, None, 0.0, 0.0, grid.world_width, 600.0, sync_no_uv, None, Vec::new(),
        ).normal;
        let serial = build_visible_instances(
            &grid, None, 0.0, 0.0, grid.world_width, 600.0, no_uv, None, Vec::new(),
        ).normal;
        assert_eq!(parallel.len(), 160);
        assert_eq!(
            bytemuck::cast_slice::<_, u8>(&parallel),
            bytemuck::cast_slice::<_, u8>(&serial),
        );
    }
}
