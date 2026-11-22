//! VERA's UTF-8 `mod.ini`: presentation metadata and resource declarations.
//!
//! The launcher and runtime share this parser so a mod cannot pass one set of
//! INI names in preflight and start with another. The file is data only; it
//! never loads code from a mod.
//! Resource manifest semantics follow the GPLv3 upstream implementation in
//! YuriPlanet/vera20k's `src/util/mod_definition.rs` and `docs/ini-roots.md`.

use std::path::Path;

use crate::util::ini_roots::{IniProfile, IniRootConfig, IniRoots};

const MANIFEST: &str = "mod.ini";
const FILE_LIMIT: u64 = 64 * 1024;
const TEXT_LIMIT: usize = 300;

#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct ArchiveNameOverrides {
    pub expansion_prefix: Option<String>,
    pub map_prefix: Option<String>,
    pub multiplayer: Option<String>,
    pub theme: Option<String>,
    pub movie_prefix: Option<String>,
}

impl ArchiveNameOverrides {
    pub fn validate(&self) -> Result<(), String> {
        for (field, prefix) in [
            ("ExpansionPrefix", self.expansion_prefix.as_deref()),
            ("MapPrefix", self.map_prefix.as_deref()),
            ("MoviePrefix", self.movie_prefix.as_deref()),
        ] {
            if let Some(prefix) = prefix
                && (prefix.len() > 249 || !plain_stem(prefix))
            {
                return Err(format!(
                    "[Archives] {field} must be a plain prefix of ASCII letters, digits, _ or -"
                ));
            }
        }
        for (field, name) in [
            ("Multiplayer", self.multiplayer.as_deref()),
            ("Theme", self.theme.as_deref()),
        ] {
            if let Some(name) = name {
                let stem = name
                    .strip_suffix(".mix")
                    .or_else(|| name.strip_suffix(".MIX"));
                if name.len() > 255
                    || !stem.is_some_and(|stem| plain_stem(stem) && !reserved_device(stem))
                {
                    return Err(format!(
                        "[Archives] {field} must be a plain .mix filename of ASCII letters, digits, _ or -"
                    ));
                }
            }
        }
        Ok(())
    }
}

fn plain_stem(name: &str) -> bool {
    !name.is_empty()
        && name
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || matches!(byte, b'_' | b'-'))
}

fn reserved_device(stem: &str) -> bool {
    let upper = stem.to_ascii_uppercase();
    matches!(upper.as_str(), "CON" | "PRN" | "AUX" | "NUL")
        || (upper.len() == 4
            && (upper.starts_with("COM") || upper.starts_with("LPT"))
            && upper.as_bytes()[3].is_ascii_digit())
}

#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct ModDefinition {
    pub name: Option<String>,
    pub version: Option<String>,
    pub author: Option<String>,
    pub about: Option<String>,
    pub terms: Option<String>,
    pub needs: Option<String>,
    files: Option<IniRootConfig>,
    pub archives: ArchiveNameOverrides,
    pub requires_ares: bool,
    explicit_loading: bool,
}

impl ModDefinition {
    /// A missing manifest is valid; a present but malformed manifest is not.
    pub fn read(dir: &Path) -> Result<Self, String> {
        let Some(path) = crate::util::case_insensitive_path::find(dir, MANIFEST) else {
            return Ok(Self::default());
        };
        let metadata = crate::util::file_system::metadata(&path)
            .map_err(|error| format!("Cannot read {}: {error}", path.display()))?;
        if !metadata.is_file() || metadata.len() > FILE_LIMIT {
            return Err(format!(
                "{} must be a plain file no larger than 64 KiB",
                path.display()
            ));
        }
        let bytes = crate::util::file_system::read(&path)
            .map_err(|error| format!("Cannot read {}: {error}", path.display()))?;
        let text = String::from_utf8_lossy(&bytes);
        Self::parse(&text).map_err(|error| format!("{}: {error}", path.display()))
    }

    pub fn parse(text: &str) -> Result<Self, String> {
        let mut result = Self::default();
        let mut section = String::new();
        for (index, raw) in text.trim_start_matches('\u{feff}').lines().enumerate() {
            let line = raw.split(';').next().unwrap_or_default().trim();
            if line.is_empty() {
                continue;
            }
            if let Some(header) = line.strip_prefix('[') {
                section = header
                    .strip_suffix(']')
                    .ok_or_else(|| format!("line {}: section header must end in ]", index + 1))?
                    .trim()
                    .to_ascii_lowercase();
                if section == "files" {
                    result.files.get_or_insert_with(stock_files);
                }
                result.explicit_loading |=
                    matches!(section.as_str(), "files" | "archives" | "extensions");
                continue;
            }
            let Some((raw_key, raw_value)) = line.split_once('=') else {
                if matches!(section.as_str(), "files" | "archives" | "extensions") {
                    return Err(format!(
                        "line {}: [{section}] expected Key=value",
                        index + 1
                    ));
                }
                continue;
            };
            let key = raw_key.trim().to_ascii_lowercase();
            let value = raw_value.trim();
            match section.as_str() {
                "mod" => result.metadata(&key, value),
                "files" => {
                    let config = result.files.get_or_insert_with(stock_files);
                    let target = match key.as_str() {
                        "rules" => &mut config.rules,
                        "art" => &mut config.art,
                        "ai" => &mut config.ai,
                        "sound" => &mut config.sound,
                        "eva" => &mut config.eva,
                        "theatersuffix" => &mut config.theater_suffix,
                        "theme" => &mut config.theme,
                        "battle" => &mut config.battle,
                        "mission" => &mut config.mission,
                        "mapselect" => &mut config.mapsel,
                        "modes" => &mut config.modes,
                        "mapcatalog" => &mut config.map_catalog,
                        _ => continue,
                    };
                    if value.is_empty() {
                        return Err(format!("[Files] {key} needs a filename"));
                    }
                    *target = Some(value.to_owned());
                }
                "archives" => {
                    let target = match key.as_str() {
                        "expansionprefix" => &mut result.archives.expansion_prefix,
                        "mapprefix" => &mut result.archives.map_prefix,
                        "multiplayer" => &mut result.archives.multiplayer,
                        "theme" => &mut result.archives.theme,
                        "movieprefix" => &mut result.archives.movie_prefix,
                        _ => continue,
                    };
                    *target = Some(value.to_owned());
                }
                "extensions" => {
                    let required = match value.to_ascii_lowercase().as_str() {
                        "yes" | "true" | "1" => true,
                        "no" | "false" | "0" => false,
                        _ => return Err(format!("[Extensions] {key} must be yes or no")),
                    };
                    if key == "ares" {
                        result.requires_ares = required;
                    } else if required {
                        return Err(format!(
                            "required extension '{key}' is not supported by this build"
                        ));
                    }
                }
                _ => {}
            }
        }
        if let Some(files) = &result.files {
            files
                .validate()
                .map_err(|error| format!("[Files]: {error}"))?;
        }
        result.archives.validate()?;
        Ok(result)
    }

    fn metadata(&mut self, key: &str, raw: &str) {
        let target = match key {
            "name" => &mut self.name,
            "version" => &mut self.version,
            "author" => &mut self.author,
            "about" => &mut self.about,
            "terms" => &mut self.terms,
            "needs" => &mut self.needs,
            _ => return,
        };
        let text: String = raw.chars().take(TEXT_LIMIT).collect();
        *target = Some(text)
            .filter(|value| !value.is_empty() && (key == "needs" || !value.contains('\u{fffd}')));
    }

    pub fn title(&self, folder: &str) -> String {
        match (
            self.name.as_deref().unwrap_or(folder),
            self.version.as_deref(),
        ) {
            (name, Some(version)) => format!("{name} {version}"),
            (name, None) => name.to_owned(),
        }
    }

    /// `[Files]` is a stock-based manifest. Without it the user's selected
    /// profile/config remains authoritative.
    pub fn ini_config(&self, base: &IniRootConfig) -> IniRootConfig {
        self.files.clone().unwrap_or_else(|| base.clone())
    }

    /// Whether `[Files]` replaces the host's INI selection for this launch.
    pub fn has_file_manifest(&self) -> bool {
        self.files.is_some()
    }

    pub fn declares_loading(&self) -> bool {
        self.explicit_loading
    }
}

fn stock_files() -> IniRootConfig {
    let stock = IniRoots::default();
    IniRootConfig {
        profile: IniProfile::Custom,
        rules: Some(stock.rules().into()),
        art: Some(stock.art().into()),
        ai: Some(stock.ai().into()),
        sound: Some(stock.sound().into()),
        eva: Some(stock.eva().into()),
        theater_suffix: Some(stock.theater_suffix().into()),
        theme: Some(stock.theme().into()),
        battle: Some(stock.battle().into()),
        mission: Some(stock.mission().into()),
        mapsel: Some(stock.mapsel().into()),
        modes: Some(stock.modes().into()),
        map_catalog: Some(stock.map_catalog().into()),
    }
}

#[cfg(all(test, tests_util))]
mod tests {
    use super::*;

    #[test]
    fn renamed_and_partial_manifest_roots_resolve_from_stock_defaults() {
        let mod_info = ModDefinition::parse(
            "[Files]\nRules=custom-rules.ini\nModes=mode-list.ini\nMapCatalog=maps.pkt\n",
        )
        .unwrap();
        let roots = mod_info
            .ini_config(&IniRootConfig {
                profile: IniProfile::Mo,
                ..Default::default()
            })
            .resolve(|name| matches!(name, "custom-rules.ini" | "artmd.ini" | "aimd.ini"))
            .unwrap();
        assert_eq!(roots.rules(), "custom-rules.ini");
        assert_eq!(roots.art(), "artmd.ini");
        assert_eq!(roots.ai(), "aimd.ini");
        assert_eq!(roots.modes(), "mode-list.ini");
        assert_eq!(roots.map_catalog(), "maps.pkt");
    }

    #[test]
    fn metadata_only_manifest_inherits_host_profile_and_vanilla_fallback_is_unchanged() {
        let manifest = ModDefinition::parse("[Mod]\nName=Just metadata\n").unwrap();
        let base = IniRootConfig {
            profile: IniProfile::Mo,
            ..Default::default()
        };
        assert_eq!(manifest.ini_config(&base), base);
        let vanilla = ModDefinition::default().ini_config(&IniRootConfig::default());
        assert_eq!(vanilla.profile, IniProfile::Yr);
        assert_eq!(vanilla.resolve(|_| true).unwrap().rules(), "rulesmd.ini");
    }

    #[test]
    fn archive_precedence_overrides_are_validated_without_paths() {
        let manifest = ModDefinition::parse("[Archives]\nExpansionPrefix=expand_custom\nMapPrefix=maps_custom\nMultiplayer=custom_multi.mix\n").unwrap();
        assert_eq!(
            manifest.archives.expansion_prefix.as_deref(),
            Some("expand_custom")
        );
        assert_eq!(
            manifest.archives.multiplayer.as_deref(),
            Some("custom_multi.mix")
        );
        assert!(ModDefinition::parse("[Archives]\nExpansionPrefix=../expand\n").is_err());
        assert!(ModDefinition::parse("[Extensions]\nAres=maybe\n").is_err());
        assert!(ModDefinition::parse("[Extensions]\nOther=yes\n").is_err());
    }
}
