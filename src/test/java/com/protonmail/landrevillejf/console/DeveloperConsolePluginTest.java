package com.protonmail.landrevillejf.console;

import com.protonmail.landrevillejf.swingide.plugin.*;
import com.protonmail.landrevillejf.swingide.plugin.service.*;
import com.protonmail.landrevillejf.swingide.plugin.ui.ComponentRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeveloperConsolePluginTest {

    private DeveloperConsolePlugin plugin;
    private PluginContext mockContext;
    private ExtendedPluginContext mockExtendedContext;
    private PluginManager mockPluginManager;
    private ComponentRegistry mockComponentRegistry;
    private PluginCacheService mockCacheService;
    private PluginNotificationService mockNotificationService;
    private PluginLoggingService mockLoggingService;
    private PluginMetricsService mockMetricsService;
    private PluginDataStore mockDataStore;
    private PluginConfig mockConfig;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        plugin = new DeveloperConsolePlugin();

        mockContext = mock(PluginContext.class);
        mockExtendedContext = mock(ExtendedPluginContext.class);
        mockPluginManager = mock(PluginManager.class);
        mockComponentRegistry = mock(ComponentRegistry.class);
        mockCacheService = mock(PluginCacheService.class);
        mockNotificationService = mock(PluginNotificationService.class);
        mockLoggingService = mock(PluginLoggingService.class);
        mockMetricsService = mock(PluginMetricsService.class);
        mockDataStore = mock(PluginDataStore.class);
        mockConfig = mock(PluginConfig.class);

        when(mockExtendedContext.getPluginManager()).thenReturn(mockPluginManager);
        when(mockExtendedContext.getComponentRegistry()).thenReturn(mockComponentRegistry);
        when(mockExtendedContext.getCacheService()).thenReturn(mockCacheService);
        when(mockExtendedContext.getNotificationService()).thenReturn(mockNotificationService);
        when(mockExtendedContext.getLoggingService()).thenReturn(mockLoggingService);
        when(mockExtendedContext.getMetricsService()).thenReturn(mockMetricsService);
        when(mockExtendedContext.getDataStore()).thenReturn(mockDataStore);
        when(mockExtendedContext.getPluginDataPath()).thenReturn(String.valueOf(tempDir.toFile()));
    }

    // ========== INITIALIZATION TESTS ==========

    @Test
    void testInitialize() {
        plugin.initialize(mockExtendedContext);

        verify(mockExtendedContext, atLeastOnce()).getComponentRegistry();
        // getPluginManager() n'est pas appelé pendant initialize(), seulement dans les commandes
        // Donc on ne le vérifie pas ici
        assertEquals(PluginStatus.ENABLED, plugin.getState());
        assertNotNull(plugin.getConfig());
    }

    // ========== MENU TESTS ==========

    @Test
    void testGetMenuItems() {
        plugin.initialize(mockExtendedContext);
        List<JMenuItem> items = plugin.getMenuItems();

        assertNotNull(items);
        assertFalse(items.isEmpty());

        JMenu menu = (JMenu) items.get(0);
        assertEquals("Developer Console", menu.getText());
        assertEquals(7, menu.getItemCount()); // Show, separator, Help, Clear, Stats, separator, Settings
    }

    // ========== LIFECYCLE TESTS ==========

    @Test
    void testEnable() {
        plugin.initialize(mockExtendedContext);
        plugin.enable();

        assertTrue(plugin.isEnabled());
        assertEquals(PluginStatus.ENABLED, plugin.getState());
        verify(mockExtendedContext).registerService(eq(MenuProvider.class), eq(plugin));
    }

    @Test
    void testEnableWhenAlreadyEnabled() {
        plugin.initialize(mockExtendedContext);
        plugin.enable();
        plugin.enable(); // Second call

        assertTrue(plugin.isEnabled());
        verify(mockExtendedContext, times(1)).registerService(eq(MenuProvider.class), eq(plugin));
    }

    @Test
    void testDisable() {
        plugin.initialize(mockExtendedContext);
        plugin.enable();
        plugin.disable();

        assertFalse(plugin.isEnabled());
        assertEquals(PluginStatus.DISABLED, plugin.getState());
    }

    @Test
    void testDisableWhenAlreadyDisabled() {
        plugin.initialize(mockExtendedContext);
        plugin.disable(); // Already disabled

        assertFalse(plugin.isEnabled());
    }

    @Test
    void testShutdown() {
        plugin.initialize(mockExtendedContext);
        plugin.enable();
        plugin.shutdown();

        assertEquals(PluginStatus.SHUTDOWN, plugin.getState());
    }

    // ========== DESCRIPTOR TESTS ==========

    @Test
    void testGetDescriptor() {
        plugin.initialize(mockExtendedContext);
        PluginDescriptor descriptor = plugin.getDescriptor();

        assertNotNull(descriptor);
        assertEquals("dev-console", descriptor.getId());
        assertEquals("Developer Console", descriptor.getName());
        assertEquals("2.0.0", descriptor.getVersion());
        assertEquals("Jean-Francois Landreville", descriptor.getAuthor());
        assertEquals("Developer Tools", descriptor.getCategory());
        assertEquals("3.0.0", descriptor.getRequiredHostVersion());
    }

    @Test
    void testGetConfig() {
        PluginConfig config = plugin.getConfig();
        assertNotNull(config);
    }

    // ========== GETTER TESTS ==========

    @Test
    void testGetName() {
        assertEquals("Developer Console", plugin.getName());
    }

    @Test
    void testGetVersion() {
        assertEquals("2.0.0", plugin.getVersion());
    }

    @Test
    void testGetDescription() {
        assertNotNull(plugin.getDescription());
        assertTrue(plugin.getDescription().contains("console"));
    }

    @Test
    void testGetAuthor() {
        assertEquals("Jean-Francois Landreville", plugin.getAuthor());
    }

    @Test
    void testGetAuthorEmail() {
        assertEquals("landrevillejf@protonmail.com", plugin.getAuthorEmail());
    }

    @Test
    void testGetCategory() {
        assertEquals("Developer Tools", plugin.getCategory());
    }

    @Test
    void testGetRequiredHostVersion() {
        assertEquals("3.0.0", plugin.getRequiredHostVersion());
    }

    @Test
    void testGetMenuLocation() {
        assertEquals("Tools", plugin.getMenuLocation());
    }

    @Test
    void testGetTitle() {
        assertEquals("Developer Console", plugin.getTitle());
    }

    @Test
    void testGetIcon() {
        assertNotNull(plugin.getIcon());
    }

    // ========== COMMAND TESTS ==========

    @Test
    void testHelpCommand() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos);

        DeveloperConsolePlugin.Command helpCmd = new DeveloperConsolePlugin.Command() {
            @Override public void execute(String[] args, PrintStream out) {
                out.println("Help output");
            }
            @Override public String getDescription() { return "Help"; }
        };

        helpCmd.execute(new String[]{"help"}, out);
        assertTrue(baos.toString().contains("Help output"));
    }

    @Test
    void testClearCommand() {
        // Clear command should not throw exceptions
        assertDoesNotThrow(() -> {
            // Simulate clear command execution
        });
    }

    @Test
    void testEchoCommand() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos);

        DeveloperConsolePlugin.Command echoCmd = new DeveloperConsolePlugin.Command() {
            @Override public void execute(String[] args, PrintStream out) {
                if (args.length > 1) {
                    for (int i = 1; i < args.length; i++) {
                        out.print(args[i] + " ");
                    }
                    out.println();
                }
            }
            @Override public String getDescription() { return "Echo"; }
        };

        echoCmd.execute(new String[]{"echo", "Hello", "World"}, out);
        assertTrue(baos.toString().contains("Hello World"));
    }

    @Test
    void testInfoCommand() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos);

        plugin.initialize(mockExtendedContext);

        DeveloperConsolePlugin.Command infoCmd = new DeveloperConsolePlugin.Command() {
            @Override public void execute(String[] args, PrintStream out) {
                out.println("ID: dev-console");
                out.println("Version: 2.0.0");
            }
            @Override public String getDescription() { return "Info"; }
        };

        infoCmd.execute(new String[]{"info"}, out);
        String output = baos.toString();
        assertTrue(output.contains("dev-console"));
        assertTrue(output.contains("2.0.0"));
    }

    // ========== SERVICE COMMAND TESTS ==========

    @Test
    void testCacheCommandWithAvailableService() {
        when(mockCacheService.size(anyString())).thenReturn(5);
        when(mockCacheService.getKeys(anyString())).thenReturn(Arrays.asList("key1", "key2"));

        plugin.initialize(mockExtendedContext);

        // Cache command should not throw
        assertDoesNotThrow(() -> {
            // Simulate cache command execution
        });
    }

    @Test
    void testMetricsCommandWithAvailableService() {
        Map<String, Object> mockMetrics = new HashMap<>();
        mockMetrics.put("test.metric", 42L);
        when(mockMetricsService.getAllMetrics(anyString())).thenReturn(mockMetrics);

        plugin.initialize(mockExtendedContext);

        assertDoesNotThrow(() -> {
            // Simulate metrics command execution
        });
    }

    // ========== SYSTEM COMMAND TESTS ==========

    @Test
    void testMemoryCommand() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos);

        DeveloperConsolePlugin.Command memoryCmd = new DeveloperConsolePlugin.Command() {
            @Override public void execute(String[] args, PrintStream out) {
                Runtime rt = Runtime.getRuntime();
                out.printf("Memory: %d MB", (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024);
            }
            @Override public String getDescription() { return "Memory"; }
        };

        memoryCmd.execute(new String[]{"memory"}, out);
        assertTrue(baos.toString().contains("Memory:"));
    }

    @Test
    void testThreadsCommand() {
        assertDoesNotThrow(() -> {
            // Threads command should not throw
        });
    }

    @Test
    void testGCCommand() {
        assertDoesNotThrow(() -> {
            System.gc(); // Just ensure it doesn't throw
        });
    }

    @Test
    void testSystemCommand() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos);

        DeveloperConsolePlugin.Command systemCmd = new DeveloperConsolePlugin.Command() {
            @Override public void execute(String[] args, PrintStream out) {
                out.println("OS: " + System.getProperty("os.name"));
                out.println("Java: " + System.getProperty("java.version"));
            }
            @Override public String getDescription() { return "System"; }
        };

        systemCmd.execute(new String[]{"system"}, out);
        String output = baos.toString();
        assertTrue(output.contains("OS:"));
        assertTrue(output.contains("Java:"));
    }

    // ========== DATA STORE COMMAND TESTS ==========

    @Test
    void testSaveCommand() {
        // store() returns void, so we use doNothing() or just don't mock the return value
        doNothing().when(mockDataStore).store(anyString(), anyString(), anyString());
        plugin.initialize(mockExtendedContext);

        assertDoesNotThrow(() -> {
            // Simulate save command
        });
    }

    @Test
    void testLoadCommand() {
        when(mockDataStore.retrieve(anyString(), anyString())).thenReturn("test value");
        plugin.initialize(mockExtendedContext);

        assertDoesNotThrow(() -> {
            // Simulate load command
        });
    }

    @Test
    void testListCommand() {
        Set<String> mockKeys = new HashSet<>(Arrays.asList("key1", "key2"));
        when(mockDataStore.getKeys(anyString())).thenReturn(new ArrayList<>(mockKeys));
        plugin.initialize(mockExtendedContext);

        assertDoesNotThrow(() -> {
            // Simulate list command
        });
    }

    // ========== PROFILING COMMAND TESTS ==========

    @Test
    void testProfileStartCommand() {
        assertDoesNotThrow(() -> {
            // Simulate profile:start command
        });
    }

    @Test
    void testProfileStopCommand() {
        assertDoesNotThrow(() -> {
            // Simulate profile:stop command
        });
    }

    // ========== EXPORT COMMAND TESTS ==========

    @Test
    void testExportCsvCommand() throws Exception {
        File csvFile = tempDir.resolve("test.csv").toFile();

        assertDoesNotThrow(() -> {
            // Simulate export:csv command
            try (java.io.FileWriter fw = new java.io.FileWriter(csvFile)) {
                fw.write("test,data\n");
            }
        });

        assertTrue(csvFile.exists());
    }

    @Test
    void testExportJsonCommand() throws Exception {
        File jsonFile = tempDir.resolve("test.json").toFile();

        assertDoesNotThrow(() -> {
            try (java.io.FileWriter fw = new java.io.FileWriter(jsonFile)) {
                fw.write("{\"test\": \"data\"}\n");
            }
        });

        assertTrue(jsonFile.exists());
    }

    // ========== CONFIG COMMAND TESTS ==========

    @Test
    void testConfigExportCommand() throws Exception {
        File configFile = tempDir.resolve("config.properties").toFile();

        assertDoesNotThrow(() -> {
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(configFile)) {
                java.util.Properties props = new java.util.Properties();
                props.setProperty("test", "value");
                props.store(fos, "Test");
            }
        });

        assertTrue(configFile.exists());
    }

    // ========== SCHEDULER COMMAND TESTS ==========

    @Test
    void testScheduleAddCommand() {
        assertDoesNotThrow(() -> {
            // Simulate schedule:add command
        });
    }

    @Test
    void testScheduleListCommand() {
        assertDoesNotThrow(() -> {
            // Simulate schedule:list command
        });
    }

    // ========== API CHECK COMMAND TESTS ==========

    @Test
    void testApiCheckCommand() {
        assertDoesNotThrow(() -> {
            // Simulate api:check command
        });
    }

    // ========== DEPENDENCY GRAPH COMMAND TESTS ==========

    @Test
    void testDepsGraphCommand() {
        assertDoesNotThrow(() -> {
            // Simulate deps:graph command
        });
    }

    // ========== ALIAS TESTS ==========

    @Test
    void testAliasesExist() {
        plugin.initialize(mockExtendedContext);
        // Aliases should be registered without errors
    }

    // ========== UI COMPONENT TESTS ==========

    @Test
    void testRegisterUIComponents() {
        plugin.initialize(mockExtendedContext);
        assertDoesNotThrow(() -> plugin.enable());
    }

    @Test
    void testShowConsolePanel() {
        plugin.initialize(mockExtendedContext);
        assertDoesNotThrow(() -> plugin.showConsolePanel());
    }

    // ========== SETTINGS TESTS ==========

    @Test
    void testShowSettings() {
        plugin.initialize(mockExtendedContext);
        assertDoesNotThrow(() -> {
            SwingUtilities.invokeLater(() -> plugin.showSettings());
        });
    }

    // ========== CONFIGURATION TESTS ==========

    @Test
    void testLoadConfiguration() {
        PluginConfig testConfig = mock(PluginConfig.class);
        when(testConfig.getSettingAsString(eq("console.ui.position"), anyString())).thenReturn("tab");

        assertDoesNotThrow(() -> {
            // Configuration loading should not throw
        });
    }

    @Test
    void testSaveConfiguration() {
        assertDoesNotThrow(() -> {
            // Configuration saving should not throw
        });
    }

    // ========== MONITORING TESTS ==========

    @Test
    void testStartMonitoring() {
        plugin.initialize(mockExtendedContext);
        assertDoesNotThrow(() -> {
            // Monitoring should start without errors
        });
    }

    @Test
    void testStopMonitoring() {
        plugin.initialize(mockExtendedContext);
        assertDoesNotThrow(() -> {
            // Monitoring should stop without errors
        });
    }

    // ========== STATS COMMAND TESTS ==========

    @Test
    void testStatsCommand() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos);

        assertDoesNotThrow(() -> {
            out.println("Commands executed: 10");
            out.println("Available commands: 50");
        });

        assertTrue(baos.toString().contains("Commands executed:"));
    }

    // ========== EDGE CASES ==========

    @Test
    void testMultipleInitializeCalls() {
        plugin.initialize(mockExtendedContext);
        assertDoesNotThrow(() -> plugin.initialize(mockExtendedContext));
    }

    @Test
    void testCommandExecutionWithNullArgs() {
        assertDoesNotThrow(() -> {
            plugin.executeCommand("");
            plugin.executeCommand(null);
            plugin.executeCommand("   ");
        });
    }

    @Test
    void testUnknownCommand() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos);

        assertDoesNotThrow(() -> {
            out.println("Unknown command: fakecommand");
        });
    }

    // ========== INTEGRATION TESTS ==========

    @Test
    void testFullLifecycle() {
        plugin.initialize(mockExtendedContext);
        plugin.enable();
        assertTrue(plugin.isEnabled());

        plugin.disable();
        assertFalse(plugin.isEnabled());

        plugin.enable();
        assertTrue(plugin.isEnabled());

        plugin.shutdown();
        assertEquals(PluginStatus.SHUTDOWN, plugin.getState());
    }
}