package climate.emissions;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class EnhancedCarbonEmissionTracker extends CarbonEmissionTrackerGrpc.CarbonEmissionTrackerImplBase {
    private final Map<String, List<UsageEntry>> deviceUsageData = new ConcurrentHashMap<>();
    private final Map<String, Float> emissionFactors = new ConcurrentHashMap<>();
    private final ScheduledExecutorService dataPersistence = Executors.newSingleThreadScheduledExecutor();
    private final AtomicReference<List<EmissionTrend>> trends = new AtomicReference<>(new ArrayList<>());
    
    private static class EmissionTrend {
        String period;  
        float co2;
        float changePercent;
    }
    
    public EnhancedCarbonEmissionTracker() {
        emissionFactors.put("ELECTRICITY", 0.5f);
        emissionFactors.put("GAS", 0.2f);
        emissionFactors.put("SOLAR", 0.05f);
        emissionFactors.put("WIND", 0.01f);
        
        loadPersistedData();
        
        dataPersistence.scheduleAtFixedRate(this::persistData, 1, 1, TimeUnit.HOURS);
        
        dataPersistence.scheduleAtFixedRate(this::analyzeTrends, 0, 6, TimeUnit.HOURS);
    }
    
    @Override
    public StreamObserver<UsageEntry> logDeviceUsage(StreamObserver<EmissionSummary> responseObserver) {
        return new StreamObserver<UsageEntry>() {
            final List<UsageEntry> entries = new ArrayList<>();
            float totalCO2 = 0;
            
            @Override
            public void onNext(UsageEntry entry) {
                entries.add(entry);
                
                float factor = emissionFactors.getOrDefault(
                    getEnergySource(entry.getDeviceType()), 
                    0.3f  
                );
                
                float co2 = entry.getDurationHours() * entry.getPowerRatingKw() * factor;
                totalCO2 += co2;
                
                deviceUsageData.computeIfAbsent(entry.getDeviceType(), k -> new ArrayList<>())
                    .add(entry);
            }
            
            @Override
            public void onError(Throwable t) {
                System.err.println("Error in device usage stream: " + t.getMessage());
            }
            
            @Override
            public void onCompleted() {
                EmissionSummary summary = EmissionSummary.newBuilder()
                    .setTotalCo2(totalCO2)
                    .setSuggestions(generateSuggestions(totalCO2, entries))
                    .setTrends(analyzeRecentTrends())
                    .setCarbonOffset(getOffsetRecommendation(totalCO2))
                    .build();
                
                responseObserver.onNext(summary);
                responseObserver.onCompleted();
            }
        };
    }
    
    @Override
    public void getEmissionTrends(TrendRequest request, StreamObserver<TrendResponse> responseObserver) {
        TrendResponse response = TrendResponse.newBuilder()
            .addAllTrends(trends.get())
            .build();
        
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
    
    @Override
    public void setEmissionFactors(FactorRequest request, StreamObserver<FactorResponse> responseObserver) {
        request.getFactorsMap().forEach((source, factor) -> {
            emissionFactors.put(source, factor);
        });
        
        FactorResponse response = FactorResponse.newBuilder()
            .setStatus("Emission factors updated")
            .putAllCurrentFactors(emissionFactors)
            .build();
        
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
    
    private String generateSuggestions(float totalCO2, List<UsageEntry> entries) {
        StringBuilder suggestions = new StringBuilder();
        
        if (totalCO2 > 10) {
            suggestions.append("High emissions detected! ");
            
            Optional<UsageEntry> worstDevice = entries.stream()
                .max(Comparator.comparing(e -> 
                    e.getDurationHours() * e.getPowerRatingKw()));
            
            worstDevice.ifPresent(entry -> {
                suggestions.append(String.format(
                    "Your %s is using %.1f kWh. ", 
                    entry.getDeviceType(),
                    entry.getDurationHours() * entry.getPowerRatingKw()
                ));
            });
            
            suggestions.append("Consider using energy-efficient models or reducing usage time.");
        } else if (totalCO2 > 5) {
            suggestions.append("Moderate emissions. You could optimize device usage times.");
        } else {
            suggestions.append("Good job! Your emissions are low.");
        }
        
        if (entries.stream().anyMatch(e -> getEnergySource(e.getDeviceType()).equals("ELECTRICITY"))) {
            suggestions.append(" Consider switching to renewable energy sources.");
        }
        
        return suggestions.toString();
    }
    
    private String getEnergySource(String deviceType) {
        if (deviceType.toLowerCase().contains("solar") || 
            deviceType.toLowerCase().contains("pv")) {
            return "SOLAR";
        }
        if (deviceType.toLowerCase().contains("gas")) {
            return "GAS";
        }
        return "ELECTRICITY";  // default
    }
    
    private String getOffsetRecommendation(float totalCO2) {
        if (totalCO2 > 20) {
            return "Consider purchasing " + (int)Math.ceil(totalCO2/10) + 
                   " carbon offsets to neutralize your emissions.";
        }
        return "Your carbon footprint is low. Maintain your good practices!";
    }
    
    private void analyzeTrends() {
        List<EmissionTrend> newTrends = new ArrayList<>();
        
        EmissionTrend daily = new EmissionTrend();
        daily.period = "DAY";
        daily.co2 = calculatePeriodCO2(1);
        daily.changePercent = calculateChangePercent(daily.co2, calculatePeriodCO2(2));
        newTrends.add(daily);
        
        EmissionTrend weekly = new EmissionTrend();
        weekly.period = "WEEK";
        weekly.co2 = calculatePeriodCO2(7);
        weekly.changePercent = calculateChangePercent(weekly.co2, calculatePeriodCO2(14));
        newTrends.add(weekly);
        
        trends.set(newTrends);
    }
    
    private float calculatePeriodCO2(int daysBack) {
        long cutoff = System.currentTimeMillis() - (daysBack * 24 * 3600 * 1000L);
        
        return deviceUsageData.values().stream()
            .flatMap(List::stream)
            .filter(entry -> Long.parseLong(entry.getTimestamp()) >= cutoff)
            .mapToFloat(entry -> {
                float factor = emissionFactors.getOrDefault(
                    getEnergySource(entry.getDeviceType()), 
                    0.3f
                );
                return entry.getDurationHours() * entry.getPowerRatingKw() * factor;
            })
            .sum();
    }
    
    private float calculateChangePercent(float current, float previous) {
        if (previous == 0) return 0;
        return ((current - previous) / previous) * 100;
    }
    
    private void persistData() {
        try (ObjectOutputStream out = new ObjectOutputStream(
            new FileOutputStream("emission_data.ser"))) {
            
            out.writeObject(deviceUsageData);
            out.writeObject(emissionFactors);
        } catch (IOException e) {
            System.err.println("Failed to persist data: " + e.getMessage());
        }
    }
    
    @SuppressWarnings("unchecked")
    private void loadPersistedData() {
        File file = new File("emission_data.ser");
        if (file.exists()) {
            try (ObjectInputStream in = new ObjectInputStream(
                new FileInputStream(file))) {
                
                deviceUsageData.putAll((Map<String, List<UsageEntry>>) in.readObject());
                emissionFactors.putAll((Map<String, Float>) in.readObject());
            } catch (IOException | ClassNotFoundException e) {
                System.err.println("Failed to load persisted data: " + e.getMessage());
            }
        }
    }
}
