package org.opensourcetrader.marketsimulator.marketdataserver;

import io.grpc.*;

public class MdAuthInterceptor implements ServerInterceptor {

    static final String SubscriberIdKey = "subscriber_id";
    public static final Context.Key<String> SUBSCRIBER_ID
            = Context.key(SubscriberIdKey);


    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> serverCall,
            Metadata metadata, ServerCallHandler<ReqT, RespT> serverCallHandler) {
        var key = Metadata.Key.of(SubscriberIdKey, new Metadata.AsciiMarshaller<String>() {
            @Override
            public String toAsciiString(String s) {
                return s;
            }

            @Override
            public String parseAsciiString(String s) {
                return s;
            }
        });

        var value = metadata.get(key);

        if (value == null) {
            serverCall.close(Status.UNAUTHENTICATED.withDescription("Subscriber ID is required"), new Metadata());
        }

        Context context = Context.current().withValue(SUBSCRIBER_ID, value);
        return Contexts.interceptCall(context,serverCall,metadata,serverCallHandler);
    }
}
