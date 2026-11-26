//! The per-game NPatch switch: a key only an NPatch reader finds turns the
//! extension on. Retail rules hold none of these keys.

use std::collections::BTreeSet;

use crate::rules::ares_switch::KeyReads;

/// Mark one inline NPatch key read for the per-game switch.
pub(crate) fn npatch_read<T>(read: impl FnOnce() -> T) -> T {
    let _npatch = crate::rules::ares_switch::npatch_reads();
    read()
}

/// NPatch-only keys found while processing the ordered Rules layers. Captured
/// for the live projection; persisted rules rebuild pass-only observations from
/// their already retained INI and native type bodies.
#[derive(Debug, Clone, Default, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
pub(crate) struct NPatchInputs {
    keys: BTreeSet<(String, String)>,
}

impl NPatchInputs {
    pub(crate) fn from_passes(reads: &KeyReads) -> Self {
        Self {
            keys: reads
                .npatch_only()
                .map(|(section, key)| (section.to_owned(), key.to_owned()))
                .collect(),
        }
    }

    /// Whether these Rules passes or the projection contain an NPatch-only
    /// key. The chosen key is logged to explain mod activation.
    pub(crate) fn decide(&self, projection: &KeyReads) -> bool {
        let key = self
            .keys
            .iter()
            .map(|(section, key)| (section.as_str(), key.as_str()))
            .chain(projection.npatch_only())
            .min();
        match key {
            Some((section, key)) => {
                log::info!("NPatch enabled by mod data: [{section}] {key}");
                true
            }
            None => false,
        }
    }

    /// Rebuild the pass-only observations when loading a save written before
    /// this transient field existed. The INI and retained native type bodies
    /// are already part of the save, so this preserves the old wire layout.
    pub(crate) fn from_saved_rules(
        ini: &crate::rules::ini_parser::IniFile,
        trace: &crate::rules::native_processing::NativeTypeConstructionTrace,
    ) -> Self {
        let mut keys = BTreeSet::new();
        if let Some(general) = ini.section("General") {
            for key in [
                "VehicleParachute",
                "EnableAIBuildLimitation",
                "DisableParallelAIQueues",
                "DisableAIParallelProduction",
            ] {
                if general.is_present(key) {
                    keys.insert(("General".to_owned(), key.to_owned()));
                }
            }
        }
        for (section, is_building) in trace.npatch_type_sections() {
            if section.is_present("AIBuildLimit") {
                keys.insert((section.name.clone(), "AIBuildLimit".to_owned()));
            }
            if is_building && section.is_present("AIPlanningCount") {
                keys.insert((section.name.clone(), "AIPlanningCount".to_owned()));
            }
        }
        Self { keys }
    }

    pub(crate) fn is_empty(&self) -> bool {
        self.keys.is_empty()
    }
}

#[cfg(all(test, tests_rules_a_n))]
#[path = "npatch_switch_tests.rs"]
mod tests;
