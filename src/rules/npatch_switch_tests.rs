//! The NPatch switch counts only keys read by NPatch-specific readers.

use crate::rules::ares_switch;
use crate::rules::ini_parser::{IniFile, IniSection};
use crate::rules::npatch_switch;
use crate::rules::ruleset::RuleSet;

fn decides(ini: &IniFile, read: impl FnOnce(&IniSection)) -> bool {
    let (_, reads) = ares_switch::record(|| read(ini.section("General").unwrap()));
    npatch_switch::NPatchInputs::default().decide(&reads)
}

#[test]
fn only_an_npatch_reader_turns_the_switch_on() {
    let ini = IniFile::from_str("[General]\nVehicleParachute=VEHCHUTE\n");
    assert!(decides(&ini, |section| {
        npatch_switch::npatch_read(|| section.read_name("VehicleParachute", 2048));
    }));
    assert!(!decides(&ini, |section| {
        section.read_name("VehicleParachute", 2048);
    }));
    assert!(!decides(&ini, |section| {
        let _ares = ares_switch::ares_reads();
        section.read_name("VehicleParachute", 2048);
    }));
    assert!(!decides(&ini, |section| {
        let _phobos = ares_switch::phobos_reads();
        section.read_name("VehicleParachute", 2048);
    }));
}

#[test]
fn pass_keys_are_retained_in_processed_npatch_inputs() {
    let ini = IniFile::from_str("[TANK]\nAIBuildLimit=2\n");
    let (_, reads) = ares_switch::record(|| {
        npatch_switch::npatch_read(|| {
            ini.section("TANK")
                .unwrap()
                .read_int("AIBuildLimit", i32::MAX);
        });
    });
    assert!(npatch_switch::NPatchInputs::from_passes(&reads).decide(&Default::default()));
}

#[test]
fn vanilla_data_keeps_npatch_off_and_an_extension_key_turns_it_on() {
    assert!(!RuleSet::from_ini(&IniFile::from_str("")).unwrap().npatch());
    let mod_rules =
        IniFile::from_str("[VehicleTypes]\n0=TANK\n[TANK]\nStrength=100\nAIBuildLimit=2\n");
    assert!(RuleSet::from_ini(&mod_rules).unwrap().npatch());
}
