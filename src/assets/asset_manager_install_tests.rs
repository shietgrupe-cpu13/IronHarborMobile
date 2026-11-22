use super::test_support::{TestDirectory, audio_test_install, make_new_format_mix_bytes};
use super::*;

#[test]
fn inspection_lists_required_failures_and_keeps_later_readable_archives() {
    let directory = audio_test_install("inspect-required");
    for name in ["cachemd.mix", "mapsmd03.mix", "movmd03.mix"] {
        std::fs::remove_file(directory.path().join(name)).unwrap();
    }
    std::fs::write(directory.path().join("cameo.mix"), b"bad").unwrap();

    let (assets, issues) =
        AssetManager::inspect_install(directory.path(), MediaArchiveMode::STOCK_DIGITAL).unwrap();
    // Missing MOVMD03 falls through to MOVIES03 in mount_startup_movie_media;
    // the optional issue names that final attempted fallback archive.
    assert_eq!(
        issues
            .iter()
            .map(|issue| (issue.name.as_str(), issue.required))
            .collect::<Vec<_>>(),
        [
            ("cachemd.mix", true),
            ("cameo.mix", true),
            ("mapsmd03.mix", false),
            ("movies03.mix", false)
        ]
    );
    assert!(issues[1].detail.contains("Invalid MIX archive"));
    assert!(assets.archive("multimd.mix").is_some());
    assert!(matches!(
        AssetManager::new(directory.path(), MediaArchiveMode::STOCK_DIGITAL),
        Err(AssetError::AssetNotFound { name }) if name == "cachemd.mix"
    ));

    // Only the test-only FailFast policy still refuses missing media.
    let mut manager = AssetManager::from_ini_probe_root(directory.path()).unwrap();
    assert!(
        manager
            .mount_native_map_media(MediaArchiveMode::STOCK_DIGITAL)
            .is_err()
    );
    assert!(
        manager
            .mount_native_movie_media(MediaArchiveMode::STOCK_DIGITAL)
            .is_err()
    );
}

#[test]
fn startup_continues_without_map_and_movie_media() {
    let directory = audio_test_install("no-media");
    for name in ["mapsmd03.mix", "multimd.mix", "movmd03.mix"] {
        std::fs::remove_file(directory.path().join(name)).unwrap();
    }
    assert!(AssetManager::new(directory.path(), MediaArchiveMode::STOCK_DIGITAL).is_ok());
    assert!(AssetManager::new(directory.path(), MediaArchiveMode::CdWildcard).is_ok());

    let (_, issues) =
        AssetManager::inspect_install(directory.path(), MediaArchiveMode::STOCK_DIGITAL).unwrap();
    assert_eq!(
        issues
            .iter()
            .map(|issue| (issue.name.as_str(), issue.required))
            .collect::<Vec<_>>(),
        [
            ("mapsmd03.mix", false),
            ("multimd.mix", false),
            ("movies03.mix", false)
        ]
    );
}

#[test]
fn ra2_profile_mounts_base_game_archives_without_yr_master_archives() {
    let directory = TestDirectory::new("ra2-profile-archive-family");
    for name in [
        "ra2.mix",
        "cache.mix",
        "local.mix",
        "conquer.mix",
        "cameo.mix",
    ] {
        directory.write_mix(name, "fixture.bin", b"base RA2");
    }
    for name in ["rules.ini", "art.ini", "ai.ini"] {
        std::fs::write(directory.path().join(name), b"[LauncherProbe]\nValue=RA2\n").unwrap();
    }

    let roots = AssetManager::inspect_launch_ini_roots(
        directory.path(),
        None,
        MediaArchiveMode::STOCK_DIGITAL,
        crate::util::ares_launch::AresSetting::Off,
        &crate::util::ini_roots::IniRootConfig {
            profile: crate::util::ini_roots::IniProfile::Ra2,
            ..Default::default()
        },
    )
    .expect("base RA2 archive stack and INI roots validate");

    assert_eq!(roots.profile(), crate::util::ini_roots::IniProfile::Ra2);
    assert_eq!(roots.battle(), "BATTLE.INI");
    assert_eq!(
        crate::assets::asset_manager::ArchiveFamily::for_ini_profile(roots.profile()),
        crate::assets::asset_manager::ArchiveFamily::Ra2
    );

    let manager = AssetManager::with_mod_and_ares_support(
        directory.path(),
        None,
        None,
        MediaArchiveMode::STOCK_DIGITAL,
        crate::util::ares_launch::AresSetting::Off,
        crate::assets::asset_manager::ArchiveFamily::Ra2,
    )
    .expect("base RA2 archives mount");
    assert!(manager.archive("ra2.mix").is_some());
    assert!(manager.archive("ra2md.mix").is_none());
    assert!(manager.archive("cache.mix").is_some());
    assert!(manager.archive("local.mix").is_some());
    assert!(manager.archive("language.mix").is_none());
}

#[test]
fn launcher_ini_preflight_uses_runtime_mod_overlay_and_rejects_incomplete_mo() {
    let install = audio_test_install("launcher-ini-preflight");
    let mod_dir = install.path().join("Mods/mentalomega330mod");
    std::fs::create_dir_all(&mod_dir).unwrap();
    for name in ["rulesmo.ini", "artmo.ini", "aimo.ini"] {
        std::fs::write(mod_dir.join(name), b"[LauncherProbe]\nValue=MO\n").unwrap();
    }
    let config = crate::util::ini_roots::IniRootConfig {
        profile: crate::util::ini_roots::IniProfile::Mo,
        ..Default::default()
    };

    let selected = AssetManager::inspect_launch_ini_roots(
        install.path(),
        Some(&mod_dir),
        MediaArchiveMode::STOCK_DIGITAL,
        crate::util::ares_launch::AresSetting::Auto,
        &config,
    )
    .unwrap();
    assert_eq!(selected.profile(), crate::util::ini_roots::IniProfile::Mo);
    assert_eq!(selected.names(), ["rulesmo.ini", "artmo.ini", "aimo.ini"]);

    std::fs::remove_file(mod_dir.join("aimo.ini")).unwrap();
    let error = AssetManager::inspect_launch_ini_roots(
        install.path(),
        Some(&mod_dir),
        MediaArchiveMode::STOCK_DIGITAL,
        crate::util::ares_launch::AresSetting::Auto,
        &config,
    )
    .unwrap_err();
    assert!(error.to_string().contains("aimo.ini"));
}

#[test]
fn inspection_accepts_nested_required_archives_without_optional_music() {
    let directory = audio_test_install("inspect-nested");
    std::fs::remove_file(directory.path().join("cachemd.mix")).unwrap();
    let nested = make_new_format_mix_bytes("inside.bin", b"nested data");
    directory.write_mix("ra2md.mix", "cachemd.mix", &nested);

    let (assets, issues) =
        AssetManager::inspect_install(directory.path(), MediaArchiveMode::STOCK_DIGITAL).unwrap();
    assert!(issues.is_empty(), "{issues:?}");
    let source = assets.resolve_ref("inside.bin").unwrap();
    assert_eq!(source.source_archive, "ra2md.mix -> cachemd.mix");
    assert_eq!(source.bytes, b"nested data");
    assert!(assets.archive("thememd.mix").is_none());
    assert!(assets.archive("theme.mix").is_none());
    let ordinary = AssetManager::new(directory.path(), MediaArchiveMode::STOCK_DIGITAL).unwrap();
    assert_eq!(
        assets.loaded_archive_names(),
        ordinary.loaded_archive_names()
    );
}

#[test]
fn inspection_reports_optional_corruption_and_both_cd_media_boundaries() {
    let directory = audio_test_install("inspect-optional");
    std::fs::write(directory.path().join("thememd.mix"), b"bad").unwrap();
    let (_, issues) =
        AssetManager::inspect_install(directory.path(), MediaArchiveMode::STOCK_DIGITAL).unwrap();
    assert_eq!(issues.len(), 1);
    assert_eq!(issues[0].name, "thememd.mix");
    assert!(!issues[0].required);
    let ordinary = AssetManager::new(directory.path(), MediaArchiveMode::STOCK_DIGITAL).unwrap();
    assert!(ordinary.archive("thememd.mix").is_none());

    // Standalone media admission retains its strict error contract even when
    // ordinary startup treats that same optional named archive as a warning.
    std::fs::write(directory.path().join("maps03.mix"), b"bad").unwrap();
    let mut media = AssetManager::from_ini_probe_root(directory.path()).unwrap();
    assert!(
        media
            .mount_native_map_media(MediaArchiveMode::STOCK_DIGITAL)
            .is_err()
    );
    assert!(AssetManager::new(directory.path(), MediaArchiveMode::STOCK_DIGITAL).is_ok());
    std::fs::remove_file(directory.path().join("maps03.mix")).unwrap();

    for name in ["mapsmd03.mix", "movmd03.mix"] {
        std::fs::remove_file(directory.path().join(name)).unwrap();
    }
    let (_, issues) =
        AssetManager::inspect_install(directory.path(), MediaArchiveMode::CdWildcard).unwrap();
    assert_eq!(
        issues
            .iter()
            .map(|issue| (issue.name.as_str(), issue.required))
            .collect::<Vec<_>>(),
        [
            ("MAPSMD*.MIX", false),
            ("thememd.mix", false),
            ("MOVMD*.MIX", false)
        ]
    );
}

/// With no mod selected, startup ignores an installation's Ares archive.
/// A selected mod keeps Load_Bootstrap_AresMIX (0x0053029E), its own copy first.
#[test]
fn only_a_selected_mod_mounts_ares_mix() {
    let directory = audio_test_install("ships-ares");
    let mode = MediaArchiveMode::STOCK_DIGITAL;
    let assets = AssetManager::new(directory.path(), mode).unwrap();
    assert!(!assets.ships_ares());
    directory.write_mix("ares.mix", "probe.csf", b"ares");
    let assets = AssetManager::new(directory.path(), mode).unwrap();
    assert!(!assets.ships_ares());
    assert!(assets.get_ref("probe.csf").is_none());
    let (inspected, _) = AssetManager::inspect_install(directory.path(), mode).unwrap();
    assert!(!inspected.ships_ares());

    let mod_dir = super::test_support::TestDirectory::new("ares-mod");
    let assets = AssetManager::with_mod(directory.path(), Some(mod_dir.path()), mode).unwrap();
    assert!(assets.ships_ares());
    assert_eq!(assets.get_ref("probe.csf"), Some(&b"ares"[..]));
    mod_dir.write_mix("ARES.MIX", "probe.csf", b"mod ares");
    let assets = AssetManager::with_mod(directory.path(), Some(mod_dir.path()), mode).unwrap();
    assert_eq!(assets.get_ref("probe.csf"), Some(&b"mod ares"[..]));
}

/// Ares Expand_MIX_Reorg (0x006BD7E3) runs Init_Base_Mix_Files ahead of
/// LANGMD.MIX, and Load_Bootstrap_AresMIX (0x0053029E) registers ares.mix
/// after the expansions and before RA2MD.MIX. With no mod selected, startup
/// keeps gamemd's order, LANGMD.MIX first, even with an installed `ares.mix`,
/// unless the player's Ares setting is On.
#[test]
fn a_game_with_ares_mix_resolves_base_mixes_and_ares_mix_ahead_of_langmd() {
    for ares_way in [false, true] {
        let _ares = crate::rules::ares_switch::read_ares_way(ares_way);
        let directory = audio_test_install("ares-mix");
        let mod_dir = super::test_support::TestDirectory::new("ares-order-mod");
        // The Ares way stands for an Ares game: the Ares setting On, which
        // takes Ares' order in every game.
        let setting = if ares_way {
            crate::util::ares_launch::AresSetting::On
        } else {
            crate::util::ares_launch::AresSetting::Auto
        };
        let source = |name: &str, selected_mod: bool| {
            let assets = AssetManager::with_mod_and_ares(
                directory.path(),
                selected_mod.then_some(mod_dir.path()),
                MediaArchiveMode::STOCK_DIGITAL,
                setting,
                ArchiveFamily::Yr,
            )
            .unwrap();
            assets.resolve_ref(name).unwrap().source_archive.to_owned()
        };
        for archive in ["langmd.mix", "ra2md.mix"] {
            directory.write_mix(archive, "probe.csf", archive.as_bytes());
        }
        assert_eq!(
            source("probe.csf", false),
            if ares_way { "ra2md.mix" } else { "langmd.mix" }
        );
        directory.write_mix("ares.mix", "other.csf", b"ares.mix");
        assert_eq!(
            source("probe.csf", false),
            if ares_way { "ra2md.mix" } else { "langmd.mix" }
        );
        assert_eq!(source("probe.csf", true), "ra2md.mix");
        for archive in ["ares.mix", "expandmd00.mix"] {
            directory.write_mix(archive, "probe.csf", archive.as_bytes());
            assert_eq!(source("probe.csf", true), archive);
        }
    }
}

#[test]
fn mo_family_mounts_spawner_renamed_archives_and_their_ini_roots() {
    let directory = audio_test_install("mo-family");
    directory.write_mix("expandmd01.mix", "unrelated.bin", b"fixture");
    for name in ["mapsmo03.mix", "multimo.mix", "movmo03.mix"] {
        directory.write_mix(name, "unrelated.bin", b"fixture");
    }
    std::fs::write(
        directory.path().join("expandmo99.mix"),
        crate::map::source::test_support::make_new_format_mix_bytes(&[
            ("rulesmo.ini", b"[General]"),
            ("artmo.ini", b"[General]"),
            ("aimo.ini", b"[General]"),
        ]),
    )
    .unwrap();

    let config = crate::util::ini_roots::IniRootConfig {
        profile: crate::util::ini_roots::IniProfile::Mo,
        ..Default::default()
    };
    let assets = AssetManager::with_mod_and_ares(
        directory.path(),
        None,
        MediaArchiveMode::STOCK_DIGITAL,
        crate::util::ares_launch::AresSetting::Auto,
        ArchiveFamily::for_ini_profile(config.profile),
    )
    .unwrap()
    .with_ini_roots(&config)
    .unwrap();
    for name in [
        "expandmo99.mix",
        "mapsmo03.mix",
        "multimo.mix",
        "movmo03.mix",
    ] {
        assert!(assets.archive(name).is_some(), "{name}");
    }
    for name in ["expandmd01.mix", "mapsmd03.mix", "multimd.mix"] {
        assert!(assets.archive(name).is_none(), "{name}");
    }
    assert_eq!(assets.ini_roots().rules(), "rulesmo.ini");
}

#[test]
fn mo_profile_auto_uses_ares_semantics_without_requiring_ares_mix() {
    let directory = audio_test_install("mo-ares-auto");
    let family = ArchiveFamily::Mo;
    let automatic = AssetManager::with_mod_and_ares(
        directory.path(),
        None,
        MediaArchiveMode::STOCK_DIGITAL,
        crate::util::ares_launch::AresSetting::Auto,
        family,
    )
    .unwrap();
    assert!(!automatic.ares_mix_mounted());
    assert!(automatic.ships_ares());

    let disabled = AssetManager::with_mod_and_ares(
        directory.path(),
        None,
        MediaArchiveMode::STOCK_DIGITAL,
        crate::util::ares_launch::AresSetting::Off,
        family,
    )
    .unwrap();
    assert!(!disabled.ares_mix_mounted());
    assert!(!disabled.ships_ares());
}
