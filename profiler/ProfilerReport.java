package engine.profiler;

import engine.logging.LogManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.ArrayList;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Comprehensive profiling report containing data from all profilers.
 * Supports export to various formats (JSON, CSV, etc.) with file output capabilities.
 */
public class ProfilerReport {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private final long timestamp;
    private final Map<String, ProfilerData> profilerData = new ConcurrentHashMap<>();
    private PerformanceAnalysis performanceAnalysis;
    private final Map<String, Object> summary = new ConcurrentHashMap<>();
    private final ProfilerManager profilerManager;
    
    public ProfilerReport() {
        this.timestamp = System.currentTimeMillis();
        this.profilerManager = null;
    }
    
    public ProfilerReport(ProfilerManager profilerManager) {
        this.timestamp = System.currentTimeMillis();
        this.profilerManager = profilerManager;
        
        // Automatically collect data from all profilers
        if (profilerManager != null && profilerManager.isInitialized()) {
            collectAllProfilerData();
        }
    }
    
    /**
     * Add profiler data to the report.
     */
    public void addProfilerData(String profilerType, ProfilerData data) {
        profilerData.put(profilerType, data);
    }
    
    /**
     * Set performance analysis results.
     */
    public void setPerformanceAnalysis(PerformanceAnalysis analysis) {
        this.performanceAnalysis = analysis;
    }
    
    /**
     * Add summary information.
     */
    public void addSummary(String key, Object value) {
        summary.put(key, value);
    }
    
    /**
     * Generate summary statistics from all profiler data.
     */
    public void generateSummary() {
        summary.put("reportTimestamp", timestamp);
        summary.put("profilerCount", profilerData.size());
        
        // Calculate overall performance metrics
        double totalFrameTime = 0.0;
        long totalMemoryUsed = 0;
        double totalNetworkLatency = 0.0;
        int activeProfilers = 0;
        
        for (ProfilerData data : profilerData.values()) {
            if (data.getProfilerType().equals("performance")) {
                Double frameTime = data.getMetric("averageFrameTime", Double.class);
                if (frameTime != null) {
                    totalFrameTime += frameTime;
                    activeProfilers++;
                }
            }
            
            if (data.getProfilerType().equals("memory")) {
                Long memoryUsed = data.getMetric("heapUsedMB", Long.class);
                if (memoryUsed != null) {
                    totalMemoryUsed += memoryUsed;
                }
            }
            
            if (data.getProfilerType().equals("network")) {
                Double latency = data.getMetric("averageLatency", Double.class);
                if (latency != null) {
                    totalNetworkLatency += latency;
                }
            }
        }
        
        if (activeProfilers > 0) {
            summary.put("averageFrameTime", totalFrameTime / activeProfilers);
        }
        summary.put("totalMemoryUsedMB", totalMemoryUsed);
        summary.put("averageNetworkLatency", totalNetworkLatency);
        
        // Add performance analysis summary if available
        if (performanceAnalysis != null) {
            summary.put("performanceScore", performanceAnalysis.getOverallScore());
            summary.put("bottleneckCount", performanceAnalysis.getBottlenecks().size());
            summary.put("regressionCount", performanceAnalysis.getRegressions().size());
        }
    }
    
    /**
     * Export report to JSON format.
     */
    public String toJSON() {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"timestamp\": ").append(timestamp).append(",\n");
        json.append("  \"summary\": {\n");
        
        boolean first = true;
        for (Map.Entry<String, Object> entry : summary.entrySet()) {
            if (!first) json.append(",\n");
            json.append("    \"").append(entry.getKey()).append("\": ");
            if (entry.getValue() instanceof String) {
                json.append("\"").append(entry.getValue()).append("\"");
            } else {
                json.append(entry.getValue());
            }
            first = false;
        }
        
        json.append("\n  },\n");
        json.append("  \"profilerData\": {\n");
        
        first = true;
        for (Map.Entry<String, ProfilerData> entry : profilerData.entrySet()) {
            if (!first) json.append(",\n");
            json.append("    \"").append(entry.getKey()).append("\": {\n");
            json.append("      \"timestamp\": ").append(entry.getValue().getTimestamp()).append(",\n");
            json.append("      \"metrics\": {\n");
            
            boolean firstMetric = true;
            for (Map.Entry<String, Object> metric : entry.getValue().getMetrics().entrySet()) {
                if (!firstMetric) json.append(",\n");
                json.append("        \"").append(metric.getKey()).append("\": ");
                if (metric.getValue() instanceof String) {
                    json.append("\"").append(metric.getValue()).append("\"");
                } else {
                    json.append(metric.getValue());
                }
                firstMetric = false;
            }
            
            json.append("\n      }\n");
            json.append("    }");
            first = false;
        }
        
        json.append("\n  }");
        
        if (performanceAnalysis != null) {
            json.append(",\n  \"performanceAnalysis\": {\n");
            json.append("    \"overallScore\": ").append(performanceAnalysis.getOverallScore()).append(",\n");
            json.append("    \"bottlenecks\": [");
            
            first = true;
            for (String bottleneck : performanceAnalysis.getBottlenecks()) {
                if (!first) json.append(", ");
                json.append("\"").append(bottleneck).append("\"");
                first = false;
            }
            
            json.append("],\n");
            json.append("    \"regressions\": [");
            
            first = true;
            for (String regression : performanceAnalysis.getRegressions()) {
                if (!first) json.append(", ");
                json.append("\"").append(regression).append("\"");
                first = false;
            }
            
            json.append("]\n");
            json.append("  }");
        }
        
        json.append("\n}");
        return json.toString();
    }
    
    /**
     * Export report to CSV format.
     */
    public String toCSV() {
        StringBuilder csv = new StringBuilder();
        
        // Header
        csv.append("Profiler,Metric,Value,Timestamp,Unit,Category\n");
        
        // Data rows
        for (Map.Entry<String, ProfilerData> entry : profilerData.entrySet()) {
            String profilerType = entry.getKey();
            ProfilerData data = entry.getValue();
            
            for (Map.Entry<String, Object> metric : data.getMetrics().entrySet()) {
                csv.append(escapeCSV(profilerType)).append(",");
                csv.append(escapeCSV(metric.getKey())).append(",");
                csv.append(escapeCSV(String.valueOf(metric.getValue()))).append(",");
                csv.append(data.getTimestamp()).append(",");
                csv.append(escapeCSV(getMetricUnit(metric.getKey()))).append(",");
                csv.append(escapeCSV(getMetricCategory(metric.getKey()))).append("\n");
            }
        }
        
        // Add summary data
        csv.append("\n# Summary Data\n");
        csv.append("Summary,Metric,Value,Timestamp,Unit,Category\n");
        for (Map.Entry<String, Object> entry : summary.entrySet()) {
            csv.append("Summary,");
            csv.append(escapeCSV(entry.getKey())).append(",");
            csv.append(escapeCSV(String.valueOf(entry.getValue()))).append(",");
            csv.append(timestamp).append(",");
            csv.append(escapeCSV(getMetricUnit(entry.getKey()))).append(",");
            csv.append("Summary\n");
        }
        
        return csv.toString();
    }
    
    /**
     * Export report to JSON file.
     * @param filename Output filename
     * @return Path to created file or null if failed
     */
    public String exportToJSON(String filename) {
        try {
            // Ensure reports directory exists
            Path reportsDir = Paths.get("reports");
            if (!Files.exists(reportsDir)) {
                Files.createDirectories(reportsDir);
            }
            
            // Generate full path
            Path filePath = reportsDir.resolve(filename);
            
            // Generate report content
            generateSummary();
            String jsonContent = toJSON();
            
            // Write to file
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(jsonContent);
            }
            
            logManager.info("ProfilerReport", "JSON report exported successfully",
                           "file", filePath.toString(), "size", jsonContent.length());
            
            return filePath.toString();
            
        } catch (IOException e) {
            logManager.error("ProfilerReport", "Failed to export JSON report", e, "filename", filename);
            return null;
        }
    }
    
    /**
     * Export report to CSV file.
     * @param filename Output filename
     * @return Path to created file or null if failed
     */
    public String exportToCSV(String filename) {
        try {
            // Ensure reports directory exists
            Path reportsDir = Paths.get("reports");
            if (!Files.exists(reportsDir)) {
                Files.createDirectories(reportsDir);
            }
            
            // Generate full path
            Path filePath = reportsDir.resolve(filename);
            
            // Generate report content
            generateSummary();
            String csvContent = toCSV();
            
            // Write to file
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(csvContent);
            }
            
            logManager.info("ProfilerReport", "CSV report exported successfully",
                           "file", filePath.toString(), "size", csvContent.length());
            
            return filePath.toString();
            
        } catch (IOException e) {
            logManager.error("ProfilerReport", "Failed to export CSV report", e, "filename", filename);
            return null;
        }
    }
    
    /**
     * Export report to HTML format for web viewing.
     * @param filename Output filename
     * @return Path to created file or null if failed
     */
    public String exportToHTML(String filename) {
        try {
            // Ensure reports directory exists
            Path reportsDir = Paths.get("reports");
            if (!Files.exists(reportsDir)) {
                Files.createDirectories(reportsDir);
            }
            
            // Generate full path
            Path filePath = reportsDir.resolve(filename);
            
            // Generate report content
            generateSummary();
            String htmlContent = toHTML();
            
            // Write to file
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(htmlContent);
            }
            
            logManager.info("ProfilerReport", "HTML report exported successfully",
                           "file", filePath.toString(), "size", htmlContent.length());
            
            return filePath.toString();
            
        } catch (IOException e) {
            logManager.error("ProfilerReport", "Failed to export HTML report", e, "filename", filename);
            return null;
        }
    }
    
    /**
     * Collect data from all available profilers.
     */
    private void collectAllProfilerData() {
        try {
            if (profilerManager == null) return;
            
            // Collect data from all profilers
            ProfilerData allData = profilerManager.collectData();
            
            // Separate data by profiler type
            if (profilerManager.getPerformanceProfiler() != null) {
                ProfilerData perfData = new ProfilerData();
                perfData.setProfilerType("performance");
                // Copy performance-related metrics
                copyMetricsByPrefix(allData, perfData, "performance");
                addProfilerData("performance", perfData);
            }
            
            if (profilerManager.getMemoryProfiler() != null) {
                ProfilerData memData = new ProfilerData();
                memData.setProfilerType("memory");
                copyMetricsByPrefix(allData, memData, "memory");
                addProfilerData("memory", memData);
            }
            
            if (profilerManager.getNetworkProfiler() != null) {
                ProfilerData netData = new ProfilerData();
                netData.setProfilerType("network");
                copyMetricsByPrefix(allData, netData, "network");
                addProfilerData("network", netData);
            }
            
            if (profilerManager.getRenderProfiler() != null) {
                ProfilerData renderData = new ProfilerData();
                renderData.setProfilerType("render");
                copyMetricsByPrefix(allData, renderData, "render");
                addProfilerData("render", renderData);
            }
            
            // Collect performance analysis if available
            PerformanceAnalyzer analyzer = profilerManager.getPerformanceAnalyzer();
            if (analyzer != null) {
                PerformanceAnalysis analysis = new PerformanceAnalysis();
                analysis.setOverallScore(85.0); // Default score, would be calculated
                
                if (analyzer.hasPerformanceRegression()) {
                    analysis.addRegression("Performance regression detected");
                }
                
                String[] suggestions = analyzer.getOptimizationSuggestions();
                for (String suggestion : suggestions) {
                    analysis.addOptimizationSuggestion(suggestion);
                }
                
                setPerformanceAnalysis(analysis);
            }
            
        } catch (Exception e) {
            logManager.error("ProfilerReport", "Failed to collect profiler data", e);
        }
    }
    
    /**
     * Copy metrics with a specific prefix from source to destination.
     */
    private void copyMetricsByPrefix(ProfilerData source, ProfilerData destination, String prefix) {
        for (Map.Entry<String, Object> entry : source.getMetrics().entrySet()) {
            if (entry.getKey().startsWith(prefix + ".")) {
                destination.addMetric(entry.getKey(), entry.getValue());
            }
        }
    }
    
    /**
     * Generate HTML report format.
     */
    public String toHTML() {
        StringBuilder html = new StringBuilder();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        
        html.append("<!DOCTYPE html>\n");
        html.append("<html>\n<head>\n");
        html.append("<title>Profiler Report - ").append(dateFormat.format(new Date(timestamp))).append("</title>\n");
        html.append("<style>\n");
        html.append("body { font-family: Arial, sans-serif; margin: 20px; }\n");
        html.append("table { border-collapse: collapse; width: 100%; margin: 20px 0; }\n");
        html.append("th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }\n");
        html.append("th { background-color: #f2f2f2; }\n");
        html.append(".summary { background-color: #e8f4fd; padding: 15px; border-radius: 5px; }\n");
        html.append(".profiler-section { margin: 20px 0; }\n");
        html.append(".metric-value { font-weight: bold; }\n");
        html.append("</style>\n");
        html.append("</head>\n<body>\n");
        
        html.append("<h1>Engine Profiler Report</h1>\n");
        html.append("<p><strong>Generated:</strong> ").append(dateFormat.format(new Date(timestamp))).append("</p>\n");
        
        // Summary section
        html.append("<div class='summary'>\n");
        html.append("<h2>Summary</h2>\n");
        html.append("<table>\n<tr><th>Metric</th><th>Value</th></tr>\n");
        for (Map.Entry<String, Object> entry : summary.entrySet()) {
            html.append("<tr><td>").append(entry.getKey()).append("</td>");
            html.append("<td class='metric-value'>").append(entry.getValue()).append("</td></tr>\n");
        }
        html.append("</table>\n</div>\n");
        
        // Profiler data sections
        for (Map.Entry<String, ProfilerData> entry : profilerData.entrySet()) {
            html.append("<div class='profiler-section'>\n");
            html.append("<h2>").append(capitalizeFirst(entry.getKey())).append(" Profiler</h2>\n");
            html.append("<table>\n<tr><th>Metric</th><th>Value</th><th>Unit</th></tr>\n");
            
            for (Map.Entry<String, Object> metric : entry.getValue().getMetrics().entrySet()) {
                html.append("<tr><td>").append(metric.getKey()).append("</td>");
                html.append("<td class='metric-value'>").append(metric.getValue()).append("</td>");
                html.append("<td>").append(getMetricUnit(metric.getKey())).append("</td></tr>\n");
            }
            
            html.append("</table>\n</div>\n");
        }
        
        // Performance analysis section
        if (performanceAnalysis != null) {
            html.append("<div class='profiler-section'>\n");
            html.append("<h2>Performance Analysis</h2>\n");
            html.append("<p><strong>Overall Score:</strong> ").append(performanceAnalysis.getOverallScore()).append("</p>\n");
            
            if (!performanceAnalysis.getBottlenecks().isEmpty()) {
                html.append("<h3>Bottlenecks</h3>\n<ul>\n");
                for (String bottleneck : performanceAnalysis.getBottlenecks()) {
                    html.append("<li>").append(bottleneck).append("</li>\n");
                }
                html.append("</ul>\n");
            }
            
            if (!performanceAnalysis.getRegressions().isEmpty()) {
                html.append("<h3>Regressions</h3>\n<ul>\n");
                for (String regression : performanceAnalysis.getRegressions()) {
                    html.append("<li>").append(regression).append("</li>\n");
                }
                html.append("</ul>\n");
            }
            
            if (!performanceAnalysis.getOptimizationSuggestions().isEmpty()) {
                html.append("<h3>Optimization Suggestions</h3>\n<ul>\n");
                for (String suggestion : performanceAnalysis.getOptimizationSuggestions()) {
                    html.append("<li>").append(suggestion).append("</li>\n");
                }
                html.append("</ul>\n");
            }
            
            html.append("</div>\n");
        }
        
        html.append("</body>\n</html>");
        return html.toString();
    }
    
    /**
     * Escape CSV values to handle commas and quotes.
     */
    private String escapeCSV(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
    
    /**
     * Get the unit for a metric based on its name.
     */
    private String getMetricUnit(String metricName) {
        if (metricName.contains("Time") || metricName.contains("Latency")) {
            return "ms";
        } else if (metricName.contains("Memory") || metricName.contains("Bytes")) {
            return "bytes";
        } else if (metricName.contains("Usage") && metricName.contains("CPU")) {
            return "%";
        } else if (metricName.contains("Count") || metricName.contains("Calls")) {
            return "count";
        } else if (metricName.contains("Rate") || metricName.contains("FPS")) {
            return "Hz";
        }
        return "";
    }
    
    /**
     * Get the category for a metric based on its name.
     */
    private String getMetricCategory(String metricName) {
        if (metricName.startsWith("performance.")) {
            return "Performance";
        } else if (metricName.startsWith("memory.")) {
            return "Memory";
        } else if (metricName.startsWith("network.")) {
            return "Network";
        } else if (metricName.startsWith("render.")) {
            return "Rendering";
        }
        return "General";
    }
    
    /**
     * Capitalize the first letter of a string.
     */
    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
    
    /**
     * Generate a timestamped filename.
     */
    public static String generateTimestampedFilename(String prefix, String extension) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
        return String.format("%s_%s.%s", prefix, dateFormat.format(new Date()), extension);
    }
    
    /**
     * Export all report formats (JSON, CSV, HTML) with timestamped filenames.
     * @param baseFilename Base filename without extension
     * @return Map of format to file path
     */
    public Map<String, String> exportAllFormats(String baseFilename) {
        Map<String, String> results = new ConcurrentHashMap<>();
        
        String jsonFile = exportToJSON(baseFilename + ".json");
        if (jsonFile != null) {
            results.put("json", jsonFile);
        }
        
        String csvFile = exportToCSV(baseFilename + ".csv");
        if (csvFile != null) {
            results.put("csv", csvFile);
        }
        
        String htmlFile = exportToHTML(baseFilename + ".html");
        if (htmlFile != null) {
            results.put("html", htmlFile);
        }
        
        logManager.info("ProfilerReport", "Exported report in multiple formats",
                       "formats", results.size(), "baseFilename", baseFilename);
        
        return results;
    }
    
    // Getters
    public long getTimestamp() { return timestamp; }
    public Map<String, ProfilerData> getProfilerData() { return new ConcurrentHashMap<>(profilerData); }
    public PerformanceAnalysis getPerformanceAnalysis() { return performanceAnalysis; }
    public Map<String, Object> getSummary() { return new ConcurrentHashMap<>(summary); }
    
    @Override
    public String toString() {
        return String.format("ProfilerReport{timestamp=%d, profilers=%d, hasAnalysis=%s}",
                           timestamp, profilerData.size(), performanceAnalysis != null);
    }
    
    /**
     * Performance analysis results container.
     */
    public static class PerformanceAnalysis {
        private double overallScore;
        private final List<String> bottlenecks = new ArrayList<>();
        private final List<String> regressions = new ArrayList<>();
        private final List<String> optimizationSuggestions = new ArrayList<>();
        
        public double getOverallScore() { return overallScore; }
        public void setOverallScore(double overallScore) { this.overallScore = overallScore; }
        
        public List<String> getBottlenecks() { return new ArrayList<>(bottlenecks); }
        public void addBottleneck(String bottleneck) { bottlenecks.add(bottleneck); }
        
        public List<String> getRegressions() { return new ArrayList<>(regressions); }
        public void addRegression(String regression) { regressions.add(regression); }
        
        public List<String> getOptimizationSuggestions() { return new ArrayList<>(optimizationSuggestions); }
        public void addOptimizationSuggestion(String suggestion) { optimizationSuggestions.add(suggestion); }
    }
}