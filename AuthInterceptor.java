public class AuthInterceptor implements ServerInterceptor {
    private static final Metadata.Key<String> AUTH_HEADER = 
        Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
        ServerCall<ReqT, RespT> call,
        Metadata headers,
        ServerCallHandler<ReqT, RespT> next) {
        
        String token = headers.get(AUTH_HEADER);
        if (!isValidToken(token)) {
            call.close(Status.UNAUTHENTICATED.withDescription("Invalid auth token"),
                new Metadata());
            return new ServerCall.Listener<>() {};
        }
        
        Context context = Context.current()
            .withValue(Context.key("user"), extractUserFromToken(token));
        
        return Contexts.interceptCall(context, call, headers, next);
    }
}
