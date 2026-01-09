package fps.anticheat.hardware;

import java.util.*;

/**
 * Represents a network adapter in the system.
 * Contains network adapter identification and characteristics for fingerprinting.
 */
public class NetworkAdapter {
    
    private final String name;
    private final String macAddress;
    private final String vendor;
    private final String type; // Ethernet, WiFi, Bluetooth, etc.
    private final boolean isPhysical;
    
    private String description;
    private String driverVersion;
    private boolean isUp;
    private boolean isLoopback;
    private boolean isVirtual;
    private long speed; // Speed in bits per second
    private String ipAddress;
    private String subnetMask;
    private String gateway;
    private List<String> dnsServers;
    
    public NetworkAdapter(String name, String macAddress, String vendor, String type) {
        this.name = name != null ? name : "Unknown";
        this.macAddress = macAddress != null ? macAddress.toUpperCase() : "Unknown";
        this.vendor = vendor != null ? vendor : "Unknown";
        this.type = type != null ? type : "Unknown";
        this.isPhysical = determineIfPhysical();
        this.dnsServers = new ArrayList<>();
        this.isUp = false;
        this.isLoopback = false;
        this.isVirtual = false;
        this.speed = -1;
    }
    
    public NetworkAdapter(String name, String macAddress, String vendor, String type, boolean isPhysical) {
        this.name = name != null ? name : "Unknown";
        this.macAddress = macAddress != null ? macAddress.toUpperCase() : "Unknown";
        this.vendor = vendor != null ? vendor : "Unknown";
        this.type = type != null ? type : "Unknown";
        this.isPhysical = isPhysical;
        this.dnsServers = new ArrayList<>();
        this.isUp = false;
        this.isLoopback = false;
        this.isVirtual = false;
        this.speed = -1;
    }
    
    /**
     * Determine if adapter is physical based on name and type
     */
    private boolean determineIfPhysical() {
        if (name == null || type == null) return false;
        
        String nameLower = name.toLowerCase();
        String typeLower = type.toLowerCase();
        
        // Virtual adapter indicators
        String[] virtualIndicators = {
            "virtual", "vmware", "virtualbox", "hyper-v", "vbox", "tap", "tun",
            "loopback", "teredo", "isatap", "6to4", "bluetooth", "vpn"
        };
        
        for (String indicator : virtualIndicators) {
            if (nameLower.contains(indicator) || typeLower.contains(indicator)) {
                return false;
            }
        }
        
        // Physical adapter indicators
        return typeLower.contains("ethernet") || 
               typeLower.contains("wifi") || 
               typeLower.contains("wireless") ||
               typeLower.contains("802.11");
    }
    
    /**
     * Check if MAC address is valid
     */
    public boolean hasValidMacAddress() {
        if (macAddress == null || macAddress.equals("Unknown")) {
            return false;
        }
        
        // Check MAC address format (XX:XX:XX:XX:XX:XX or XX-XX-XX-XX-XX-XX)
        String cleanMac = macAddress.replaceAll("[:-]", "");
        if (cleanMac.length() != 12) {
            return false;
        }
        
        // Check if all characters are hex
        try {
            Long.parseLong(cleanMac, 16);
        } catch (NumberFormatException e) {
            return false;
        }
        
        // Check for invalid MAC addresses
        String[] invalidMacs = {
            "000000000000", "FFFFFFFFFFFF", "AAAAAAAAAAAA", "555555555555"
        };
        
        for (String invalid : invalidMacs) {
            if (cleanMac.equals(invalid)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Get MAC address vendor from OUI (Organizationally Unique Identifier)
     */
    public String getMacVendor() {
        if (!hasValidMacAddress()) {
            return "Unknown";
        }
        
        String oui = macAddress.replaceAll("[:-]", "").substring(0, 6).toUpperCase();
        
        // Common OUI prefixes (simplified mapping)
        Map<String, String> ouiMap = new HashMap<>();
        ouiMap.put("001B63", "Apple");
        ouiMap.put("00D0C9", "Intel");
        ouiMap.put("001AA0", "Dell");
        ouiMap.put("00E04C", "Realtek");
        ouiMap.put("001E58", "Broadcom");
        ouiMap.put("00237D", "Qualcomm");
        ouiMap.put("001CF0", "Marvell");
        ouiMap.put("00904C", "Epigram");
        
        return ouiMap.getOrDefault(oui, vendor);
    }
    
    /**
     * Check if this is an Ethernet adapter
     */
    public boolean isEthernet() {
        return type.toUpperCase().contains("ETHERNET") ||
               name.toUpperCase().contains("ETHERNET") ||
               name.toUpperCase().contains("ETH");
    }
    
    /**
     * Check if this is a WiFi adapter
     */
    public boolean isWiFi() {
        return type.toUpperCase().contains("WIFI") ||
               type.toUpperCase().contains("WIRELESS") ||
               type.toUpperCase().contains("802.11") ||
               name.toUpperCase().contains("WIFI") ||
               name.toUpperCase().contains("WIRELESS");
    }
    
    /**
     * Check if this is a Bluetooth adapter
     */
    public boolean isBluetooth() {
        return type.toUpperCase().contains("BLUETOOTH") ||
               name.toUpperCase().contains("BLUETOOTH");
    }
    
    /**
     * Get adapter speed in human-readable format
     */
    public String getFormattedSpeed() {
        if (speed <= 0) return "Unknown";
        
        if (speed >= 1000000000) {
            return String.format("%.1f Gbps", speed / 1000000000.0);
        } else if (speed >= 1000000) {
            return String.format("%.0f Mbps", speed / 1000000.0);
        } else if (speed >= 1000) {
            return String.format("%.0f Kbps", speed / 1000.0);
        } else {
            return speed + " bps";
        }
    }
    
    /**
     * Get adapter fingerprint for identification
     */
    public String getAdapterFingerprint() {
        StringBuilder fingerprint = new StringBuilder();
        fingerprint.append(name);
        fingerprint.append("|");
        fingerprint.append(macAddress);
        fingerprint.append("|");
        fingerprint.append(vendor);
        fingerprint.append("|");
        fingerprint.append(type);
        
        return fingerprint.toString();
    }
    
    /**
     * Check if adapter is suitable for fingerprinting
     */
    public boolean isSuitableForFingerprinting() {
        return isPhysical && 
               hasValidMacAddress() && 
               !isLoopback && 
               !isVirtual &&
               (isEthernet() || isWiFi());
    }
    
    /**
     * Get adapter priority for fingerprinting (higher = more important)
     */
    public int getFingerprintPriority() {
        int priority = 0;
        
        // Physical adapters get higher priority
        if (isPhysical) priority += 50;
        
        // Active adapters get higher priority
        if (isUp) priority += 30;
        
        // Ethernet gets highest priority, then WiFi
        if (isEthernet()) priority += 40;
        else if (isWiFi()) priority += 30;
        else if (isBluetooth()) priority += 10;
        
        // Valid MAC address increases priority
        if (hasValidMacAddress()) priority += 25;
        
        // Higher speed gets slight priority boost
        if (speed >= 1000000000) priority += 15; // >= 1 Gbps
        else if (speed >= 100000000) priority += 10; // >= 100 Mbps
        else if (speed >= 10000000) priority += 5; // >= 10 Mbps
        
        // Non-virtual adapters get priority
        if (!isVirtual) priority += 20;
        
        return priority;
    }
    
    /**
     * Add DNS server
     */
    public void addDnsServer(String dnsServer) {
        if (dnsServer != null && !dnsServer.isEmpty()) {
            dnsServers.add(dnsServer);
        }
    }
    
    /**
     * Check if adapter has network configuration
     */
    public boolean hasNetworkConfiguration() {
        return ipAddress != null && !ipAddress.isEmpty() &&
               subnetMask != null && !subnetMask.isEmpty();
    }
    
    /**
     * Check if adapter is connected to internet
     */
    public boolean hasInternetAccess() {
        return isUp && hasNetworkConfiguration() && 
               gateway != null && !gateway.isEmpty() &&
               !dnsServers.isEmpty();
    }
    
    @Override
    public String toString() {
        return String.format("NetworkAdapter{name='%s', type='%s', mac='%s', up=%s, physical=%s}", 
                           name, type, 
                           hasValidMacAddress() ? macAddress.substring(0, 8) + "..." : "Invalid",
                           isUp, isPhysical);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        NetworkAdapter that = (NetworkAdapter) obj;
        return name.equals(that.name) && 
               macAddress.equals(that.macAddress) && 
               type.equals(that.type);
    }
    
    @Override
    public int hashCode() {
        return name.hashCode() + macAddress.hashCode() + type.hashCode();
    }
    
    // Getters and setters
    public String getName() { return name; }
    public String getMacAddress() { return macAddress; }
    public String getVendor() { return vendor; }
    public String getType() { return type; }
    public boolean isPhysical() { return isPhysical; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getDriverVersion() { return driverVersion; }
    public void setDriverVersion(String driverVersion) { this.driverVersion = driverVersion; }
    
    public boolean isUp() { return isUp; }
    public void setUp(boolean up) { isUp = up; }
    
    public boolean isLoopback() { return isLoopback; }
    public void setLoopback(boolean loopback) { isLoopback = loopback; }
    
    public boolean isVirtual() { return isVirtual; }
    public void setVirtual(boolean virtual) { isVirtual = virtual; }
    
    public long getSpeed() { return speed; }
    public void setSpeed(long speed) { this.speed = speed; }
    
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    
    public String getSubnetMask() { return subnetMask; }
    public void setSubnetMask(String subnetMask) { this.subnetMask = subnetMask; }
    
    public String getGateway() { return gateway; }
    public void setGateway(String gateway) { this.gateway = gateway; }
    
    public List<String> getDnsServers() { return new ArrayList<>(dnsServers); }
    public void setDnsServers(List<String> dnsServers) { this.dnsServers = new ArrayList<>(dnsServers); }
}