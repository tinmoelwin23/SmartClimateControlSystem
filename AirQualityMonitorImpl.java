public class AirQualityMonitorImpl extends AirQualityMonitorGrpc.AirQualityMonitorImplBase {
    @Override
    public void streamAirQuality(SensorRequest request, StreamObserver<AirQualityReading> responseObserver) {
        try {
            validateSensorRequest(request);
            
            
            Context.current().addListener(
                new CancellationListener(responseObserver),
                Executors.newSingleThreadExecutor());
            
            for (int i = 0; i < 30; i++) {
                if (Context.current().isCancelled()) {
                    logger.info("Stream cancelled by client");
                    return;
                }
                
                try {
                    AirQualityReading reading = generateReading(request);
                    responseObserver.onNext(reading);
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    responseObserver.onError(
                        Status.CANCELLED
                            .withDescription("Stream interrupted")
                            .withCause(e)
                            .asRuntimeException());
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(GrpcErrorHandler.handleException(e).asRuntimeException());
        }
    }
    
    private static class CancellationListener implements Context.CancellationListener {
        private final StreamObserver<?> responseObserver;
        
        CancellationListener(StreamObserver<?> responseObserver) {
            this.responseObserver = responseObserver;
        }
        
        @Override
        public void cancelled(Context context) {
            logger.info("Client cancelled the request");
            responseObserver.onError(
                Status.CANCELLED
                    .withDescription("Request cancelled by client")
                    .asRuntimeException());
        }
    }
}
