# VelocityWhitelist

A simple whitelist / blacklist plugin for [velocity](https://github.com/PaperMC/Velocity)

Tested with velocity `3.3.0`, java 17

## Files

### Config

File location: `plugins/velocitywhitelist/config.yml`

```yaml
# Config file version. Do not edit it
_version: 1

# The way to identify a player
# Options: name, uuid. Default: name
# For online servers, it's suggested to use the uuid mode 
# since it can keep tracking on the mojang account of the player
identify_mode: name

# If the whitelist functionality is enabled
whitelist_enabled: true
# Message sent to those not whitelisted players
whitelist_kick_message: You are not in the whitelist!

# If the blacklist functionality is enabled
blacklist_enabled: true
# Message sent to those blacklisted players
blacklist_kick_message: You are banned from the server!
```

### Whitelist / Blacklist

File location: `plugins/velocitywhitelist/whitelist.yml`, `plugins/velocitywhitelist/blacklist.yml`

```yaml
# Listed player names. Used in "name" mode only
names:
- Fallen_Breath
- Steve

# Listed player UUIDs. Used in "uuid" mode only
uuids:
- 85dbd009-69ed-3cc4-b6b6-ac1e6d07202e
- 5c93374f-2d55-3003-a4b5-ca885736fb0f
```

Additionally, items of the `uuids` list can be a map containing exactly 1 entry, indicating uuid to the player name.
This is designed for easier identifying what the UUID belongs to

```yaml
uuids:
- 85dbd009-69ed-3cc4-b6b6-ac1e6d07202e: Fallen_Breath
- 5c93374f-2d55-3003-a4b5-ca885736fb0f: Steve
```

## Command

Require permission `velocitywhitelist.command`

- `/whitelist` and `/vwhitelist` (alias) commands are for whitelist control
- `/blacklist` and `/vblacklist` (alias) commands are for whitelist control

Let's use `/whitelist` as demonstration

- `/whitelist`: Show plugin status
- `/whitelist add <value>`: Add a player to the whitelist
- `/whitelist remove <value>`: Remove a player from the whitelist
- `/whitelist list`: List all whitelist players
- `/whitelist reload`: Reload whitelist from whitelist file from the disk. Notes that config will not be reloaded

For player operation commands, `<value>` has different meaning depends on the identity mode:

- `name` mode: `<value>` should be the name of the player
- `uuid` mode: `<value>` should be the UUID of the player, or the name of the player. 
  If it's a player name, and the player is connected to the proxy, the player's UUID will be used, 
  otherwise it will try to fetch and use the player's online UUID from mojang API

## TODO

- [x] UUID support
- [ ] IP ban for blacklist
