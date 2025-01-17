package org.opensourcetrader.marketsimulator.fix;

public class RejectOrderException extends Exception {
    public RejectOrderException(String message) {
        super(message);
    }
}
