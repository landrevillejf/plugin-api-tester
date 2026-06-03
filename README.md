# 🔧 Plugin API Tester - Developer Console

**Version:** 2.0.0  
**Author:** Jean-Francois Landreville  
**Category:** Developer Tools  
**Required Host Version:** 3.0.0+

---

## 📋 Overview

The **Plugin API Tester** (also known as Developer Console) is a comprehensive testing and debugging tool for plugin developers. It provides a command-line interface within the IDE to test all available plugin services, monitor system resources, manage plugins, and debug plugin interactions in real-time.

---

## ✨ Features

### 🎮 Interactive Console
- Command-line interface with syntax highlighting
- Command history navigation (Up/Down arrows)
- Command auto-completion (Tab key / dropdown)
- Real-time command execution feedback
- Execution time measurement for commands

### 🔌 Plugin Management
- List all loaded plugins with their versions and states
- View detailed plugin information
- Enable/disable plugins dynamically
- Reload plugins without restarting the IDE

### 🛠️ Service Testing
- **Cache Service** - Test caching with put/get/remove operations
- **Notification Service** - Test desktop notifications
- **Logging Service** - Test log messages at all levels (DEBUG, INFO, WARN, ERROR)
- **Metrics Service** - View plugin metrics and statistics
- **Data Store Service** - Save, load, delete, and list stored data

### 💻 System Monitoring
- **Memory Usage** - Real-time heap and non-heap memory monitoring
- **Thread Information** - List all threads with CPU time
- **Garbage Collection** - Manual GC trigger with memory impact display
- **System Information** - OS, Java version, processors, environment variables
- **Real-time Status Bar** - Live memory and thread count display

### 📊 Data Management
- Persistent data storage for plugin testing
- Key-value store with save/load/delete/list operations
- Cross-session data persistence

---

## 🚀 Installation

1. Place the `dev-console.jar` in your IDE's `plugins/` directory
2. Restart the IDE or enable the plugin via the Plugin Manager
3. The console panel will appear at the bottom of the IDE

---

## 🎯 Usage

### Opening the Console

- **Menu:** `Tools → Developer Console`
- **Shortcut:** `Ctrl+Shift+C` (or `Cmd+Shift+C` on Mac)
- **Toolbar:** Click the console icon

### Basic Commands

| Command | Description |
|---------|-------------|
| `help` | Show all available commands |
| `clear` | Clear the console output |
| `echo <text>` | Echo text back to console |
| `info` | Show plugin information |
| `settings` | Open plugin settings |
| `stats` | Show console statistics |

### Plugin Management Commands

| Command | Description | Example |
|---------|-------------|---------|
| `plugins` | List all loaded plugins | `plugins` |
| `plugin <name>` | Show plugin details | `plugin Linter` |
| `enable <name>` | Enable a plugin | `enable Notes` |
| `disable <name>` | Disable a plugin | `disable Notes` |
| `reload <name>` | Reload a plugin | `reload Insight` |

### Service Testing Commands

| Command | Description |
|---------|-------------|
| `services` | List all available plugin services |
| `cache` | Show cache statistics |
| `metrics` | Show plugin metrics |
| `test:cache` | Test cache service (put/get/remove) |
| `test:notify [message]` | Test notification service |
| `test:logging` | Test logging service (all levels) |

### System Commands

| Command | Description |
|---------|-------------|
| `memory` | Show detailed memory usage |
| `threads` | Show all running threads with CPU time |
| `gc` | Run garbage collector |
| `system` | Show system information (OS, Java, etc.) |
| `env [filter]` | Show environment variables |
| `props [filter]` | Show system properties |
| `monitor` | Start real-time monitoring |
| `stop` | Stop monitoring |

### Data Store Commands

| Command | Description | Example |
|---------|-------------|---------|
| `save <key> <value>` | Save data to store | `save myKey "Hello World"` |
| `load <key>` | Load data from store | `load myKey` |
| `delete <key>` | Delete data from store | `delete myKey` |
| `list` | List all stored keys | `list` |

---

## 💡 Examples

### Testing Plugin Lifecycle

```bash
> plugins
=== Loaded Plugins ===
  Linter v2.2.6 - ENABLED (Enabled: true)
  Notes v2.0.0 - ENABLED (Enabled: true)
  Insight v3.0.0 - ENABLED (Enabled: true)

> disable Notes
Plugin disabled: Notes

> enable Notes
Plugin enabled: Notes

> reload Linter
Reloading plugin: Linter
Plugin reloaded: Linter
```

### Testing Services

```bash
> test:cache
Stored: test_123456789 = Hello World Wed Jun 03 10:30:00 EDT 2026
Retrieved: Hello World Wed Jun 03 10:30:00 EDT 2026
Removed test key
Cache test completed successfully!

> test:notify Hello from console!
Test notification sent: Hello from console!

> test:logging
Test log messages sent at all levels
```

### System Monitoring

```bash
> memory
=== Memory Usage ===
  Heap Memory: 45.23 MB / 512.00 MB
  Non-Heap: 12.45 MB
  Max memory: 1024.00 MB
  Total memory: 128.00 MB
  Free memory: 82.77 MB
  Used memory: 45.23 MB

> gc
Running garbage collector...
Garbage collector completed
=== Memory Usage ===
  Heap Memory: 32.15 MB / 512.00 MB
  ...
```

### Data Persistence

```bash
> save config:theme dark
Saved: config:theme = dark

> save config:fontSize 14
Saved: config:fontSize = 14

> list
=== Stored Keys ===
  config:theme
  config:fontSize

> load config:theme
config:theme = dark
```

---

## 🎛️ Settings

The plugin provides the following configurable settings:

| Setting | Options | Description |
|---------|---------|-------------|
| UI Position | `bottom` / `tab` | Where the console panel appears in the IDE |

Access settings via:
- **Menu:** `Tools → Developer Console → Settings`
- **Command:** `settings`
- **Toolbar:** Click the settings button (⚙)

---

## 🐛 Troubleshooting

### Console not showing?
- Ensure the plugin is enabled (`plugins` command)
- Check the UI position setting (try switching between `bottom` and `tab`)
- Restart the plugin: `disable Developer Console` then `enable Developer Console`

### Commands not working?
- Check that the required services are available (`services` command)
- Verify the plugin has necessary permissions
- Check the IDE logs for errors

### Data store commands failing?
- Ensure the data directory is writable
- Check disk space availability

---

## 🔧 Development

### Adding Custom Commands

To extend the console with your own commands:

```java
commands.put("mycommand", new Command() {
    @Override
    public void execute(String[] args, PrintStream out) {
        out.println("My custom command executed!");
    }
    
    @Override
    public String getDescription() {
        return "My custom command description";
    }
});
```

### Service Integration

The console integrates with all major plugin services:

- `PluginLoggingService` - Logging
- `PluginCacheService` - Caching
- `PluginNotificationService` - Notifications
- `PluginMetricsService` - Metrics
- `PluginDataStore` - Persistent storage
- `PluginPermissionService` - Permissions
- `PluginHookService` - Hooks
- `PluginAsyncTaskExecutor` - Async tasks

---

## 📝 Version History

### v2.0.0 (Current)
- Added command history navigation (Up/Down arrows)
- Added plugin management (enable/disable/reload)
- Added service testing commands (test:cache, test:notify, test:logging)
- Added system monitoring (monitor/stop/stats)
- Added data store commands (save/load/delete/list)
- Added real-time status bar with memory/threads
- Added progress bar for long operations
- Added command execution timing
- Organized help with categories and emojis
- Added "Show Panel" button in toolbar

### v1.0.0
- Initial release with basic console functionality
- Core commands: help, plugins, services, cache, metrics, echo, clear, info
- Basic system commands: threads, memory

---

## 📄 License

This plugin is part of the IDE platform and is distributed under the same license terms.

---

## 👤 Author

**Jean-Francois Landreville**  
Email: landrevillejf@protonmail.com

---

## 🤝 Support

For issues, feature requests, or contributions:
- Check the IDE logs for error messages
- Use the console's `info` command to verify plugin version
- Use `stats` to see console usage statistics

---

*Made with ❤️ for the IDE plugin ecosystem*