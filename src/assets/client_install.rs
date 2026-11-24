//! Read portable CnCNet client catalogs without running their launcher.
//!
//! Source: https://github.com/CnCNet/xna-cncnet-client/tree/develop/DXMainClient/Domain/Multiplayer
//! MapLoader.cs (LoadMultiMapsAsync/LoadGameModes), GameMode.cs (Initialize/
//! GetMapRulesIniFiles), Map.cs, and GameModeMapBase.cs. These UTF-8 client metadata
//! files are not gamemd INIs; their plural GameModes and relative paths are
//! interpreted here, before the existing game readers consume selected files.

use std::collections::{BTreeMap, BTreeSet, HashMap};
use std::path::{Path, PathBuf};

use crate::util::file_system;

const MAX_INI_BYTES: usize = 4 * 1024 * 1024;
const MAX_LINE_BYTES: usize = 4096;
const MAX_ENTRIES: usize = 65_536;

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ClientInstall {
    pub maps: Vec<ClientMap>,
    pub modes: Vec<ClientMode>,
    pub theme: ClientTheme,
}

/// Optional launcher artwork for equivalent VERA screens.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct ClientTheme {
    pub startup_splash: Option<PathBuf>,
    pub skirmish_background: Option<PathBuf>,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ClientMap {
    /// Actual spelling relative to the selected folder, with `/` separators.
    pub file_name: String,
    pub display_name: Option<String>,
    pub game_modes: Vec<String>,
    pub min_players: Option<u8>,
    pub max_players: Option<u8>,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ClientMode {
    pub name: String,
    pub map_code: Option<PathBuf>,
}

pub(crate) const CLIENT_MODE_PREFIX: &str = "@client-mode/";

impl ClientMode {
    pub(crate) fn override_key(&self) -> String {
        format!("{CLIENT_MODE_PREFIX}{}.ini", self.name.to_ascii_lowercase())
    }
}

impl ClientInstall {
    /// None means this is not a portable client folder. An invalid catalog or
    /// escaping path is an error; unsupported option-bearing rows are omitted.
    pub fn read(root: &Path) -> Result<Option<Self>, String> {
        let root = canonical(root)?;
        let mut paths = ClientPaths::default();
        let Some(definitions) = paths.resolve_file(&root, "Resources/ClientDefinitions.ini")?
        else {
            return Ok(None);
        };
        let definitions = ClientIni::read(&definitions)?;
        let catalog_name = definitions
            .value("Settings", "MPMapsPath")
            .filter(|name| !name.is_empty());
        let Some(catalog) = paths.resolve_file(&root, catalog_name.unwrap_or("INI/MPMaps.ini"))?
        else {
            return match catalog_name {
                Some(name) => Err(format!("Client map catalog is unavailable: {name}")),
                None => Ok(None),
            };
        };
        let catalog = ClientIni::read(&catalog)?;
        let extension = definitions
            .value("Settings", "MapFileExtension")
            .unwrap_or("map");
        if extension.is_empty() || !extension.bytes().all(|c| c.is_ascii_alphanumeric()) {
            return Err("[Settings] MapFileExtension must be a plain file extension".into());
        }

        let mut modes = Vec::new();
        let mut mode_names = BTreeSet::new();
        let listed_modes = catalog.entries("GameModes");
        let names: Vec<_> = if listed_modes.is_empty() {
            catalog
                .entries("MultiMaps")
                .iter()
                .flat_map(|(_, map)| {
                    catalog
                        .value(map, "GameModes")
                        .unwrap_or("Default")
                        .split(',')
                })
                .flat_map(|name| {
                    catalog
                        .value("GameModeAliases", name.trim())
                        .unwrap_or(name)
                        .split(',')
                })
                .map(str::trim)
                .collect()
        } else {
            listed_modes.iter().map(|(_, name)| name.as_str()).collect()
        };
        for name in names {
            if name.is_empty()
                || unsupported(&catalog, name, true)
                || !mode_names.insert(name.to_ascii_lowercase())
            {
                continue;
            }
            let Some(mode) = read_mode(&mut paths, &root, &catalog, name)? else {
                continue;
            };
            modes.push(mode);
        }
        let mode_names: BTreeMap<_, _> = modes
            .iter()
            .map(|mode| (mode.name.to_ascii_lowercase(), mode.name.as_str()))
            .collect();
        let mut maps = Vec::new();
        for (_, name) in catalog.entries("MultiMaps") {
            if name.is_empty() || unsupported(&catalog, name, false) {
                continue;
            }
            let map_path = paths.resolve_file(&root, &format!("{name}.{extension}"))?;
            let Some(map_path) = map_path else { continue };
            let memberships = catalog.value(name, "GameModes").unwrap_or("Default");
            let game_modes: Vec<String> = memberships
                .split(',')
                .map(str::trim)
                .flat_map(|name| {
                    catalog
                        .value("GameModeAliases", name)
                        .unwrap_or(name)
                        .split(',')
                })
                .filter_map(|name| mode_names.get(&name.trim().to_ascii_lowercase()))
                .map(|name| (*name).to_owned())
                .collect();
            if game_modes.is_empty() {
                continue;
            }
            let (Ok(min_players), Ok(max_players)) = (
                player_count(
                    &catalog,
                    name,
                    &["ClientMinPlayer", "MinPlayers", "MinPlayer"],
                ),
                player_count(
                    &catalog,
                    name,
                    &["ClientMaxPlayer", "MaxPlayers", "MaxPlayer"],
                ),
            ) else {
                continue;
            };
            if min_players
                .zip(max_players)
                .is_some_and(|(min, max)| min > max)
                || catalog.enabled(name, "EnforceMinPlayers")
                    && min_players.is_none_or(|min| min > 2)
            {
                continue;
            }
            let relative = map_path
                .strip_prefix(&root)
                .map_err(|_| "Map is outside the selected folder")?;
            let file_name = relative
                .components()
                .map(|part| {
                    part.as_os_str()
                        .to_str()
                        .ok_or("Client map path is not UTF-8")
                })
                .collect::<Result<Vec<_>, _>>()?
                .join("/");
            maps.push(ClientMap {
                file_name,
                display_name: catalog
                    .value(name, "Description")
                    .filter(|text| !text.is_empty())
                    .map(str::to_owned),
                game_modes,
                min_players,
                max_players,
            });
        }
        Ok(Some(Self {
            maps,
            modes,
            theme: read_theme(&mut paths, &root, &definitions),
        }))
    }
}

fn theme_entry(value: &str) -> Option<(&str, &str)> {
    let (name, path) = value.split_once(',')?;
    let name = name.trim();
    (!name.is_empty()).then_some((name, path.trim()))
}

fn read_theme(paths: &mut ClientPaths, root: &Path, definitions: &ClientIni) -> ClientTheme {
    // CnCNet ClientConfiguration.cs and UserINISettings.cs: [Themes] lists
    // name,directory pairs. The user's Theme defaults to entry 0.
    // https://github.com/CnCNet/xna-cncnet-client/tree/0ebe0d60/ClientCore
    let settings_file = definitions
        .value("Settings", "SettingsFile")
        .unwrap_or("Settings.ini");
    let settings = paths
        .resolve_file(root, settings_file)
        .ok()
        .flatten()
        .and_then(|path| ClientIni::read(&path).ok());
    let selected = settings
        .as_ref()
        .and_then(|ini| ini.value("MultiPlayer", "Theme"))
        .and_then(|selection| {
            definitions
                .entries("Themes")
                .iter()
                .filter_map(|(_, value)| theme_entry(value))
                .find(|(name, _)| *name == selection)
                // Older packages store the numbered entry in Theme.
                .or_else(|| {
                    let index = selection.parse::<u32>().ok()?;
                    theme_entry(definitions.value("Themes", &index.to_string())?)
                })
        });
    let directory = selected
        .or_else(|| definitions.value("Themes", "0").and_then(theme_entry))
        .map(|(_, directory)| directory)
        .filter(|directory| !directory.is_empty());

    // Startup.cs and GameClass.cs search the chosen resource directory
    // before the base Resources. Theme directories can end in a separator.
    let mut resource = |name: &str| {
        path_parts(name).ok()?;
        if let Some(directory) = directory {
            let directory = directory.trim_end_matches(['/', '\\']);
            let path = paths
                .resolve_file(root, &format!("Resources/{directory}/{name}"))
                .ok()?;
            if path.is_some() {
                return path;
            }
        }
        paths
            .resolve_file(root, &format!("Resources/{name}"))
            .ok()
            .flatten()
    };

    // GameLobbyBase.Initialize loads gamelobbybg.png before loading the
    // window's INI. INItializableWindow reads the named theme INI first.
    let skirmish = resource("SkirmishLobby.ini").and_then(|path| ClientIni::read(&path).ok());
    let background = skirmish
        .as_ref()
        .and_then(|ini| ini.value("SkirmishLobby", "BackgroundTexture"))
        .unwrap_or("gamelobbybg.png");
    ClientTheme {
        startup_splash: resource("splashScreen.png"),
        skirmish_background: resource(background),
    }
}

// Missing explicitly named code omits the mode. A missing implicit default
// remains None, as the client's IniFile constructor reads it as an empty file.
fn read_mode(
    paths: &mut ClientPaths,
    root: &Path,
    ini: &ClientIni,
    name: &str,
) -> Result<Option<ClientMode>, String> {
    let map_code = if let Some(legacy) = ini
        .value(name, "CustomIniPath")
        .filter(|value| !value.is_empty())
    {
        match paths.resolve_file(root, legacy)? {
            Some(path) => Some(path),
            None => {
                let parts = path_parts(legacy)?;
                if parts.len() != 2 || !parts[0].eq_ignore_ascii_case("INI") {
                    return Ok(None);
                }
                let Some(path) = paths.resolve_file(root, &format!("INI/Map Code/{}", parts[1]))?
                else {
                    return Ok(None);
                };
                Some(path)
            }
        }
    } else {
        let explicit = ini
            .value(name, "MapCodeIniName")
            .filter(|value| !value.is_empty());
        let default = format!("{name}.ini");
        let code_name = explicit.unwrap_or(&default);
        // Validate before prefixing, so an absolute path cannot become relative.
        path_parts(code_name)?;
        let code = paths.resolve_file(root, &format!("INI/Map Code/{code_name}"))?;
        if explicit.is_some() && code.is_none() {
            return Ok(None);
        }
        code
    };
    Ok(Some(ClientMode {
        name: name.to_owned(),
        map_code,
    }))
}

fn unsupported(ini: &ClientIni, section: &str, mode: bool) -> bool {
    let has = |key| {
        ini.value(section, key)
            .is_some_and(|value| !value.is_empty())
    };
    if [
        "ForcedOptions",
        "BaseSection",
        "AllowedStartingLocations",
        "DisallowedPlayerSides",
        "DisallowedHumanPlayerSides",
        "DisallowedComputerPlayerSides",
        "RandomizedMapCodeIniNames",
        "ExtraIniName",
        "MinPlayersOverride",
        "MaxPlayersOverride",
        "CoopDifficultyLevel",
        "Bases",
    ]
    .iter()
    .any(|key| has(key))
        || ini
            .value(section, "ForcedSpawnIniOptions")
            .is_some_and(|group| !group.is_empty() && (!mode || !ini.entries(group).is_empty()))
        || [
            "MultiplayerOnly",
            "ClientMultiplayerOnly",
            "HumanPlayersOnly",
            "IsCoopMission",
            "ForceRandomStartLocations",
            "ForceNoTeams",
            "UseDifficultyDropDown",
        ]
        .iter()
        .any(|key| ini.enabled(section, key))
        || ["Credits", "UnitCount", "NeutralColor", "SpecialColor"]
            .iter()
            .any(|key| {
                ini.value(section, key)
                    .is_some_and(|value| !value.is_empty() && value != "-1")
            })
    {
        return true;
    }
    if !mode && has("CustomIniPath") {
        return true;
    }
    mode && (ini.enabled(section, "EnforceMinPlayers")
        || !ini
            .entries(&format!("{section}ForcedSpawnIniOptions"))
            .is_empty())
}

fn player_count(ini: &ClientIni, section: &str, keys: &[&str]) -> Result<Option<u8>, ()> {
    keys.iter()
        .find_map(|key| ini.value(section, key))
        .map(|value| value.parse::<u8>().map_err(|_| ()))
        .transpose()
}

fn path_parts(name: &str) -> Result<Vec<&str>, String> {
    let parts: Vec<_> = name.split(['/', '\\']).collect();
    if name.len() > MAX_LINE_BYTES
        || parts.iter().any(|part| {
            part.is_empty()
                || matches!(*part, "." | "..")
                || part.contains(':')
                || part.chars().any(char::is_control)
        })
    {
        return Err(format!(
            "Client path must stay inside the selected folder: {name:?}"
        ));
    }
    Ok(parts)
}

/// One directory snapshot per catalog read, shared by maps, modes and artwork.
#[derive(Default)]
struct ClientPaths {
    directories: HashMap<PathBuf, HashMap<String, PathBuf>>,
}

impl ClientPaths {
    /// Keep the same case collision order as the normal asset path lookup.
    fn index(directory: &Path) -> HashMap<String, PathBuf> {
        let mut names = HashMap::new();
        if let Ok(entries) = file_system::read_dir(directory) {
            for entry in entries.filter_map(Result::ok) {
                let key = entry.file_name().to_string_lossy().to_ascii_lowercase();
                let path = entry.path();
                let winner = names.entry(key).or_insert_with(|| path.clone());
                if path < *winner {
                    *winner = path;
                }
            }
        }
        names
    }

    /// Canonicalize each component before descending, including cache hits.
    fn resolve_file(&mut self, root: &Path, name: &str) -> Result<Option<PathBuf>, String> {
        let mut path = root.to_owned();
        for part in path_parts(name)? {
            let names = self
                .directories
                .entry(path.clone())
                .or_insert_with(|| Self::index(&path));
            let Some(found) = names.get(&part.to_ascii_lowercase()) else {
                return Ok(None);
            };
            path = canonical(found)?;
            if !path.starts_with(root) {
                return Err(format!(
                    "Client path resolves outside the selected folder: {name}"
                ));
            }
        }
        Ok(file_system::is_file(&path).then_some(path))
    }
}

fn canonical(path: &Path) -> Result<PathBuf, String> {
    #[cfg(not(all(feature = "wasm", target_arch = "wasm32", target_os = "unknown")))]
    {
        file_system::canonicalize(path)
            .map_err(|error| format!("Cannot resolve {}: {error}", path.display()))
    }
    // The browser's in-memory filesystem has no symlinks. All child segments
    // have already been validated; this also builds without wasm-net.
    #[cfg(all(feature = "wasm", target_arch = "wasm32", target_os = "unknown"))]
    {
        Ok(path.to_path_buf())
    }
}

/// A bounded reader for the client's metadata syntax. Row order is retained;
/// repeated keys replace their value without changing that order.
#[derive(Default)]
struct ClientIni(BTreeMap<String, Vec<(String, String)>>);

impl ClientIni {
    fn read(path: &Path) -> Result<Self, String> {
        #[cfg(not(all(feature = "wasm", target_arch = "wasm32", target_os = "unknown")))]
        let bytes = {
            use std::io::Read;
            let mut bytes = Vec::new();
            file_system::File::open(path)
                .and_then(|file| {
                    file.take((MAX_INI_BYTES + 1) as u64)
                        .read_to_end(&mut bytes)
                })
                .map_err(|error| format!("Cannot read {}: {error}", path.display()))?;
            bytes
        };
        #[cfg(all(feature = "wasm", target_arch = "wasm32", target_os = "unknown"))]
        let bytes = file_system::read(path)
            .map_err(|error| format!("Cannot read {}: {error}", path.display()))?;
        if bytes.len() > MAX_INI_BYTES {
            return Err(format!("Client catalog exceeds 4 MiB: {}", path.display()));
        }
        let text = String::from_utf8_lossy(&bytes);
        let mut ini = Self::default();
        let mut section = String::new();
        let mut count = 0;
        // Parsing-only index avoids quadratic scans of large MultiMaps lists.
        // It is discarded once the ordered rows become immutable.
        let mut positions = BTreeMap::<(String, String), usize>::new();
        for line in text.trim_start_matches('\u{feff}').lines() {
            if line.len() > MAX_LINE_BYTES {
                return Err(format!(
                    "Client catalog line exceeds 4096 bytes: {}",
                    path.display()
                ));
            }
            let line = line.split(';').next().unwrap_or_default().trim();
            if let Some(header) = line
                .strip_prefix('[')
                .and_then(|line| line.strip_suffix(']'))
            {
                section = header.trim().to_ascii_lowercase();
            } else if let Some((key, value)) = line.split_once('=') {
                count += 1;
                if count > MAX_ENTRIES {
                    return Err(format!(
                        "Client catalog has too many entries: {}",
                        path.display()
                    ));
                }
                let entries = ini.0.entry(section.clone()).or_default();
                let key = key.trim();
                let value = value.trim().to_owned();
                let lookup = (section.clone(), key.to_ascii_lowercase());
                if let Some(index) = positions.get(&lookup).copied() {
                    entries[index].1 = value;
                } else {
                    positions.insert(lookup, entries.len());
                    entries.push((key.to_owned(), value));
                }
            }
        }
        Ok(ini)
    }

    fn entries(&self, section: &str) -> &[(String, String)] {
        self.0
            .get(&section.to_ascii_lowercase())
            .map(Vec::as_slice)
            .unwrap_or_default()
    }

    fn value(&self, section: &str, key: &str) -> Option<&str> {
        self.entries(section)
            .iter()
            .find(|(name, _)| name.eq_ignore_ascii_case(key))
            .map(|(_, value)| value.as_str())
    }

    fn enabled(&self, section: &str, key: &str) -> bool {
        self.value(section, key).is_some_and(|value| {
            !matches!(
                value.to_ascii_lowercase().as_str(),
                "" | "no" | "false" | "0"
            )
        })
    }
}

#[cfg(all(test, tests_assets))]
mod tests {
    use super::*;
    use crate::assets::asset_manager::test_support::TestDirectory;

    #[test]
    fn map_memberships_supply_modes_when_the_mode_table_is_absent() {
        let fixture = TestDirectory::new("client-map-memberships");
        let write = |name, bytes: &[u8]| {
            let path = fixture.path().join(name);
            std::fs::create_dir_all(path.parent().unwrap()).unwrap();
            std::fs::write(path, bytes).unwrap();
        };
        write(
            "Resources/ClientDefinitions.ini",
            b"[Settings]\nMPMapsPath=INI/MPMaps.ini\n",
        );
        write("INI/MPMaps.ini", b"[MultiMaps]\n0=Maps/One\n[Maps/One]\nGameModes=Standard,Locked\n[Standard]\nForcedSpawnIniOptions=EmptyOptions\nMapCodeININame=Standard.ini\n[EmptyOptions]\n[Locked]\nForcedSpawnIniOptions=Locks\n[Locks]\nFog=yes\n");
        write("INI/Map Code/Standard.ini", b"[General]\n");
        write("Maps/One.map", b"[Basic]\n");
        let client = ClientInstall::read(fixture.path()).unwrap().unwrap();
        assert_eq!(client.modes.len(), 1);
        assert_eq!(client.maps[0].game_modes, ["Standard"]);
        assert!(
            client.modes[0]
                .map_code
                .as_ref()
                .unwrap()
                .ends_with("INI/Map Code/Standard.ini")
        );
    }

    #[test]
    fn client_paths_reuse_listings_only_for_one_catalog_read() {
        let fixture = TestDirectory::new("client-path-snapshot");
        let root = canonical(fixture.path()).unwrap();
        std::fs::create_dir(root.join("Maps")).unwrap();
        std::fs::write(root.join("Maps/First.MAP"), []).unwrap();

        let mut paths = ClientPaths::default();
        assert!(
            paths
                .resolve_file(&root, "maps/first.map")
                .unwrap()
                .is_some()
        );
        std::fs::write(root.join("Maps/Second.MAP"), []).unwrap();
        assert!(
            paths
                .resolve_file(&root, "MAPS/second.map")
                .unwrap()
                .is_none()
        );
        assert!(
            ClientPaths::default()
                .resolve_file(&root, "MAPS/second.map")
                .unwrap()
                .is_some()
        );
    }

    #[test]
    fn client_theme_uses_selected_lobby_art_and_base_splash() {
        let dir = TestDirectory::new("client-theme");
        let resources = dir.path().join("Resources");
        std::fs::create_dir_all(resources.join("Alternate/Pictures")).unwrap();
        std::fs::create_dir_all(dir.path().join("INI")).unwrap();
        std::fs::write(
            resources.join("ClientDefinitions.ini"),
            "[Themes]\n0=Default,\n1=Other,alternate/\n[Settings]\nSettingsFile=client.ini\n",
        )
        .unwrap();
        std::fs::write(dir.path().join("INI/MPMaps.ini"), "[GameModes]\n").unwrap();
        std::fs::write(resources.join("SplashScreen.PNG"), []).unwrap();
        std::fs::write(resources.join("gamelobbybg.png"), []).unwrap();
        std::fs::write(resources.join("Alternate/Pictures/Lobby.PNG"), []).unwrap();
        std::fs::write(
            resources.join("Alternate/SkirmishLobby.INI"),
            "[SkirmishLobby]\nBackgroundTexture=pictures\\lobby.png\n",
        )
        .unwrap();
        for selection in ["Other", "1"] {
            std::fs::write(
                dir.path().join("client.ini"),
                format!("[MultiPlayer]\nTheme={selection}\n"),
            )
            .unwrap();
            let theme = ClientInstall::read(dir.path()).unwrap().unwrap().theme;
            assert_eq!(
                theme,
                ClientTheme {
                    startup_splash: Some(canonical(&resources.join("SplashScreen.PNG")).unwrap()),
                    skirmish_background: Some(
                        canonical(&resources.join("Alternate/Pictures/Lobby.PNG")).unwrap()
                    ),
                }
            );
        }
    }

    #[test]
    fn invalid_theme_background_does_not_reject_a_valid_map() {
        let dir = TestDirectory::new("client-theme-invalid");
        let resources = dir.path().join("Resources");
        std::fs::create_dir_all(&resources).unwrap();
        std::fs::create_dir_all(dir.path().join("INI")).unwrap();
        std::fs::write(
            resources.join("ClientDefinitions.ini"),
            "[Themes]\n0=Default,\n",
        )
        .unwrap();
        std::fs::write(
            dir.path().join("INI/MPMaps.ini"),
            "[GameModes]\n0=Default\n[MultiMaps]\n0=Arena\n",
        )
        .unwrap();
        std::fs::write(dir.path().join("Arena.map"), []).unwrap();
        std::fs::write(dir.path().join("outside.png"), []).unwrap();
        std::fs::write(
            resources.join("SkirmishLobby.ini"),
            "[SkirmishLobby]\nBackgroundTexture=../outside.png\n",
        )
        .unwrap();

        let install = ClientInstall::read(dir.path()).unwrap().unwrap();
        assert_eq!(install.theme.skirmish_background, None);
        assert_eq!(install.maps[0].file_name, "Arena.map");
    }

    #[test]
    fn client_metadata_resolves_real_spelling_and_plain_modes() {
        let dir = TestDirectory::new("client-install");
        for name in ["rEsOuRcEs", "INI/Map Code", "Maps/Sub"] {
            std::fs::create_dir_all(dir.path().join(name)).unwrap();
        }
        std::fs::write(
            dir.path().join("rEsOuRcEs/ClientDefinitions.INI"),
            "[Settings]\nMPMapsPath=ini/maps.ini\n",
        )
        .unwrap();
        std::fs::write(dir.path().join("INI/Maps.ini"), "[GameModes]\n0=Plain\n1=Modern\n2=Locked\n3=Online\n4=Literal\n[Plain]\nCustomIniPath=INI\\Plain.ini\n[Modern]\nMapCodeIniName=modern.ini\n[Literal]\nCustomIniPath=INI\\Literal.ini\n[Locked]\nForcedOptions=Locks\n[Online]\nMultiplayerOnly=yes\n[MultiMaps]\n0=maps\\sub\\alpha\n1=maps\\sub\\locked\n2=maps\\sub\\missing\n[maps\\sub\\alpha]\nDescription=Alpha\nGameModes=Plain,Modern,Locked,Online\nMinPlayers=1\nMaxPlayers=4\n[maps\\sub\\locked]\nGameModes=Plain\nForcedOptions=Locks\n").unwrap();
        for name in [
            "INI/Map Code/Plain.ini",
            "INI/Map Code/Modern.ini",
            "INI/Literal.ini",
            "INI/Map Code/Literal.ini",
            "Maps/Sub/Alpha.MAP",
            "Maps/Sub/Locked.map",
        ] {
            std::fs::write(dir.path().join(name), "[General]\n").unwrap();
        }
        let install = ClientInstall::read(dir.path()).unwrap().unwrap();
        assert_eq!(
            install
                .modes
                .iter()
                .map(|mode| mode.name.as_str())
                .collect::<Vec<_>>(),
            ["Plain", "Modern", "Literal"]
        );
        assert_eq!(
            install.modes[0].map_code,
            Some(canonical(&dir.path().join("INI/Map Code/Plain.ini")).unwrap())
        );
        assert_eq!(
            install.modes[1].map_code,
            Some(canonical(&dir.path().join("INI/Map Code/Modern.ini")).unwrap())
        );
        assert_eq!(
            install.modes[2].map_code,
            Some(canonical(&dir.path().join("INI/Literal.ini")).unwrap())
        );
        assert_eq!(
            install.maps,
            [ClientMap {
                file_name: "Maps/Sub/Alpha.MAP".into(),
                display_name: Some("Alpha".into()),
                game_modes: vec!["Plain".into(), "Modern".into()],
                min_players: Some(1),
                max_players: Some(4)
            }]
        );
    }

    #[test]
    fn unavailable_and_escaping_client_files_are_not_imported() {
        let dir = TestDirectory::new("client-invalid");
        assert_eq!(ClientInstall::read(dir.path()).unwrap(), None);
        std::fs::create_dir_all(dir.path().join("Resources")).unwrap();
        let definitions = dir.path().join("Resources/ClientDefinitions.ini");
        std::fs::write(&definitions, "[Settings]\nMPMapsPath=missing.ini\n").unwrap();
        assert!(
            ClientInstall::read(dir.path())
                .unwrap_err()
                .contains("unavailable")
        );
        std::fs::write(&definitions, "[Settings]\nMPMapsPath=../outside.ini\n").unwrap();
        assert!(
            ClientInstall::read(dir.path())
                .unwrap_err()
                .contains("inside")
        );
        #[cfg(unix)]
        {
            let outside = TestDirectory::new("client-outside");
            std::fs::write(outside.path().join("maps.ini"), "[GameModes]\n").unwrap();
            std::os::unix::fs::symlink(
                outside.path().join("maps.ini"),
                dir.path().join("escape.ini"),
            )
            .unwrap();
            std::fs::write(&definitions, "[Settings]\nMPMapsPath=escape.ini\n").unwrap();
            assert!(
                ClientInstall::read(dir.path())
                    .unwrap_err()
                    .contains("outside")
            );
        }
    }
}
