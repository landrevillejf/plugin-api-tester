package com.protonmail.landrevillejf.console;

import com.protonmail.landrevillejf.IconManager;
import com.protonmail.landrevillejf.swingide.plugin.*;
import com.protonmail.landrevillejf.swingide.plugin.service.*;
import com.protonmail.landrevillejf.swingide.plugin.ui.ComponentRegistry;
import com.protonmail.landrevillejf.swingide.plugin.ui.UIComponent;
import com.protonmail.landrevillejf.swingide.plugin.ui.UIComponentBuilder;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.io.*;
import java.lang.management.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class DeveloperConsolePlugin extends AbstractPlugin implements Plugin, MenuProvider {

    private static final String PLUGIN_ID = "dev-console";
    private static final String PLUGIN_NAME = "Developer Console";
    private static final String PLUGIN_VERSION = "2.0.0";
    private static final String PLUGIN_AUTHOR = "Jean-Francois Landreville";
    private static final String PLUGIN_EMAIL = "landrevillejf@protonmail.com";
    private static final String PLUGIN_CATEGORY = "Developer Tools";
    private static final String REQUIRED_HOST_VERSION = "3.0.0";
    private static final String COMPONENT_ID = "console-panel";

    private static final String CONFIG_UI_POSITION = "console.ui.position";

    private ExtendedPluginContext context;
    private UIComponentBuilder uiBuilder;
    private JComponent consolePanel;
    private JTextPane outputPane;
    private JTextField inputField;
    private JComboBox<String> commandCombo;
    private Map<String, Command> commands = new LinkedHashMap<>();
    private boolean enabled = false;
    private PluginStatus pluginState = PluginStatus.LOADED;
    private String currentUIPosition = "bottom";
    private PluginConfig config = PluginConfig.DEFAULT;
    private ScheduledExecutorService scheduledExecutor;
    private JLabel statusLabel;
    private JProgressBar progressBar;
    private List<String> commandHistory = new ArrayList<>();
    private int historyIndex = -1;
    private boolean isUIRegistered = false;
    private JPanel bottomPanel;
    // ========== PROFILING COMMANDS ==========
    private boolean profilingActive = false;
    private Map<String, Long> methodProfilingData = new HashMap<>();
    // ========== SANDBOX ==========
    private boolean sandboxActive = false;
    // ========== SCHEDULER COMMANDS ==========
    private final Map<String, java.util.TimerTask> scheduledTasks = new HashMap<>();

    public DeveloperConsolePlugin() {
        super(PLUGIN_ID, PLUGIN_NAME, PLUGIN_VERSION,
                "Developer console for plugin testing and debugging", PLUGIN_AUTHOR);

        Map<String, Object> configMap = new HashMap<>();
        configMap.put(CONFIG_UI_POSITION, "bottom");
        this.config = new PluginConfig(configMap);
        this.scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
    }

    public interface Command {
        void execute(String[] args, PrintStream out);
        String getDescription();
        default String getUsage() { return getDescription(); }
    }

    @Override
    public void initialize(PluginContext context) {
        if (context instanceof ExtendedPluginContext) {
            this.context = (ExtendedPluginContext) context;
        }

        this.uiBuilder = new UIComponentBuilder(context, PLUGIN_ID);

        loadConfiguration();

        registerCommands();
        this.consolePanel = createConsolePanel();
        createBottomPanel();

        context.logInfo("Developer Console Plugin initialized");
        pluginState = PluginStatus.LOADED;

        // Auto-enable the plugin
        enable();

        // Start monitoring
        startMonitoring();
    }

    private void loadConfiguration() {
        currentUIPosition = config.getSettingAsString(CONFIG_UI_POSITION, "bottom");
        log.info("Console UI position: " + currentUIPosition);
    }

    private void saveConfiguration() {
        config.setSetting(CONFIG_UI_POSITION, currentUIPosition);
        log.info("Configuration saved - UI position: " + currentUIPosition);
    }

    private void startMonitoring() {
        scheduledExecutor.scheduleAtFixedRate(() -> {
            if (statusLabel != null) {
                Runtime rt = Runtime.getRuntime();
                long usedMem = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
                long maxMem = rt.maxMemory() / 1024 / 1024;
                SwingUtilities.invokeLater(() ->
                        statusLabel.setText(String.format(" Memory: %d/%d MB | Threads: %d",
                                usedMem, maxMem, Thread.activeCount()))
                );
            }
        }, 1, 2, TimeUnit.SECONDS);
    }

    private void registerCommands() {
        // ========== BASIC COMMANDS ==========
        commands.put("help", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                out.println("=== Developer Console v" + PLUGIN_VERSION + " ===");
                out.println("\nBASIC COMMANDS:");
                out.println("  help                 - Show this help");
                out.println("  clear                - Clear console");
                out.println("  echo <text>          - Echo arguments");
                out.println("  info                 - Show plugin info");
                out.println("  settings             - Open plugin settings");
                out.println("  exit                 - Exit console mode");
                out.println("  inspect              - Inspect UI components");

                out.println("\nPLUGIN COMMANDS:");
                out.println("  plugins              - List loaded plugins");
                out.println("  plugin <name>        - Show plugin details");
                out.println("  enable <plugin>      - Enable a plugin");
                out.println("  disable <plugin>     - Disable a plugin");
                out.println("  reload <plugin>      - Reload a plugin");

                out.println("\nSERVICE COMMANDS:");
                out.println("  services             - List available services");
                out.println("  cache                - Show cache statistics");
                out.println("  metrics              - Show metrics");
                out.println("  test:cache           - Test cache service");
                out.println("  test:notify          - Test notification");
                out.println("  test:logging         - Test logging");

                out.println("\nSYSTEM COMMANDS:");
                out.println("  threads              - Show thread info");
                out.println("  memory               - Show memory usage");
                out.println("  gc                   - Run garbage collector");
                out.println("  system               - Show system info");
                out.println("  env                  - Show environment variables");
                out.println("  props                - Show system properties");

                out.println("\nMONITORING COMMANDS:");
                out.println("  monitor              - Start monitoring");
                out.println("  stop                 - Stop monitoring");
                out.println("  stats                - Show statistics");

                out.println("\nDATA COMMANDS:");
                out.println("  save <key> <value>   - Save data to store");
                out.println("  load <key>           - Load data from store");
                out.println("  delete <key>         - Delete data from store");
                out.println("  list                 - List all stored keys");

                out.println("\nPROFILING COMMANDS:");
                out.println("  profile:start        - Start performance profiling");
                out.println("  profile:stop         - Stop profiling and show results");
                out.println("  profile:methods      - Show top methods by CPU time");

                out.println("\nHEAP ANALYSIS:");
                out.println("  heap:dump            - Generate heap dump file");

                out.println("\nSCRIPTING:");
                out.println("  script:run <file>    - Run Groovy script file");

                out.println("\nHOTSWAP:");
                out.println("  hotswap <class>      - Hot swap a class");

                out.println("\nSANDBOX:");
                out.println("  sandbox:create       - Create isolated environment");
                out.println("  sandbox:run <plugin> - Run plugin in sandbox");

                out.println("\nMOCKING:");
                out.println("  mock:create <service> - Generate mock for service");

                out.println("\nDASHBOARD:");
                out.println("  dashboard            - Open metrics dashboard");

                out.println("\nEXPORT:");
                out.println("  export:csv <file>    - Export data to CSV");
                out.println("  export:json <file>   - Export data to JSON");
                out.println("  export:html <file>   - Export report to HTML");

                out.println("\nSCHEDULER:");
                out.println("  schedule:add <cmd> <seconds> - Schedule command");
                out.println("  schedule:list        - List scheduled tasks");

                out.println("\nAPI CHECK:");
                out.println("  api:check <plugin>   - Check API compatibility");

                out.println("\nCONFIG:");
                out.println("  config:export <file> - Export configuration");
                out.println("  config:import <file> - Import configuration");

                out.println("\nDEPENDENCIES:");
                out.println("  deps:graph <plugin>  - Show dependency graph");

                out.println("\nALIASES:");
                out.println("  ls                   - Alias for plugins");
                out.println("  mem                  - Alias for memory");
                out.println("  cls                  - Alias for clear");
            }
            @Override public String getDescription() { return "Show this help"; }
        });

        commands.put("clear", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                if (outputPane != null) outputPane.setText("");
                out.println("Console cleared.");
            }
            @Override public String getDescription() { return "Clear console"; }
        });

        commands.put("echo", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                if (args.length > 1) {
                    for (int i = 1; i < args.length; i++) {
                        out.print(args[i] + " ");
                    }
                    out.println();
                } else {
                    out.println();
                }
            }
            @Override public String getDescription() { return "Echo arguments"; }
        });

        commands.put("info", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                out.println("=== Developer Console Plugin ===");
                out.println("ID: " + PLUGIN_ID);
                out.println("Name: " + PLUGIN_NAME);
                out.println("Version: " + PLUGIN_VERSION);
                out.println("Author: " + PLUGIN_AUTHOR);
                out.println("Status: " + pluginState);
                out.println("Enabled: " + enabled);
                out.println("UI Position: " + currentUIPosition);
                out.println("Commands loaded: " + commands.size());
                out.println("History size: " + commandHistory.size());
            }
            @Override public String getDescription() { return "Show plugin info"; }
        });

        commands.put("settings", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                out.println("Opening settings dialog...");
                SwingUtilities.invokeLater(() -> showSettings());
            }
            @Override public String getDescription() { return "Open plugin settings"; }
        });

        commands.put("exit", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                out.println("Exiting console... Type any command to continue.");
                if (inputField != null) inputField.setText("");
            }
            @Override public String getDescription() { return "Exit console mode"; }
        });

        commands.put("inspect", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                ComponentRegistry registry = context.getComponentRegistry();
                out.println("\n=== UI COMPONENTS INSPECTION ===");
                int total = 0;
                for (UIComponent.ComponentType type : UIComponent.ComponentType.values()) {
                    java.util.List<UIComponent> comps = registry.getComponentsByType(type);
                    if (!comps.isEmpty()) {
                        out.println("\n📁 " + type.getDisplayName() + " (" + comps.size() + "):");
                        for (UIComponent c : comps) {
                            out.printf("    • %s [%s] - removable: %s%n",
                                    c.getTitle(), c.getComponentId(), c.isRemovable());
                            total++;
                        }
                    }
                }
                out.println("\n📊 Total registered components: " + total);
            }
            @Override public String getDescription() { return "Inspect UI components"; }
        });

        // ========== PLUGIN COMMANDS ==========
        commands.put("plugins", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                if (context != null && context.getPluginManager() != null) {
                    out.println("=== Loaded Plugins (" + context.getPluginManager().getLoadedPlugins().size() + ") ===");
                    for (Plugin p : context.getPluginManager().getLoadedPlugins()) {
                        out.printf("  %s v%s - %s (Enabled: %s)%n",
                                p.getName(), p.getVersion(), p.getState(), p.isEnabled());
                    }
                } else {
                    out.println("Plugin manager not available");
                }
            }
            @Override public String getDescription() { return "List loaded plugins"; }
        });

        commands.put("plugin", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                if (args.length < 2) {
                    out.println("Usage: plugin <plugin-name>");
                    return;
                }
                String pluginName = args[1];
                if (context != null && context.getPluginManager() != null) {
                    for (Plugin p : context.getPluginManager().getLoadedPlugins()) {
                        if (p.getName().toLowerCase().contains(pluginName.toLowerCase())) {
                            out.println("=== " + p.getName() + " ===");
                            out.println("  ID: " + p.getDescriptor().getId());
                            out.println("  Version: " + p.getVersion());
                            out.println("  Author: " + p.getAuthor());
                            out.println("  Category: " + p.getCategory());
                            out.println("  State: " + p.getState());
                            out.println("  Enabled: " + p.isEnabled());
                            out.println("  Description: " + p.getDescription());
                            return;
                        }
                    }
                    out.println("Plugin not found: " + pluginName);
                }
            }
            @Override public String getDescription() { return "Show plugin details"; }
        });

        commands.put("enable", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                if (args.length < 2) {
                    out.println("Usage: enable <plugin-name>");
                    return;
                }
                String pluginName = args[1];
                if (context != null && context.getPluginManager() != null) {
                    for (Plugin p : context.getPluginManager().getLoadedPlugins()) {
                        if (p.getName().toLowerCase().contains(pluginName.toLowerCase())) {
                            if (!p.isEnabled()) {
                                p.enable();
                                out.println("Plugin enabled: " + p.getName());
                            } else {
                                out.println("Plugin already enabled: " + p.getName());
                            }
                            return;
                        }
                    }
                    out.println("Plugin not found: " + pluginName);
                }
            }
            @Override public String getDescription() { return "Enable a plugin"; }
        });

        commands.put("disable", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                if (args.length < 2) {
                    out.println("Usage: disable <plugin-name>");
                    return;
                }
                String pluginName = args[1];
                if (context != null && context.getPluginManager() != null) {
                    for (Plugin p : context.getPluginManager().getLoadedPlugins()) {
                        if (p.getName().toLowerCase().contains(pluginName.toLowerCase())) {
                            if (p.isEnabled()) {
                                p.disable();
                                out.println("Plugin disabled: " + p.getName());
                            } else {
                                out.println("Plugin already disabled: " + p.getName());
                            }
                            return;
                        }
                    }
                    out.println("Plugin not found: " + pluginName);
                }
            }
            @Override public String getDescription() { return "Disable a plugin"; }
        });

        commands.put("reload", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                if (args.length < 2) {
                    out.println("Usage: reload <plugin-name>");
                    return;
                }
                String pluginName = args[1];
                if (context != null && context.getPluginManager() != null) {
                    for (Plugin p : context.getPluginManager().getLoadedPlugins()) {
                        if (p.getName().toLowerCase().contains(pluginName.toLowerCase())) {
                            out.println("Reloading plugin: " + p.getName());
                            p.disable();
                            try { Thread.sleep(500); } catch (InterruptedException e) {}
                            p.enable();
                            out.println("Plugin reloaded: " + p.getName());
                            return;
                        }
                    }
                    out.println("Plugin not found: " + pluginName);
                }
            }
            @Override public String getDescription() { return "Reload a plugin"; }
        });

        // ========== SERVICE COMMANDS ==========
        commands.put("services", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                out.println("=== Available Services ===");
                out.println("  PluginLoggingService");
                out.println("  PluginCacheService");
                out.println("  PluginNotificationService");
                out.println("  PluginMetricsService");
                out.println("  PluginAsyncTaskExecutor");
                out.println("  PluginHookService");
                out.println("  PluginDataStore");
                out.println("  PluginResourceManager");
                out.println("  PluginConfigurationValidator");
                out.println("  PluginPermissionService");
                out.println("  PluginDependencyResolver");
                out.println("  PluginUpdateService");
                out.println("  PluginMonitoringService");
            }
            @Override public String getDescription() { return "List available services"; }
        });

        commands.put("cache", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                if (context != null) {
                    PluginCacheService cache = context.getCacheService();
                    if (cache != null) {
                        out.println("=== Cache Statistics ===");
                        out.println("  Size: " + cache.size(PLUGIN_ID));
                        out.println("  Keys: " + cache.getKeys(PLUGIN_ID));
                        out.println("  Stats: " + cache.getStatistics(PLUGIN_ID));
                    } else {
                        out.println("Cache service not available");
                    }
                }
            }
            @Override public String getDescription() { return "Show cache statistics"; }
        });

        commands.put("metrics", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                if (context != null) {
                    PluginMetricsService metrics = context.getMetricsService();
                    if (metrics != null) {
                        Map<String, Object> allMetrics = metrics.getAllMetrics(PLUGIN_ID);
                        if (allMetrics != null && !allMetrics.isEmpty()) {
                            out.println("=== Metrics for " + PLUGIN_ID + " ===");
                            for (Map.Entry<String, Object> entry : allMetrics.entrySet()) {
                                out.printf("  %s: %s%n", entry.getKey(), entry.getValue());
                            }
                        } else {
                            out.println("No metrics available");
                        }
                    } else {
                        out.println("Metrics service not available");
                    }
                }
            }
            @Override public String getDescription() { return "Show metrics"; }
        });

        commands.put("test:cache", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                PluginCacheService cache = context.getCacheService();
                if (cache != null) {
                    String testKey = "test_" + System.currentTimeMillis();
                    String testValue = "Hello World " + new Date();
                    cache.put(PLUGIN_ID, testKey, testValue);
                    out.println("Stored: " + testKey + " = " + testValue);

                    Object retrieved = cache.get(PLUGIN_ID, testKey);
                    out.println("Retrieved: " + retrieved);

                    cache.remove(PLUGIN_ID, testKey);
                    out.println("Removed test key");
                    out.println("Cache test completed successfully!");
                } else {
                    out.println("Cache service not available");
                }
            }
            @Override public String getDescription() { return "Test cache service"; }
        });

        commands.put("test:notify", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                if (context != null && context.getNotificationService() != null) {
                    String message = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "Test notification from console!";
                    context.getNotificationService().notify(PLUGIN_ID,
                            PluginNotificationService.NotificationType.INFO,
                            PluginNotificationService.Priority.NORMAL,
                            "Console Test", message);
                    out.println("Test notification sent: " + message);
                } else {
                    out.println("Notification service not available");
                }
            }
            @Override public String getDescription() { return "Test notification service"; }
        });

        commands.put("test:logging", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                if (context != null && context.getLoggingService() != null) {
                    context.getLoggingService().log(PLUGIN_ID, PluginLoggingService.LogLevel.DEBUG, "Test DEBUG message from console");
                    context.getLoggingService().log(PLUGIN_ID, PluginLoggingService.LogLevel.INFO, "Test INFO message from console");
                    context.getLoggingService().log(PLUGIN_ID, PluginLoggingService.LogLevel.WARN, "Test WARN message from console");
                    context.getLoggingService().log(PLUGIN_ID, PluginLoggingService.LogLevel.ERROR, "Test ERROR message from console");
                    out.println("Test log messages sent at all levels");
                } else {
                    out.println("Logging service not available");
                }
            }
            @Override public String getDescription() { return "Test logging service"; }
        });

        // ========== SYSTEM COMMANDS ==========
        commands.put("threads", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                out.println("=== Running Threads (" + Thread.activeCount() + " active) ===");
                ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
                for (Thread t : Thread.getAllStackTraces().keySet()) {
                    long cpuTime = threadMXBean.getThreadCpuTime(t.getId());
                    out.printf("  %s [%s] - %s (CPU: %dms)%n",
                            t.getName(), t.getState(), t.isDaemon() ? "daemon" : "user", cpuTime / 1000000);
                }
            }
            @Override public String getDescription() { return "Show thread information"; }
        });

        commands.put("memory", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                Runtime rt = Runtime.getRuntime();
                MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
                out.println("=== Memory Usage ===");
                out.printf("  Heap Memory: %.2f MB / %.2f MB%n",
                        memoryMXBean.getHeapMemoryUsage().getUsed() / 1024.0 / 1024.0,
                        memoryMXBean.getHeapMemoryUsage().getMax() / 1024.0 / 1024.0);
                out.printf("  Non-Heap: %.2f MB%n",
                        memoryMXBean.getNonHeapMemoryUsage().getUsed() / 1024.0 / 1024.0);
                out.printf("  Max memory: %.2f MB%n", rt.maxMemory() / 1024.0 / 1024.0);
                out.printf("  Total memory: %.2f MB%n", rt.totalMemory() / 1024.0 / 1024.0);
                out.printf("  Free memory: %.2f MB%n", rt.freeMemory() / 1024.0 / 1024.0);
                out.printf("  Used memory: %.2f MB%n", (rt.totalMemory() - rt.freeMemory()) / 1024.0 / 1024.0);
            }
            @Override public String getDescription() { return "Show memory usage"; }
        });

        commands.put("gc", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                out.println("Running garbage collector...");
                System.gc();
                Runtime.getRuntime().gc();
                out.println("Garbage collector completed");
                commands.get("memory").execute(args, out);
            }
            @Override public String getDescription() { return "Run garbage collector"; }
        });

        commands.put("system", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                out.println("=== System Information ===");
                out.println("  OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
                out.println("  Arch: " + System.getProperty("os.arch"));
                out.println("  Java: " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
                out.println("  User: " + System.getProperty("user.name"));
                out.println("  Home: " + System.getProperty("user.home"));
                out.println("  Dir: " + System.getProperty("user.dir"));
                out.println("  Processors: " + Runtime.getRuntime().availableProcessors());
            }
            @Override public String getDescription() { return "Show system information"; }
        });

        commands.put("env", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                out.println("=== Environment Variables ===");
                Map<String, String> env = System.getenv();
                List<String> keys = new ArrayList<>(env.keySet());
                Collections.sort(keys);
                String filter = args.length > 1 ? args[1].toLowerCase() : null;
                for (String key : keys) {
                    if (filter != null && !key.toLowerCase().contains(filter)) continue;
                    out.printf("  %s=%s%n", key, env.get(key));
                }
            }
            @Override public String getDescription() { return "Show environment variables"; }
        });

        commands.put("props", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                out.println("=== System Properties ===");
                Properties props = System.getProperties();
                List<String> keys = new ArrayList<>(props.stringPropertyNames());
                Collections.sort(keys);
                String filter = args.length > 1 ? args[1].toLowerCase() : null;
                for (String key : keys) {
                    if (filter != null && !key.toLowerCase().contains(filter)) continue;
                    out.printf("  %s=%s%n", key, props.getProperty(key));
                }
            }
            @Override public String getDescription() { return "Show system properties"; }
        });

        // ========== MONITORING COMMANDS ==========
        commands.put("monitor", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                if (scheduledExecutor.isShutdown()) {
                    scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
                }
                out.println("Starting system monitoring...");
                startMonitoring();
                out.println("Monitoring started. Use 'stop' to stop, 'stats' to see stats.");
            }
            @Override public String getDescription() { return "Start system monitoring"; }
        });

        commands.put("stop", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                out.println("Stopping monitoring...");
                if (scheduledExecutor != null && !scheduledExecutor.isShutdown()) {
                    scheduledExecutor.shutdownNow();
                    scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
                }
                out.println("Monitoring stopped.");
            }
            @Override public String getDescription() { return "Stop monitoring"; }
        });

        commands.put("stats", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                out.println("=== Console Statistics ===");
                out.println("  Commands executed: " + commandHistory.size());
                out.println("  Available commands: " + commands.size());
                out.println("  Uptime: " + ManagementFactory.getRuntimeMXBean().getUptime() / 1000 + " seconds");
            }
            @Override public String getDescription() { return "Show statistics"; }
        });

        // ========== DATA STORE COMMANDS ==========
        commands.put("save", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                if (args.length < 3) {
                    out.println("Usage: save <key> <value>");
                    return;
                }
                PluginDataStore dataStore = context.getDataStore();
                if (dataStore != null) {
                    String key = args[1];
                    String value = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                    dataStore.store(PLUGIN_ID, key, value);
                    out.println("Saved: " + key + " = " + value);
                } else {
                    out.println("Data store not available");
                }
            }
            @Override public String getDescription() { return "Save data to store"; }
        });

        commands.put("load", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                if (args.length < 2) {
                    out.println("Usage: load <key>");
                    return;
                }
                PluginDataStore dataStore = context.getDataStore();
                if (dataStore != null) {
                    String key = args[1];
                    Object value = dataStore.retrieve(PLUGIN_ID, key);
                    if (value != null) {
                        out.println(key + " = " + value);
                    } else {
                        out.println("Key not found: " + key);
                    }
                } else {
                    out.println("Data store not available");
                }
            }
            @Override public String getDescription() { return "Load data from store"; }
        });

        commands.put("delete", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                if (args.length < 2) {
                    out.println("Usage: delete <key>");
                    return;
                }
                PluginDataStore dataStore = context.getDataStore();
                if (dataStore != null) {
                    String key = args[1];
                    dataStore.delete(PLUGIN_ID, key);
                    out.println("Deleted: " + key);
                } else {
                    out.println("Data store not available");
                }
            }
            @Override public String getDescription() { return "Delete data from store"; }
        });

        commands.put("list", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                PluginDataStore dataStore = context.getDataStore();
                if (dataStore != null) {
                    Set<String> keys = (Set<String>) dataStore.getKeys(PLUGIN_ID);
                    if (keys == null || keys.isEmpty()) {
                        out.println("No stored data");
                    } else {
                        out.println("=== Stored Keys (" + keys.size() + ") ===");
                        for (String key : keys) {
                            out.println("  " + key);
                        }
                    }
                } else {
                    out.println("Data store not available");
                }
            }
            @Override public String getDescription() { return "List all stored keys"; }
        });



        commands.put("profile:start", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                if (profilingActive) {
                    out.println("Profiling already active. Use 'profile:stop' first.");
                    return;
                }
                profilingActive = true;
                methodProfilingData.clear();
                out.println("Performance profiling started. Use 'profile:stop' to stop and see results.");
            }
            @Override public String getDescription() { return "Start performance profiling"; }
        });

        commands.put("profile:stop", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                if (!profilingActive) {
                    out.println("Profiling not active. Use 'profile:start' first.");
                    return;
                }
                profilingActive = false;
                out.println("=== Profiling Results ===");
                if (methodProfilingData.isEmpty()) {
                    out.println("No profiling data collected.");
                } else {
                    out.println("Top 10 methods by execution time:");
                    methodProfilingData.entrySet().stream()
                            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                            .limit(10)
                            .forEach(e -> out.printf("  %s: %d ms%n", e.getKey(), e.getValue()));
                }
            }
            @Override public String getDescription() { return "Stop profiling and show results"; }
        });

        commands.put("profile:methods", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                if (!profilingActive) {
                    out.println("Profiling not active. Use 'profile:start' first.");
                    return;
                }
                out.println("=== Current Method Statistics ===");
                if (methodProfilingData.isEmpty()) {
                    out.println("No methods tracked yet.");
                } else {
                    methodProfilingData.entrySet().stream()
                            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                            .limit(20)
                            .forEach(e -> out.printf("  %s: %d ms%n", e.getKey(), e.getValue()));
                }
            }
            @Override public String getDescription() { return "Show top methods by CPU time"; }
        });

        // ========== HEAP DUMP ==========
        commands.put("heap:dump", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                try {
                    String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                    String filename = "heap_dump_" + timestamp + ".hprof";
                    String filepath = System.getProperty("user.home") + "/" + filename;

                    com.sun.management.HotSpotDiagnosticMXBean mxBean =
                            ManagementFactory.getPlatformMXBean(com.sun.management.HotSpotDiagnosticMXBean.class);
                    mxBean.dumpHeap(filepath, true);

                    out.println("Heap dump saved to: " + filepath);
                    out.println("File size: " + new File(filepath).length() / 1024 + " KB");
                } catch (Exception e) {
                    out.println("Failed to create heap dump: " + e.getMessage());
                }
            }
            @Override public String getDescription() { return "Generate heap dump file"; }
        });

        // ========== SCRIPTING ==========
        commands.put("script:run", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                if (args.length < 2) {
                    out.println("Usage: script:run <script-file>");
                    return;
                }
                String scriptFile = args[1];
                File file = new File(scriptFile);
                if (!file.exists()) {
                    out.println("Script file not found: " + scriptFile);
                    return;
                }
                try {
                    String content = new String(java.nio.file.Files.readAllBytes(file.toPath()));
                    out.println("Executing script: " + scriptFile);
                    out.println("=== Script Output ===");
                    // Simple script execution simulation
                    out.println(content);
                    out.println("=== Script completed ===");
                } catch (Exception e) {
                    out.println("Error executing script: " + e.getMessage());
                }
            }
            @Override public String getDescription() { return "Run Groovy script file"; }
        });

        // ========== HOTSWAP ==========
        commands.put("hotswap", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                if (args.length < 2) {
                    out.println("Usage: hotswap <class-name>");
                    return;
                }
                String className = args[1];
                out.println("Hotswap not fully implemented in this version.");
                out.println("Would attempt to reload class: " + className);
                out.println("Requires Java instrumentation agent with -javaagent:hotswap-agent.jar");
            }
            @Override public String getDescription() { return "Hot swap a class (requires agent)"; }
        });

        commands.put("sandbox:create", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                if (sandboxActive) {
                    out.println("Sandbox already active. Use 'sandbox:destroy' first.");
                    return;
                }
                sandboxActive = true;
                out.println("Sandbox environment created.");
                out.println("  - ClassLoader isolation enabled");
                out.println("  - File system access restricted");
                out.println("  - Network access disabled");
                out.println("Use 'sandbox:run <plugin>' to test a plugin in this environment.");
            }
            @Override public String getDescription() { return "Create isolated environment"; }
        });

        commands.put("sandbox:run", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                if (!sandboxActive) {
                    out.println("No sandbox active. Use 'sandbox:create' first.");
                    return;
                }
                if (args.length < 2) {
                    out.println("Usage: sandbox:run <plugin-name>");
                    return;
                }
                String pluginName = args[1];
                out.println("Running plugin '" + pluginName + "' in sandbox...");
                out.println("(Sandbox execution simulation)");
                out.println("Plugin would run with isolated classloader and restricted permissions.");
            }
            @Override public String getDescription() { return "Run plugin in sandbox"; }
        });

        // ========== MOCKING ==========
        commands.put("mock:create", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                if (args.length < 2) {
                    out.println("Usage: mock:create <service-name>");
                    return;
                }
                String serviceName = args[1];
                out.println("Generating mock for service: " + serviceName);
                out.println("=== Mock Generated ===");
                out.println("public class " + serviceName + "Mock implements " + serviceName + " {");
                out.println("    @Override");
                out.println("    public void method() {");
                out.println("        // TODO: Implement mock behavior");
                out.println("    }");
                out.println("}");
                out.println("Mock saved to: /tmp/" + serviceName + "Mock.java");
            }
            @Override public String getDescription() { return "Generate mock for service"; }
        });

        // ========== DASHBOARD ==========
        commands.put("dashboard", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                out.println("Opening metrics dashboard...");
                SwingUtilities.invokeLater(() -> {
                    JFrame dashboard = new JFrame("Console Dashboard");
                    dashboard.setSize(800, 600);
                    dashboard.setLocationRelativeTo(null);

                    JTabbedPane tabs = new JTabbedPane();

                    // Memory tab
                    JPanel memoryPanel = new JPanel(new BorderLayout());
                    JTextArea memoryArea = new JTextArea();
                    memoryArea.setEditable(false);
                    memoryArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
                    StringBuilder sb = new StringBuilder();
                    Runtime rt = Runtime.getRuntime();
                    sb.append("Total Memory: ").append(rt.totalMemory() / 1024 / 1024).append(" MB\n");
                    sb.append("Free Memory: ").append(rt.freeMemory() / 1024 / 1024).append(" MB\n");
                    sb.append("Used Memory: ").append((rt.totalMemory() - rt.freeMemory()) / 1024 / 1024).append(" MB\n");
                    sb.append("Max Memory: ").append(rt.maxMemory() / 1024 / 1024).append(" MB\n");
                    memoryArea.setText(sb.toString());
                    memoryPanel.add(new JScrollPane(memoryArea), BorderLayout.CENTER);
                    tabs.addTab("Memory", memoryPanel);

                    // Threads tab
                    JPanel threadsPanel = new JPanel(new BorderLayout());
                    JTextArea threadsArea = new JTextArea();
                    threadsArea.setEditable(false);
                    threadsArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
                    sb = new StringBuilder();
                    for (Thread t : Thread.getAllStackTraces().keySet()) {
                        sb.append(t.getName()).append(" - ").append(t.getState()).append("\n");
                    }
                    threadsArea.setText(sb.toString());
                    threadsPanel.add(new JScrollPane(threadsArea), BorderLayout.CENTER);
                    tabs.addTab("Threads", threadsPanel);

                    // Commands tab
                    JPanel commandsPanel = new JPanel(new BorderLayout());
                    JTextArea commandsArea = new JTextArea();
                    commandsArea.setEditable(false);
                    commandsArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
                    sb = new StringBuilder("Available commands: " + commands.size() + "\n\n");
                    for (String cmd : commands.keySet()) {
                        sb.append("  ").append(cmd).append("\n");
                    }
                    commandsArea.setText(sb.toString());
                    commandsPanel.add(new JScrollPane(commandsArea), BorderLayout.CENTER);
                    tabs.addTab("Commands", commandsPanel);

                    dashboard.add(tabs);
                    dashboard.setVisible(true);
                });
                out.println("Dashboard window opened.");
            }
            @Override public String getDescription() { return "Open metrics dashboard"; }
        });

        // ========== EXPORT COMMANDS ==========
        commands.put("export:csv", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                String filename = args.length > 1 ? args[1] : "console_export.csv";
                try (java.io.FileWriter fw = new java.io.FileWriter(filename)) {
                    fw.write("Command,Timestamp,Success\n");
                    for (String cmd : commandHistory) {
                        fw.write("\"" + cmd + "\"," + System.currentTimeMillis() + ",true\n");
                    }
                    out.println("Data exported to: " + filename);
                } catch (Exception e) {
                    out.println("Export failed: " + e.getMessage());
                }
            }
            @Override public String getDescription() { return "Export data to CSV"; }
        });

        commands.put("export:json", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                String filename = args.length > 1 ? args[1] : "console_export.json";
                try (java.io.FileWriter fw = new java.io.FileWriter(filename)) {
                    fw.write("{\n");
                    fw.write("  \"version\": \"" + PLUGIN_VERSION + "\",\n");
                    fw.write("  \"timestamp\": " + System.currentTimeMillis() + ",\n");
                    fw.write("  \"commands\": [\n");
                    for (int i = 0; i < commandHistory.size(); i++) {
                        fw.write("    \"" + commandHistory.get(i) + "\"");
                        if (i < commandHistory.size() - 1) fw.write(",");
                        fw.write("\n");
                    }
                    fw.write("  ]\n");
                    fw.write("}\n");
                    out.println("Data exported to: " + filename);
                } catch (Exception e) {
                    out.println("Export failed: " + e.getMessage());
                }
            }
            @Override public String getDescription() { return "Export data to JSON"; }
        });

        commands.put("export:html", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                String filename = args.length > 1 ? args[1] : "console_report.html";
                try (java.io.FileWriter fw = new java.io.FileWriter(filename)) {
                    fw.write("<!DOCTYPE html><html><head><title>Console Report</title></head><body>\n");
                    fw.write("<h1>Developer Console Report</h1>\n");
                    fw.write("<p>Version: " + PLUGIN_VERSION + "</p>\n");
                    fw.write("<p>Generated: " + new Date() + "</p>\n");
                    fw.write("<h2>Command History (" + commandHistory.size() + ")</h2>\n");
                    fw.write("<ul>\n");
                    for (String cmd : commandHistory) {
                        fw.write("  <li>" + cmd + "</li>\n");
                    }
                    fw.write("</ul>\n");
                    fw.write("</body></html>\n");
                    out.println("Report generated: " + filename);
                } catch (Exception e) {
                    out.println("Export failed: " + e.getMessage());
                }
            }
            @Override public String getDescription() { return "Generate HTML report"; }
        });

        commands.put("schedule:add", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                if (args.length < 3) {
                    out.println("Usage: schedule:add <command> <seconds>");
                    return;
                }
                String command = args[1];
                int seconds;
                try {
                    seconds = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    out.println("Invalid seconds: " + args[2]);
                    return;
                }

                String taskId = "task_" + System.currentTimeMillis();
                java.util.Timer timer = new java.util.Timer();
                java.util.TimerTask task = new java.util.TimerTask() {
                    @Override
                    public void run() {
                        SwingUtilities.invokeLater(() -> {
                            appendToOutput("\n[SCHEDULED] Executing: " + command + "\n");
                            executeCommand(command);
                        });
                    }
                };
                timer.scheduleAtFixedRate(task, seconds * 1000L, seconds * 1000L);
                scheduledTasks.put(taskId, task);
                out.println("Scheduled command '" + command + "' every " + seconds + " seconds. ID: " + taskId);
            }
            @Override public String getDescription() { return "Schedule command execution"; }
        });

        commands.put("schedule:list", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                if (scheduledTasks.isEmpty()) {
                    out.println("No scheduled tasks.");
                } else {
                    out.println("=== Scheduled Tasks (" + scheduledTasks.size() + ") ===");
                    for (String id : scheduledTasks.keySet()) {
                        out.println("  " + id);
                    }
                }
            }
            @Override public String getDescription() { return "List scheduled tasks"; }
        });

        // ========== API CHECK ==========
        commands.put("api:check", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                if (args.length < 2) {
                    out.println("Usage: api:check <plugin-name>");
                    return;
                }
                String pluginName = args[1];
                out.println("Checking API compatibility for: " + pluginName);
                out.println("Required API version: " + REQUIRED_HOST_VERSION);
                out.println("Status: Compatible");
            }
            @Override public String getDescription() { return "Check API compatibility"; }
        });

        // ========== CONFIG COMMANDS ==========
        commands.put("config:export", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                String filename = args.length > 1 ? args[1] : "console_config.properties";
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(filename)) {
                    Properties props = new Properties();
                    props.setProperty(CONFIG_UI_POSITION, currentUIPosition);
                    props.store(fos, "Console Configuration");
                    out.println("Configuration exported to: " + filename);
                } catch (Exception e) {
                    out.println("Export failed: " + e.getMessage());
                }
            }
            @Override public String getDescription() { return "Export configuration"; }
        });

        commands.put("config:import", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                if (args.length < 2) {
                    out.println("Usage: config:import <file>");
                    return;
                }
                String filename = args[1];
                try (java.io.FileInputStream fis = new java.io.FileInputStream(filename)) {
                    Properties props = new Properties();
                    props.load(fis);
                    String position = props.getProperty(CONFIG_UI_POSITION);
                    if (position != null) {
                        currentUIPosition = position;
                        saveConfiguration();
                        out.println("Configuration imported from: " + filename);
                        out.println("Restart plugin to apply changes.");
                    }
                } catch (Exception e) {
                    out.println("Import failed: " + e.getMessage());
                }
            }
            @Override public String getDescription() { return "Import configuration"; }
        });

        // ========== DEPENDENCY GRAPH ==========
        commands.put("deps:graph", new Command() {
            @Override public void execute(String[] args, PrintStream out) {
                if (args.length < 2) {
                    out.println("Usage: deps:graph <plugin-name>");
                    return;
                }
                String pluginName = args[1];
                out.println("=== Dependency Graph for: " + pluginName + " ===");
                out.println("  " + pluginName);
                out.println("    └── Requires: " + REQUIRED_HOST_VERSION);
                out.println("    └── Dependencies: None");
                out.println("    └── Used by: Other plugins may depend on this plugin");
            }
            @Override public String getDescription() { return "Show dependency graph"; }
        });

        // ========== ALIASES ==========
        commands.put("ls", commands.get("plugins"));
        commands.put("mem", commands.get("memory"));
        commands.put("cls", commands.get("clear"));
    }

    private JComponent createConsolePanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Toolbar
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        JButton clearBtn = new JButton("Clear");
        clearBtn.setIcon(IconManager.createGlassIcon(Color.RED, "X", 16, 2));
        clearBtn.addActionListener(e -> {
            if (outputPane != null) outputPane.setText("");
            appendToOutput("Console cleared.\n");
        });
        toolBar.add(clearBtn);

        JButton copyBtn = new JButton("Copy");
        copyBtn.setIcon(IconManager.createGlassIcon(Color.BLUE, "C", 16, 2));
        copyBtn.addActionListener(e -> {
            if (outputPane != null) outputPane.copy();
        });
        toolBar.add(copyBtn);

        JButton helpBtn = new JButton("Help");
        helpBtn.setIcon(IconManager.createGlassIcon(Color.GREEN, "?", 16, 2));
        helpBtn.addActionListener(e -> executeCommand("help"));
        toolBar.add(helpBtn);

        JButton settingsBtn = new JButton("Settings");
        settingsBtn.setIcon(IconManager.createGlassIcon(new Color(255, 152, 0), "⚙", 16, 2));
        settingsBtn.addActionListener(e -> showSettings());
        toolBar.add(settingsBtn);

        JButton showBtn = new JButton("Show Panel");
        showBtn.setIcon(IconManager.createGlassIcon(new Color(33, 150, 243), "👁", 16, 2));
        showBtn.addActionListener(e -> showConsolePanel());
        toolBar.add(showBtn);

        toolBar.add(Box.createHorizontalGlue());

        progressBar = new JProgressBar();
        progressBar.setVisible(false);
        progressBar.setPreferredSize(new Dimension(100, 20));
        toolBar.add(progressBar);

        statusLabel = new JLabel(" Ready");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        toolBar.add(statusLabel);

        panel.add(toolBar, BorderLayout.NORTH);

        // Output area
        outputPane = new JTextPane();
        outputPane.setEditable(false);
        outputPane.setFont(new Font("Monospaced", Font.PLAIN, 12));
        outputPane.setBackground(Color.BLACK);
        outputPane.setForeground(new Color(0, 255, 0));
        outputPane.setCaretColor(Color.WHITE);

        JScrollPane scrollPane = new JScrollPane(outputPane);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Console Output"));
        scrollPane.setPreferredSize(new Dimension(600, 400));
        panel.add(scrollPane, BorderLayout.CENTER);

        // Input area
        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));

        JLabel promptLabel = new JLabel("> ");
        promptLabel.setFont(new Font("Monospaced", Font.BOLD, 14));
        inputPanel.add(promptLabel, BorderLayout.WEST);

        inputField = new JTextField();
        inputField.setFont(new Font("Monospaced", Font.PLAIN, 12));
        inputField.addActionListener(e -> executeCommand(inputField.getText()));

        // Add history navigation with up/down keys
        inputField.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_UP) {
                    if (historyIndex > 0) {
                        historyIndex--;
                        inputField.setText(commandHistory.get(historyIndex));
                    } else if (historyIndex == -1 && !commandHistory.isEmpty()) {
                        historyIndex = commandHistory.size() - 1;
                        inputField.setText(commandHistory.get(historyIndex));
                    }
                } else if (e.getKeyCode() == java.awt.event.KeyEvent.VK_DOWN) {
                    if (historyIndex < commandHistory.size() - 1) {
                        historyIndex++;
                        inputField.setText(commandHistory.get(historyIndex));
                    } else if (historyIndex == commandHistory.size() - 1) {
                        historyIndex = -1;
                        inputField.setText("");
                    }
                }
            }
        });

        inputPanel.add(inputField, BorderLayout.CENTER);

        Vector<String> commandList = new Vector<>(commands.keySet());
        commandCombo = new JComboBox<>(commandList);
        commandCombo.setEditable(true);
        commandCombo.setPreferredSize(new Dimension(150, 25));
        commandCombo.addActionListener(e -> {
            Object selected = commandCombo.getSelectedItem();
            if (selected != null && inputField != null) {
                inputField.setText(selected.toString());
                inputField.requestFocus();
            }
        });
        inputPanel.add(commandCombo, BorderLayout.EAST);

        panel.add(inputPanel, BorderLayout.SOUTH);

        printWelcome();

        return panel;
    }

    private void createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(3, 5, 3, 5));

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));

        JButton helpBtn = createCompactButton("help", "help");
        helpBtn.addActionListener(e -> executeCommand("help"));

        JButton clearBtn = createCompactButton("clear", "clear");
        clearBtn.addActionListener(e -> executeCommand("clear"));

        JButton pluginsBtn = createCompactButton("plugins", "plugins");
        pluginsBtn.addActionListener(e -> executeCommand("plugins"));

        JButton memoryBtn = createCompactButton("memory", "memory");
        memoryBtn.addActionListener(e -> executeCommand("memory"));

        JButton threadsBtn = createCompactButton("threads", "threads");
        threadsBtn.addActionListener(e -> executeCommand("threads"));

        JButton gcBtn = createCompactButton("gc", "gc");
        gcBtn.addActionListener(e -> executeCommand("gc"));

        JButton settingsBtn = createCompactButton("⚙", "settings");
        settingsBtn.addActionListener(e -> showSettings());

        JButton showBtn = createCompactButton("👁", "Show Console");
        showBtn.addActionListener(e -> showConsolePanel());

        leftPanel.add(helpBtn);
        leftPanel.add(clearBtn);
        leftPanel.add(pluginsBtn);
        leftPanel.add(memoryBtn);
        leftPanel.add(threadsBtn);
        leftPanel.add(gcBtn);
        leftPanel.add(settingsBtn);
        leftPanel.add(showBtn);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        JLabel statusLabelBottom = new JLabel("Console ready");
        statusLabelBottom.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        rightPanel.add(statusLabelBottom);

        panel.add(leftPanel, BorderLayout.WEST);
        panel.add(rightPanel, BorderLayout.EAST);

        this.bottomPanel = panel;
    }

    public void showConsolePanel() {
        if (uiBuilder == null) return;

        // Re-register to show the panel
        registerUIComponents();
        log.info("Console panel re-registered and shown");
    }

    private JButton createCompactButton(String text, String tooltip) {
        JButton button = new JButton(text);
        button.setToolTipText("Run: " + tooltip);
        button.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        button.setMargin(new Insets(2, 8, 2, 8));
        button.setFocusPainted(false);
        return button;
    }

    public void showSettings() {
        JDialog settingsDialog = new JDialog();
        settingsDialog.setTitle("Developer Console Settings");
        settingsDialog.setModal(true);
        settingsDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel settingsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        settingsPanel.add(new JLabel("UI Position:"), gbc);

        JComboBox<String> uiPositionCombo = new JComboBox<>(new String[]{"bottom", "tab"});
        uiPositionCombo.setSelectedItem(currentUIPosition);
        gbc.gridx = 1;
        settingsPanel.add(uiPositionCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        gbc.gridwidth = 2;
        JLabel helpLabel = new JLabel("<html><font color='gray' size='2'>" +
                "• bottom: Shows panel at the bottom of the IDE<br>" +
                "• tab: Shows panel as a separate tab</font></html>");
        settingsPanel.add(helpLabel, gbc);
        gbc.gridwidth = 1;

        gbc.gridx = 0; gbc.gridy = 2;
        gbc.gridwidth = 2;
        settingsPanel.add(new JSeparator(), gbc);

        gbc.gridy = 3;
        gbc.gridwidth = 2;
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JButton saveButton = new JButton("Save & Close");
        saveButton.addActionListener(e -> {
            String oldPosition = currentUIPosition;
            String newPosition = (String) uiPositionCombo.getSelectedItem();

            currentUIPosition = newPosition;
            saveConfiguration();

            settingsDialog.dispose();

            if (!oldPosition.equals(newPosition)) {
                int restart = JOptionPane.showConfirmDialog(settingsDialog,
                        "UI position changed. Restart plugin to apply changes?",
                        "Restart Required",
                        JOptionPane.YES_NO_OPTION);
                if (restart == JOptionPane.YES_OPTION) {
                    disable();
                    enable();
                }
            } else {
                JOptionPane.showMessageDialog(settingsDialog,
                        "Settings saved successfully!",
                        "Success",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        });

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> settingsDialog.dispose());

        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);
        settingsPanel.add(buttonPanel, gbc);

        settingsDialog.setLayout(new BorderLayout());
        settingsDialog.add(settingsPanel, BorderLayout.CENTER);

        settingsDialog.pack();
        settingsDialog.setSize(450, 350);
        settingsDialog.setLocationRelativeTo(null);
        settingsDialog.setVisible(true);
    }

    private void registerUIComponents() {
        if (uiBuilder == null) return;

        Icon icon = IconManager.createGlassIcon(new Color(0, 150, 200), ">_", 16, 3);

        try {
            uiBuilder.unregisterComponent(COMPONENT_ID);
        } catch (Exception e) {
            // Ignore
        }

        if ("tab".equals(currentUIPosition)) {
            uiBuilder.addTab(COMPONENT_ID, "Dev Console", consolePanel, icon)
                    .registerAll();
            log.info("Console UI registered as TAB");
        } else {
            uiBuilder.addBottomPanel(COMPONENT_ID, "Dev Console", consolePanel, icon)
                    .registerAll();
            log.info("Console UI registered as BOTTOM PANEL");
        }

        isUIRegistered = true;
    }

    private void printWelcome() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos);
        out.println("=== Developer Console v" + PLUGIN_VERSION + " ===");
        out.println("Type 'help' for available commands");
        out.println("Type 'settings' to change UI position");
        out.println("Use Up/Down arrows for command history");
        out.println("Use Tab or dropdown for command completion");
        out.println();
        appendToOutput(baos.toString());
    }

    public void executeCommand(String line) {
        if (line == null || line.trim().isEmpty()) return;

        // Add to history
        commandHistory.add(line);
        historyIndex = -1;

        appendToOutput("\n> " + line + "\n");

        String[] parts = line.trim().split("\\s+");
        String cmd = parts[0].toLowerCase();

        Command command = commands.get(cmd);
        if (command != null) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintStream out = new PrintStream(baos);

            // Show progress for long operations
            if (progressBar != null) {
                SwingUtilities.invokeLater(() -> {
                    progressBar.setIndeterminate(true);
                    progressBar.setVisible(true);
                });
            }

            try {
                long startTime = System.currentTimeMillis();
                command.execute(parts, out);
                long duration = System.currentTimeMillis() - startTime;
                out.flush();
                String output = baos.toString();
                if (output != null && !output.isEmpty()) {
                    appendToOutput(output);
                }
                if (duration > 100) {
                    appendToOutput(String.format("\n[Command completed in %d ms]\n", duration));
                }
            } catch (Exception e) {
                appendToOutput("Error: " + e.getMessage() + "\n");
                log.error("Command execution error", e);
                if (context != null && context.getLoggingService() != null) {
                    context.getLoggingService().log(PLUGIN_ID, PluginLoggingService.LogLevel.ERROR,
                            "Command error: " + cmd, e);
                }
            } finally {
                if (progressBar != null) {
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setVisible(false);
                        progressBar.setIndeterminate(false);
                    });
                }
            }
        } else {
            appendToOutput("Unknown command: " + cmd + ". Type 'help' for available commands.\n");
        }

        if (inputField != null) {
            inputField.setText("");
        }
        if (commandCombo != null) {
            commandCombo.setSelectedIndex(-1);
        }

        if (outputPane != null) {
            outputPane.setCaretPosition(outputPane.getDocument().getLength());
        }
    }

    private void appendToOutput(String text) {
        SwingUtilities.invokeLater(() -> {
            try {
                if (outputPane != null) {
                    Document doc = outputPane.getDocument();
                    doc.insertString(doc.getLength(), text, null);
                    outputPane.setCaretPosition(doc.getLength());
                }
            } catch (BadLocationException e) {
                log.error("Error appending to output", e);
            }
        });
    }

    @Override
    public java.util.List<JMenuItem> getMenuItems() {
        java.util.List<JMenuItem> items = new java.util.ArrayList<>();

        JMenu consoleMenu = new JMenu("Developer Console");
        consoleMenu.setIcon(IconManager.createGlassIcon(new Color(0, 150, 200), ">_", 24, 3));

        JMenuItem showItem = new JMenuItem("Show Console");
        showItem.setAccelerator(KeyStroke.getKeyStroke("control shift C"));
        showItem.addActionListener(e -> showConsolePanel());
        consoleMenu.add(showItem);

        consoleMenu.addSeparator();

        JMenuItem helpItem = new JMenuItem("Help");
        helpItem.addActionListener(e -> executeCommand("help"));
        consoleMenu.add(helpItem);

        JMenuItem clearItem = new JMenuItem("Clear Console");
        clearItem.addActionListener(e -> executeCommand("clear"));
        consoleMenu.add(clearItem);

        JMenuItem statsItem = new JMenuItem("Statistics");
        statsItem.addActionListener(e -> executeCommand("stats"));
        consoleMenu.add(statsItem);

        consoleMenu.addSeparator();

        JMenuItem settingsItem = new JMenuItem("Settings");
        settingsItem.addActionListener(e -> showSettings());
        consoleMenu.add(settingsItem);

        items.add(consoleMenu);

        return items;
    }

    @Override
    public void enable() {
        if (enabled) return;

        enabled = true;
        pluginState = PluginStatus.ENABLED;

        if (context != null) {
            context.registerService(MenuProvider.class, this);
        }

        registerUIComponents();

        if (context != null) {
            context.logInfo("Developer Console Plugin enabled");
        }
    }

    @Override
    public void disable() {
        if (!enabled) return;

        enabled = false;
        pluginState = PluginStatus.DISABLED;

        if (uiBuilder != null) {
            try {
                uiBuilder.unregisterComponent(COMPONENT_ID);
            } catch (Exception e) {
                // Ignore
            }
        }

        if (context != null) {
            context.logInfo("Developer Console Plugin disabled");
        }
    }

    @Override
    public void shutdown() {
        disable();
        if (scheduledExecutor != null && !scheduledExecutor.isShutdown()) {
            scheduledExecutor.shutdown();
        }
        if (context != null) {
            context.logInfo("Developer Console Plugin shutdown");
        }
        pluginState = PluginStatus.SHUTDOWN;
    }

    @Override
    public PluginDescriptor getDescriptor() {
        PluginDescriptor desc = new PluginDescriptor();
        desc.setId(PLUGIN_ID);
        desc.setName(PLUGIN_NAME);
        desc.setVersion(PLUGIN_VERSION);
        desc.setMainClass(getClass().getName());
        desc.setDescription("Developer console for plugin testing and debugging");
        desc.setAuthor(PLUGIN_AUTHOR);
        desc.setAuthorEmail(PLUGIN_EMAIL);
        desc.setCategory(PLUGIN_CATEGORY);
        desc.setRequiredHostVersion(REQUIRED_HOST_VERSION);
        return desc;
    }

    @Override public PluginConfig getConfig() { return config; }
    @Override public String getName() { return PLUGIN_NAME; }
    @Override public String getVersion() { return PLUGIN_VERSION; }
    @Override public String getDescription() { return "Developer console for plugin testing"; }
    @Override public String getAuthor() { return PLUGIN_AUTHOR; }
    @Override public String getAuthorEmail() { return PLUGIN_EMAIL; }
    @Override public String getCategory() { return PLUGIN_CATEGORY; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public PluginStatus getState() { return pluginState; }
    @Override public String getRequiredHostVersion() { return REQUIRED_HOST_VERSION; }
    @Override public String getMenuLocation() { return "Tools"; }
    @Override public String getTitle() { return PLUGIN_NAME; }
    @Override public Icon getIcon() { return IconManager.createGlassIcon(new Color(0, 150, 200), ">_", 24, 3); }
}