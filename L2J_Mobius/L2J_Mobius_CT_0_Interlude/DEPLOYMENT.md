# L2J Mobius CT 0 Interlude — Deployment Guide

## Project Structure

```
L2J_Mobius_CT_0_Interlude/          ← Project root (source code)
├── java/                            Java source files
├── dist/game/                       Config templates (e.g. log.cfg)
├── build.xml                        Ant build configuration
├── build-and-deploy.sh              Build & deploy (compile + deploy)
├── deploy.sh                        Deploy only (pre-built JARs + configs)
├── rollback.sh                      Restore previous version from backup
└── DEPLOYMENT.md                    This document

../build/dist/libs/                  Compiled JARs (build output)

~/server/                            Running server
├── game/                            Game server config & logs
│   ├── config/
│   ├── data/
│   ├── log/                         All log files
│   └── log.cfg                      Logging configuration
├── login/                           Login server config & logs
│   └── log/
├── libs/                            Server JARs
│   ├── GameServer.jar
│   ├── LoginServer.jar
│   └── *.jar                        Dependencies
└── backup/                          Backup versions (timestamped)
    └── backup_YYYYMMDD_HHMMSS/
```

## Scripts

### build-and-deploy.sh

Full-cycle build and deploy (recommended for daily development):
1. Compile all Java source files (`ant compile`)
2. Build JAR files (`ant jar`)
3. Call `deploy.sh` (stop → backup → deploy → start → verify)

**Duration:** ~30–60 seconds.

### deploy.sh

Deploy pre-built JARs and config files **without recompiling**. Use this when only config files or non-Java assets changed.

Steps performed:
1. Verify build artifacts exist
2. Create timestamped backup of current JARs and `log.cfg`
3. Stop the server (`sudo systemctl stop l2game l2login`)
4. Copy JARs from `../build/dist/libs/` to `~/server/libs/`
5. Update `log.cfg` in `~/server/game/`
6. Verify deployed file timestamps
7. Start the server (`sudo systemctl start l2login`, wait 10s, `sudo systemctl start l2game`)
8. Print server status and last 20 lines of `game.log`

Options:
- `--help`, `-h` — Show usage
- `--verify-only` — Check files only, no deployment
- `--no-restart` — Deploy without restarting the server

### rollback.sh

Restore a previous version from backup:
1. List all available backups (newest first) with timestamps
2. Prompt to choose a backup by number
3. Ask for confirmation
4. Stop the server
5. Restore `GameServer.jar`, `LoginServer.jar`, and `log.cfg` from the selected backup
6. Verify restored files via byte comparison
7. Start the server

## Quick Commands

All commands are run from the project root.

### Build & Deploy (Recommended)
```bash
./build-and-deploy.sh
```

### Deploy Only (No Compilation)
```bash
./deploy.sh
```

### Manual Build Only
```bash
/usr/bin/ant compile   # Compile only
/usr/bin/ant jar       # Build JARs
/usr/bin/ant cleanup   # Full build with cleanup (removes old artifacts)
```

### Rollback
```bash
./rollback.sh
```

### Server Control
```bash
sudo systemctl stop l2game     # Stop game server
sudo systemctl start l2game    # Start game server
sudo systemctl status l2game   # Check status
sudo systemctl restart l2game  # Restart game server
```

## What Gets Deployed

| Artifact | Source | Destination |
|----------|--------|-------------|
| `GameServer.jar` | `../build/dist/libs/` | `~/server/libs/` |
| `LoginServer.jar` | `../build/dist/libs/` | `~/server/libs/` |
| `log.cfg` | `dist/game/log.cfg` | `~/server/game/log.cfg` |

## Directory Paths

| Path | Purpose |
|------|---------|
| `.` | Project root (source code) |
| `java/` | Java source files |
| `dist/game/` | Config templates |
| `../build/dist/libs/` | Compiled JARs (build output) |
| `~/server/` | Running server root |
| `~/server/libs/` | Running server JARs |
| `~/server/game/` | Game server config & logs |
| `~/server/game/log/` | Log files directory |
| `~/server/login/log/` | Login server logs |
| `~/server/backup/` | Backup versions (timestamped) |

## Build Duration

| Phase | Duration |
|-------|----------|
| Compilation | ~8–15 seconds |
| JAR creation | ~1–2 seconds |
| Server stop/start | ~20–30 seconds |
| File copy & verify | ~1 second |
| **Total** | **~30–60 seconds** |

## Logging

### Log File Locations

All logs reside under `~/server/game/log/`:

| File | Content |
|------|---------|
| `game.log` | Main game server log |
| `chat%g.log` | Chat messages |
| `item%g.log` | Item transactions |
| `gmaudit%g.log` | GM actions |
| `error%g.log` | Error logs |
| `playeraction%g.log` | Player action log (see below) |

Log files rotate automatically when they exceed 100 MB. A maximum of 20 rotated files are kept per log type.

### Player Action Logs

Records per-event details:
- Player name
- Action type (select target, move, attack, skill use, item use)
- Real-time stats (HP, MP, CP, P.Atk, M.Atk, P.Def, M.Def, Level)
- Equipment information (for attacks and target selection)
- Timestamp

### Viewing Logs
```bash
tail -20 ~/server/game/log/game.log            # Last 20 lines
tail -f ~/server/game/log/game.log             # Live follow
tail -f ~/server/game/log/playeraction0.log    # Live player action log
```

## Backup Management

- Backups are created automatically **before each deployment**.
- Location: `~/server/backup/backup_YYYYMMDD_HHMMSS/`
- Contents: `GameServer.jar`, `LoginServer.jar`, `log.cfg`
- Backups are kept indefinitely; manual cleanup can be used if disk space becomes an issue.
- To restore, use `./rollback.sh` or manually copy:
  ```bash
  cp ~/server/backup/<backup_name>/*.jar ~/server/libs/
  cp ~/server/backup/<backup_name>/log.cfg ~/server/game/
  ```

## Workflow

### After Code Changes
1. Edit source files in `java/`
2. Run `./build-and-deploy.sh`
3. Wait for completion (~30–60 s)
4. Verify logs:
   ```bash
   tail -f ~/server/game/log/game.log
   ```

### After Config-only Changes
1. Edit config files in `dist/game/`
2. Run `./deploy.sh`
3. Verify logs

### If Something Goes Wrong
1. Run `./rollback.sh`
2. Select the previous working backup
3. Check server status:
   ```bash
   sudo systemctl status l2game
   ```

## Troubleshooting

| Issue | Solution |
|-------|----------|
| "Permission denied" on scripts | `chmod +x <script>.sh` |
| Build fails with compilation error | Read the error, fix the code, re-run `./build-and-deploy.sh` |
| "Server failed to stop" | Check if process is frozen; restart manually if needed |
| Server won't start after deploy | Check logs: `tail -50 ~/server/game/log/error%g.log`, then run `./rollback.sh` |
| "JARs not deployed" | Verify `../build/dist/libs/` exists and contains JARs |
| "Source files not found" | Confirm paths match the directory structure |
| "Compilation errors" | Check Java syntax; ensure Java 25 is installed |
| Can't find log files | Game logs: `~/server/game/log/`; Login logs: `~/server/login/log/` |
| Service status unknown | `sudo systemctl status l2game` or `journalctl -u l2game -n 20` |

### Forced Process Kill
```bash
ps aux | grep -i gameserver
sudo kill -9 <PID>    # Only if the server is unresponsive
```

## Permissions

- All scripts require execute permission (`chmod +x`). The following should have it:
  - `build-and-deploy.sh`
  - `deploy.sh`
  - `rollback.sh`
- The systemd service (`l2game`) runs as the `l2server` user.
- `sudo` is required for `systemctl` commands.

## Notes

- Backups use the server's system clock — ensure the system time is accurate.
- All paths in scripts use `$HOME`-relative notation; no hardcoded absolute paths.