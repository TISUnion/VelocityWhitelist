# VelocityWhitelist

A simple whitelist plugin for [velocity](https://github.com/PaperMC/Velocity)

Currently, it only supports name-based whitelist

Tested with velocity `3.3.0`

## File location

- Config file: `plugins/velocitywhitelist/config.yml`
- Whitelist file: `plugins/velocitywhitelist/whitelist.yml`

## Command

Require permission `velocitywhitelist.command`

`/vwhitelist` is an alias of `/whitelist`

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

- [ ] UUID support
