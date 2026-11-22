//! Small launcher-readable record of completed stock RA2/YR campaign missions.
//!
//! This is presentation metadata only. Campaign startup, mission selection,
//! map-select routing, and carry-over remain owned by the engine's campaign
//! implementation and save files.

use std::path::Path;

use crate::util::ini_roots::IniProfile;

/// Persist a completed stock mission without allowing a launcher I/O failure
/// to affect the victory transition or simulation state.
pub(crate) fn record_completed_mission(
    data_root: &Path,
    profile: IniProfile,
    campaign_id: &str,
    mission: i32,
    finished: bool,
) -> anyhow::Result<()> {
    let profile = match profile {
        IniProfile::Ra2 => "ra2",
        IniProfile::Yr => "yr",
        _ => return Ok(()),
    };
    let campaign = match campaign_id.to_ascii_lowercase().as_str() {
        "all1" => "all1",
        "sov1" => "sov1",
        _ => return Ok(()),
    };
    let mission = u8::try_from(mission).unwrap_or(0).clamp(0, 7);
    if mission == 0 {
        return Ok(());
    }

    let directory = data_root.join("Campaigns");
    std::fs::create_dir_all(&directory)?;
    let path = directory.join("progress.ini");
    let previous = std::fs::read_to_string(&path).unwrap_or_default();
    let prefix = format!("{profile}.{campaign}.");
    let mut completed = mission;
    let mut was_finished = false;
    for line in previous.lines().map(str::trim) {
        let Some((key, value)) = line.split_once('=') else {
            continue;
        };
        if let Some(field) = key.strip_prefix(&prefix) {
            match field {
                "completed" => {
                    completed = completed.max(value.parse::<u8>().unwrap_or(0).min(7));
                }
                "finished" => was_finished = value == "1",
                _ => {}
            }
        }
    }
    let finished = finished || was_finished;
    let mut lines: Vec<String> = previous
        .lines()
        .filter(|line| !line.trim().starts_with(&prefix))
        .map(str::to_owned)
        .collect();
    if lines.is_empty() {
        lines.push("version=1".to_owned());
    }
    lines.push(format!("{prefix}completed={completed}"));
    lines.push(format!("{prefix}finished={}", u8::from(finished)));
    let mut contents = lines.join("\n");
    contents.push('\n');

    let temporary = directory.join(format!(".progress-{}.tmp", std::process::id()));
    let mut file = std::fs::OpenOptions::new()
        .write(true)
        .create(true)
        .truncate(true)
        .open(&temporary)?;
    use std::io::Write;
    file.write_all(contents.as_bytes())?;
    file.sync_all()?;
    drop(file);
    let backup = directory.join(format!(".progress-{}.bak", std::process::id()));
    let had_previous = path.exists();
    if had_previous {
        if backup.exists() {
            std::fs::remove_file(&backup)?;
        }
        std::fs::rename(&path, &backup)?;
    }
    if let Err(error) = std::fs::rename(&temporary, &path) {
        if had_previous {
            let _ = std::fs::rename(&backup, &path);
        }
        let _ = std::fs::remove_file(&temporary);
        return Err(error.into());
    }
    if had_previous {
        std::fs::remove_file(backup)?;
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn records_stock_progress_monotonically_and_keeps_other_campaigns() {
        let root = std::env::temp_dir().join(format!(
            "vera-campaign-progress-{}-{}",
            std::process::id(),
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_nanos()
        ));
        record_completed_mission(&root, IniProfile::Ra2, "ALL1", 2, false).unwrap();
        record_completed_mission(&root, IniProfile::Ra2, "ALL1", 1, false).unwrap();
        record_completed_mission(&root, IniProfile::Yr, "SOV1", 1, true).unwrap();
        let contents = std::fs::read_to_string(root.join("Campaigns/progress.ini")).unwrap();
        assert!(contents.contains("ra2.all1.completed=2"));
        assert!(contents.contains("ra2.all1.finished=0"));
        assert!(contents.contains("yr.sov1.completed=1"));
        assert!(contents.contains("yr.sov1.finished=1"));
        let _ = std::fs::remove_dir_all(root);
    }
}
