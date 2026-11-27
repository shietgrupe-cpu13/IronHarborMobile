use crate::rules::ini_parser::{IniFile, IniSection};
use crate::rules::object_image::{ImageTheater, ObjectImageKind, ObjectImageState};

#[test]
fn retail_snow_seal_uses_the_retained_arctic_image() {
    let Some(snow) = crate::rules::retail_ini_fixture::retail_battle_rules_with_map_patch(
        &IniFile::from_str("[Map]\nTheater=SNOW\n"),
    ) else {
        return;
    };
    // ObjectTypeClass::LoadSHP5F9070..5F9119, reached by the stock GHOST
    // Rules reader after Full_Init687649 publishes the incoming theater.
    assert_eq!(snow.rules.object("GHOST").unwrap().image, "SEALA");
    assert_eq!(
        snow.rules
            .object("GHOST")
            .unwrap()
            .shp_image_loads()
            .first()
            .unwrap()
            .image,
        "SEALA"
    );
    let (_, assets) = crate::rules::retail_ini_fixture::retail_assets().unwrap();
    let arctic = assets.get_ref("SEALA.SHP").unwrap();
    let ordinary = assets.get_ref("SEAL.SHP").unwrap();
    assert_ne!(
        arctic, ordinary,
        "the selected stock art is a different physical image"
    );
    let temperate = crate::rules::retail_ini_fixture::retail_battle_rules().unwrap();
    assert_eq!(temperate.rules.object("GHOST").unwrap().image, "SEAL");
}

/// Ares Load2DArt takes ImageFile plus the selected theater letter only when
/// the corresponding art section exists; stock loading ignores this key.
#[test]
fn ares_alternate_theater_art_is_conditional_and_keeps_stock_behavior() {
    let raw = {
        let mut section = IniSection::new("VIRUS".to_owned());
        section.set("AlternateTheaterArt", "yes");
        section
    };
    let art = IniFile::from_str("[VIRUS]\nVoxel=no\n[VIRUSD]\nVoxel=no\n");

    let mut stock = ObjectImageState::new("VIRUS");
    stock.read_ini(&raw, &art, ObjectImageKind::Ordinary, ImageTheater::Desert);
    assert_eq!(stock.image(), "VIRUS");

    let _ares = crate::rules::ares_switch::read_ares_way(true);
    let mut enabled = ObjectImageState::new("VIRUS");
    enabled.read_ares_alternate_theater_art(&raw);
    enabled.read_ini(&raw, &art, ObjectImageKind::Ordinary, ImageTheater::Desert);
    assert_eq!(enabled.image(), "VIRUSD");
    assert_eq!(enabled.shp_loads()[0].image, "VIRUSD");

    let missing_theater_art = IniFile::from_str("[VIRUS]\nVoxel=no\n");
    let mut missing = ObjectImageState::new("VIRUS");
    missing.read_ares_alternate_theater_art(&raw);
    missing.read_ini(
        &raw,
        &missing_theater_art,
        ObjectImageKind::Ordinary,
        ImageTheater::Desert,
    );
    assert_eq!(missing.image(), "VIRUS");
}
