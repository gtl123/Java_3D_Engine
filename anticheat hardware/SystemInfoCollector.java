package fps.anticheat.hardware;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

/**
 * Collects comprehensive system and hardware information for fingerprinting.
 * Uses multiple methods to gather hardware data while handling platform differences.
 */
public class SystemInfoCollector {
    
    private static final String OS_NAME = System.getProperty("os.name").toLowerCase();
    private static final boolean IS_WINDOWS = OS_NAME.contains("windows");
    private static final boolean IS_LINUX = OS_NAME.contains("linux");
    private static final boolean IS_MAC = OS_NAME.contains("mac");
    
    // Command timeouts
    private static final int COMMAND_TIMEOUT_MS = 5000;
    
    public SystemInfoCollector() {
        // Initialize any required resources
    }
    
    /**
     * Collect comprehensive hardware information
     */
    public HardwareInfo collectHardwareInfo() {
        HardwareInfo hardwareInfo = new HardwareInfo();
        
        try {
            // Collect basic system information
            collectSystemInfo(hardwareInfo);
            
            // Collect CPU information
            collectCPUInfo(hardwareInfo);
            
            // Collect memory information
            collectMemoryInfo(hardwareInfo);
            
            // Collect motherboard information
            collectMotherboardInfo(hardwareInfo);
            
            // Collect storage information
            collectStorageInfo(hardwareInfo);
            
            // Collect GPU information
            collectGPUInfo(hardwareInfo);
            
            // Collect network information
            collectNetworkInfo(hardwareInfo);
            
        } catch (Exception e) {
            hardwareInfo.addCollectionError("Hardware collection failed: " + e.getMessage());
        }
        
        return hardwareInfo;
    }
    
    /**
     * Collect basic system information
     */
    private void collectSystemInfo(HardwareInfo hardwareInfo) {
        try {
            // Operating system information
            hardwareInfo.setOperatingSystem(System.getProperty("os.name"));
            hardwareInfo.setOsVersion(System.getProperty("os.version"));
            
            // System uptime
            if (IS_WINDOWS) {
                String uptime = executeCommand("wmic os get LastBootUpTime /value");
                hardwareInfo.setSystemUptime(parseWindowsUptime(uptime));
            } else if (IS_LINUX) {
                String uptime = executeCommand("cat /proc/uptime");
                hardwareInfo.setSystemUptime(parseLinuxUptime(uptime));
            }
            
            // Hostname
            try {
                hardwareInfo.setHostname(InetAddress.getLocalHost().getHostName());
            } catch (Exception e) {
                hardwareInfo.setHostname("Unknown");
            }
            
            // System manufacturer and model
            if (IS_WINDOWS) {
                String manufacturer = executeCommand("wmic computersystem get Manufacturer /value");
                String model = executeCommand("wmic computersystem get Model /value");
                hardwareInfo.setSystemManufacturer(parseWmicValue(manufacturer, "Manufacturer"));
                hardwareInfo.setSystemModel(parseWmicValue(model, "Model"));
            }
            
        } catch (Exception e) {
            hardwareInfo.addCollectionError("System info collection failed: " + e.getMessage());
        }
    }
    
    /**
     * Collect CPU information
     */
    private void collectCPUInfo(HardwareInfo hardwareInfo) {
        try {
            if (IS_WINDOWS) {
                collectWindowsCPUInfo(hardwareInfo);
            } else if (IS_LINUX) {
                collectLinuxCPUInfo(hardwareInfo);
            } else {
                // Fallback to Java system properties
                collectFallbackCPUInfo(hardwareInfo);
            }
        } catch (Exception e) {
            hardwareInfo.addCollectionError("CPU info collection failed: " + e.getMessage());
            collectFallbackCPUInfo(hardwareInfo);
        }
    }
    
    /**
     * Collect Windows CPU information
     */
    private void collectWindowsCPUInfo(HardwareInfo hardwareInfo) {
        try {
            String cpuInfo = executeCommand("wmic cpu get Name,Manufacturer,Architecture,NumberOfCores,NumberOfLogicalProcessors,MaxClockSpeed /value");
            
            hardwareInfo.setCpuModel(parseWmicValue(cpuInfo, "Name"));
            hardwareInfo.setCpuVendor(parseWmicValue(cpuInfo, "Manufacturer"));
            hardwareInfo.setCpuArchitecture(parseWmicValue(cpuInfo, "Architecture"));
            hardwareInfo.setCpuCores(parseWmicIntValue(cpuInfo, "NumberOfCores"));
            hardwareInfo.setCpuThreads(parseWmicIntValue(cpuInfo, "NumberOfLogicalProcessors"));
            hardwareInfo.setCpuFrequency(parseWmicLongValue(cpuInfo, "MaxClockSpeed") * 1000000L); // Convert MHz to Hz
            
        } catch (Exception e) {
            throw new RuntimeException("Windows CPU info collection failed", e);
        }
    }
    
    /**
     * Collect Linux CPU information
     */
    private void collectLinuxCPUInfo(HardwareInfo hardwareInfo) {
        try {
            String cpuInfo = executeCommand("cat /proc/cpuinfo");
            
            // Parse CPU info from /proc/cpuinfo
            String[] lines = cpuInfo.split("\n");
            for (String line : lines) {
                if (line.startsWith("model name")) {
                    hardwareInfo.setCpuModel(line.split(":")[1].trim());
                } else if (line.startsWith("vendor_id")) {
                    hardwareInfo.setCpuVendor(line.split(":")[1].trim());
                } else if (line.startsWith("cpu cores")) {
                    hardwareInfo.setCpuCores(Integer.parseInt(line.split(":")[1].trim()));
                } else if (line.startsWith("siblings")) {
                    hardwareInfo.setCpuThreads(Integer.parseInt(line.split(":")[1].trim()));
                } else if (line.startsWith("cpu MHz")) {
                    hardwareInfo.setCpuFrequency((long)(Float.parseFloat(line.split(":")[1].trim()) * 1000000L));
                }
            }
            
        } catch (Exception e) {
            throw new RuntimeException("Linux CPU info collection failed", e);
        }
    }
    
    /**
     * Collect fallback CPU information using Java system properties
     */
    private void collectFallbackCPUInfo(HardwareInfo hardwareInfo) {
        hardwareInfo.setCpuModel("Unknown");
        hardwareInfo.setCpuVendor("Unknown");
        hardwareInfo.setCpuArchitecture(System.getProperty("os.arch"));
        hardwareInfo.setCpuCores(Runtime.getRuntime().availableProcessors());
        hardwareInfo.setCpuThreads(Runtime.getRuntime().availableProcessors());
    }
    
    /**
     * Collect memory information
     */
    private void collectMemoryInfo(HardwareInfo hardwareInfo) {
        try {
            if (IS_WINDOWS) {
                String memInfo = executeCommand("wmic memorychip get Capacity,Speed,MemoryType /value");
                long totalMemory = 0;
                int moduleCount = 0;
                
                String[] lines = memInfo.split("\n");
                for (String line : lines) {
                    if (line.startsWith("Capacity=") && !line.contains("Capacity=")) {
                        try {
                            long capacity = Long.parseLong(line.split("=")[1].trim());
                            totalMemory += capacity;
                            moduleCount++;
                        } catch (NumberFormatException ignored) {}
                    }
                }
                
                hardwareInfo.setTotalMemory(totalMemory);
                hardwareInfo.setMemoryModules(moduleCount);
                
            } else if (IS_LINUX) {
                String memInfo = executeCommand("cat /proc/meminfo");
                String[] lines = memInfo.split("\n");
                for (String line : lines) {
                    if (line.startsWith("MemTotal:")) {
                        String[] parts = line.split("\\s+");
                        long memKB = Long.parseLong(parts[1]);
                        hardwareInfo.setTotalMemory(memKB * 1024); // Convert KB to bytes
                        break;
                    }
                }
            } else {
                // Fallback using Java Runtime
                Runtime runtime = Runtime.getRuntime();
                hardwareInfo.setTotalMemory(runtime.maxMemory());
            }
            
        } catch (Exception e) {
            hardwareInfo.addCollectionError("Memory info collection failed: " + e.getMessage());
        }
    }
    
    /**
     * Collect motherboard information
     */
    private void collectMotherboardInfo(HardwareInfo hardwareInfo) {
        try {
            if (IS_WINDOWS) {
                String mbInfo = executeCommand("wmic baseboard get Manufacturer,Product,SerialNumber /value");
                String biosInfo = executeCommand("wmic bios get Version,ReleaseDate /value");
                
                hardwareInfo.setMotherboardManufacturer(parseWmicValue(mbInfo, "Manufacturer"));
                hardwareInfo.setMotherboardModel(parseWmicValue(mbInfo, "Product"));
                hardwareInfo.setMotherboardSerial(parseWmicValue(mbInfo, "SerialNumber"));
                hardwareInfo.setBiosVersion(parseWmicValue(biosInfo, "Version"));
                hardwareInfo.setBiosDate(parseWmicValue(biosInfo, "ReleaseDate"));
            }
        } catch (Exception e) {
            hardwareInfo.addCollectionError("Motherboard info collection failed: " + e.getMessage());
        }
    }
    
    /**
     * Collect storage information
     */
    private void collectStorageInfo(HardwareInfo hardwareInfo) {
        try {
            if (IS_WINDOWS) {
                String diskInfo = executeCommand("wmic diskdrive get Model,SerialNumber,Size,MediaType /value");
                parseWindowsStorageInfo(hardwareInfo, diskInfo);
            } else if (IS_LINUX) {
                String diskInfo = executeCommand("lsblk -d -o NAME,SIZE,MODEL,SERIAL");
                parseLinuxStorageInfo(hardwareInfo, diskInfo);
            }
        } catch (Exception e) {
            hardwareInfo.addCollectionError("Storage info collection failed: " + e.getMessage());
        }
    }
    
    /**
     * Parse Windows storage information
     */
    private void parseWindowsStorageInfo(HardwareInfo hardwareInfo, String diskInfo) {
        String[] entries = diskInfo.split("\n\n");
        for (String entry : entries) {
            if (entry.trim().isEmpty()) continue;
            
            String model = parseWmicValue(entry, "Model");
            String serial = parseWmicValue(entry, "SerialNumber");
            String sizeStr = parseWmicValue(entry, "Size");
            String mediaType = parseWmicValue(entry, "MediaType");
            
            if (!model.equals("Unknown") && !sizeStr.equals("Unknown")) {
                try {
                    long size = Long.parseLong(sizeStr);
                    StorageDevice device = new StorageDevice(model, serial, "Unknown", size, mediaType);
                    hardwareInfo.addStorageDevice(device);
                } catch (NumberFormatException ignored) {}
            }
        }
    }
    
    /**
     * Parse Linux storage information
     */
    private void parseLinuxStorageInfo(HardwareInfo hardwareInfo, String diskInfo) {
        String[] lines = diskInfo.split("\n");
        for (int i = 1; i < lines.length; i++) { // Skip header
            String[] parts = lines[i].trim().split("\\s+");
            if (parts.length >= 3) {
                String name = parts[0];
                String sizeStr = parts[1];
                String model = parts.length > 2 ? parts[2] : "Unknown";
                
                try {
                    long size = parseLinuxSize(sizeStr);
                    StorageDevice device = new StorageDevice(model, "Unknown", "Unknown", size, "Unknown");
                    hardwareInfo.addStorageDevice(device);
                } catch (Exception ignored) {}
            }
        }
    }
    
    /**
     * Collect GPU information
     */
    private void collectGPUInfo(HardwareInfo hardwareInfo) {
        try {
            if (IS_WINDOWS) {
                String gpuInfo = executeCommand("wmic path win32_VideoController get Name,AdapterRAM,DriverVersion /value");
                parseWindowsGPUInfo(hardwareInfo, gpuInfo);
            } else if (IS_LINUX) {
                String gpuInfo = executeCommand("lspci | grep VGA");
                parseLinuxGPUInfo(hardwareInfo, gpuInfo);
            }
        } catch (Exception e) {
            hardwareInfo.addCollectionError("GPU info collection failed: " + e.getMessage());
        }
    }
    
    /**
     * Parse Windows GPU information
     */
    private void parseWindowsGPUInfo(HardwareInfo hardwareInfo, String gpuInfo) {
        String[] entries = gpuInfo.split("\n\n");
        boolean first = true;
        
        for (String entry : entries) {
            if (entry.trim().isEmpty()) continue;
            
            String name = parseWmicValue(entry, "Name");
            String ramStr = parseWmicValue(entry, "AdapterRAM");
            String driver = parseWmicValue(entry, "DriverVersion");
            
            if (!name.equals("Unknown")) {
                long memory = 0;
                try {
                    memory = Long.parseLong(ramStr);
                } catch (NumberFormatException ignored) {}
                
                String vendor = determineGPUVendor(name);
                GPUInfo gpu = new GPUInfo(name, vendor, "Unknown", memory, "Unknown", first);
                gpu.setDriverVersion(driver);
                hardwareInfo.addGpuDevice(gpu);
                
                if (first) {
                    hardwareInfo.setGpuModel(name);
                    hardwareInfo.setGpuVendor(vendor);
                    hardwareInfo.setGpuMemory(memory);
                    hardwareInfo.setGpuDriverVersion(driver);
                    first = false;
                }
            }
        }
    }
    
    /**
     * Parse Linux GPU information
     */
    private void parseLinuxGPUInfo(HardwareInfo hardwareInfo, String gpuInfo) {
        String[] lines = gpuInfo.split("\n");
        boolean first = true;
        
        for (String line : lines) {
            if (line.contains("VGA")) {
                String[] parts = line.split(":");
                if (parts.length > 1) {
                    String name = parts[1].trim();
                    String vendor = determineGPUVendor(name);
                    
                    GPUInfo gpu = new GPUInfo(name, vendor, "Unknown", 0, "Unknown", first);
                    hardwareInfo.addGpuDevice(gpu);
                    
                    if (first) {
                        hardwareInfo.setGpuModel(name);
                        hardwareInfo.setGpuVendor(vendor);
                        first = false;
                    }
                }
            }
        }
    }
    
    /**
     * Collect network information
     */
    private void collectNetworkInfo(HardwareInfo hardwareInfo) {
        try {
            // Get network interfaces using Java
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                
                String name = ni.getDisplayName() != null ? ni.getDisplayName() : ni.getName();
                String mac = formatMacAddress(ni.getHardwareAddress());
                String type = determineNetworkType(name);
                
                NetworkAdapter adapter = new NetworkAdapter(name, mac, "Unknown", type);
                adapter.setUp(ni.isUp());
                adapter.setLoopback(ni.isLoopback());
                adapter.setVirtual(ni.isVirtual());
                
                hardwareInfo.addNetworkAdapter(adapter);
            }
        } catch (Exception e) {
            hardwareInfo.addCollectionError("Network info collection failed: " + e.getMessage());
        }
    }
    
    /**
     * Execute a system command and return output
     */
    private String executeCommand(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder();
            if (IS_WINDOWS) {
                pb.command("cmd", "/c", command);
            } else {
                pb.command("sh", "-c", command);
            }
            
            Process process = pb.start();
            
            // Set timeout
            boolean finished = process.waitFor(COMMAND_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "";
            }
            
            // Read output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            return output.toString();
            
        } catch (Exception e) {
            return "";
        }
    }
    
    /**
     * Parse WMIC value from output
     */
    private String parseWmicValue(String output, String key) {
        String[] lines = output.split("\n");
        for (String line : lines) {
            if (line.startsWith(key + "=")) {
                String value = line.substring(key.length() + 1).trim();
                return value.isEmpty() ? "Unknown" : value;
            }
        }
        return "Unknown";
    }
    
    /**
     * Parse WMIC integer value
     */
    private int parseWmicIntValue(String output, String key) {
        String value = parseWmicValue(output, key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    /**
     * Parse WMIC long value
     */
    private long parseWmicLongValue(String output, String key) {
        String value = parseWmicValue(output, key);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
    
    /**
     * Parse Windows uptime
     */
    private long parseWindowsUptime(String output) {
        // Implementation would parse Windows boot time and calculate uptime
        return System.currentTimeMillis(); // Placeholder
    }
    
    /**
     * Parse Linux uptime
     */
    private long parseLinuxUptime(String output) {
        try {
            String[] parts = output.trim().split("\\s+");
            float uptimeSeconds = Float.parseFloat(parts[0]);
            return (long)(uptimeSeconds * 1000); // Convert to milliseconds
        } catch (Exception e) {
            return 0L;
        }
    }
    
    /**
     * Parse Linux size string (e.g., "500G", "1T")
     */
    private long parseLinuxSize(String sizeStr) {
        if (sizeStr == null || sizeStr.isEmpty()) return 0L;
        
        sizeStr = sizeStr.toUpperCase();
        long multiplier = 1L;
        
        if (sizeStr.endsWith("K")) {
            multiplier = 1024L;
            sizeStr = sizeStr.substring(0, sizeStr.length() - 1);
        } else if (sizeStr.endsWith("M")) {
            multiplier = 1024L * 1024L;
            sizeStr = sizeStr.substring(0, sizeStr.length() - 1);
        } else if (sizeStr.endsWith("G")) {
            multiplier = 1024L * 1024L * 1024L;
            sizeStr = sizeStr.substring(0, sizeStr.length() - 1);
        } else if (sizeStr.endsWith("T")) {
            multiplier = 1024L * 1024L * 1024L * 1024L;
            sizeStr = sizeStr.substring(0, sizeStr.length() - 1);
        }
        
        try {
            return (long)(Float.parseFloat(sizeStr) * multiplier);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
    
    /**
     * Determine GPU vendor from name
     */
    private String determineGPUVendor(String name) {
        if (name == null) return "Unknown";
        
        String nameLower = name.toLowerCase();
        if (nameLower.contains("nvidia") || nameLower.contains("geforce") || nameLower.contains("quadro")) {
            return "NVIDIA";
        } else if (nameLower.contains("amd") || nameLower.contains("radeon") || nameLower.contains("ati")) {
            return "AMD";
        } else if (nameLower.contains("intel")) {
            return "Intel";
        } else {
            return "Unknown";
        }
    }
    
    /**
     * Format MAC address
     */
    private String formatMacAddress(byte[] mac) {
        if (mac == null || mac.length != 6) return "Unknown";
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mac.length; i++) {
            sb.append(String.format("%02X", mac[i]));
            if (i < mac.length - 1) sb.append(":");
        }
        return sb.toString();
    }
    
    /**
     * Determine network adapter type from name
     */
    private String determineNetworkType(String name) {
        if (name == null) return "Unknown";
        
        String nameLower = name.toLowerCase();
        if (nameLower.contains("ethernet") || nameLower.contains("eth")) {
            return "Ethernet";
        } else if (nameLower.contains("wifi") || nameLower.contains("wireless") || nameLower.contains("802.11")) {
            return "WiFi";
        } else if (nameLower.contains("bluetooth")) {
            return "Bluetooth";
        } else if (nameLower.contains("loopback")) {
            return "Loopback";
        } else {
            return "Unknown";
        }
    }
}