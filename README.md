# FindTrade

A Minecraft plugin for finding villagers with specific enchantment trades using visual particle guidance.

![Demo Video](https://github.com/user-attachments/assets/4098bd03-362b-4079-b558-4b7bf99ae9cc)

## Features

- **Enchantment Search**: Find nearby villagers selling specific enchantment books
- **Pathfinding**: Realistic walkable path visualization using the [pathetic-bukkit](https://github.com/bsommerfeld/pathetic-bukkit) library
- **Region Management**: Define regions to identify villager locations easily
- **Interactive TUI**: Text-based user interface for search results and region management
- **Customizable Particles**: Configure multiple particle types for pillar and path effects

## Commands

### `/findtrade search <enchantment>`
Search for nearby villagers offering a specific enchantment book.

- Displays results in an interactive TUI
- Click on a result to show particle effects guiding you to the villager
- Shows region name if the villager is within a defined region

### `/findtrade region`
Opens the region management TUI for creating, editing, and deleting regions.

- **Create**: Define a region with a name and coordinates (supports WorldEdit selection)
- **List**: View all defined regions
- **Edit**: Modify region name or coordinates
- **Delete**: Remove a region

**Aliases**: `/ft`

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `findtrade.use` | Use search and view regions | `true` |
| `findtrade.write` | Create, edit, and delete regions | `op` |

## Configuration

The configuration file is located at `plugins/FindTrade/config.yml`.

### Particle Effects

```yaml
particle-effects:
  duration: 30  # How long particles are displayed (seconds)

  # Pillar particles above villager location
  pillar:
    enabled: true
    interval: 1
    particles:
      - type: "HAPPY_VILLAGER"
        height: 20
        count: 10
      - type: "END_ROD"
        height: 20
        count: 5

  # Path particles between player and villager
  path:
    enabled: true
    use-pathfinding: true  # false = straight line (lower server load)
    update-interval: 0.1
    particles:
      - type: "END_ROD"
        count: 1
        offset-x: 0.5
        offset-y: 0.5
        offset-z: 0.5
        randomness-x: 0.0
        randomness-y: 0.0
        randomness-z: 0.0
        speed: 0.0
```

### Pathfinding

The plugin uses A* pathfinding to show realistic walkable paths. This runs asynchronously and has minimal server impact. If you experience performance issues, set `use-pathfinding: false` to use simple straight-line visualization.

## Localization

Language files are located in `plugins/FindTrade/localization/`. Supported languages:
- English (`en.yml`)
- Korean (`ko.yml`)

## Dependencies

- **WorldEdit** (optional) - For region selection using WorldEdit wand

## Installation

1. Download the latest release from the releases page
2. Place the `.jar` file in your server's `plugins` folder
3. Restart your server

## Changelog

### 0.4.0
- **Major refactor**: Consolidated commands into `/findtrade`
- Added A* pathfinding for realistic path visualization (pathetic-bukkit)
- Added interactive TUI for search results and region management
- Configurable multiple particle types for pillar and path effects
- Added particle offset, randomness, and speed settings
- Improved fallback to straight line when pathfinding fails

### 0.3.0
- Added EasyVillagerTrade integration with TUI interface
- Added interactive Text User Interface for enchantment management
- Enhanced search to include both registered and nearby villagers
- Added clickable chat messages for better interaction

### 0.2.0
- Added region-based villager trade management
- Enhanced `/findvillager` command to search for specific enchantments
- Added WorldEdit integration for region selection

### 0.1.0
- Initial release
- Basic villager trade management
- Enchantment search functionality
- Particle effect visualization

## License

This project is licensed under the MIT License.
