public class ClimateControlClient {
    private void startAirQualityMonitoring() {
        SensorRequest request = SensorRequest.newBuilder()
            .setSensorId("sensor-1")
            .setLocation("Living Room")
            .build();
        
        
        Context.CancellableContext cancellableContext = Context.current().withCancellation();
        StreamObserver<AirQualityReading> responseObserver = new StreamObserver<>() {
            @Override
            public void onNext(AirQualityReading reading) {
                
            }
            
            @Override
            public void onError(Throwable t) {
                Status status = Status.fromThrowable(t);
                if (status.getCode() == Status.Code.CANCELLED) {
                    logger.info("Stream was cancelled");
                } else {
                    logger.error("Air quality stream error: " + status);
                }
            }
            
            @Override
            public void onCompleted() {
                logger.info("Air quality stream completed");
            }
        };
        
        cancellableContext.run(() -> 
            airQualityStub.streamAirQuality(request, responseObserver));
        
        cancelButton.addActionListener(e -> {
            cancellableContext.cancel(new InterruptedException("User cancelled"));
        });
    }
}
