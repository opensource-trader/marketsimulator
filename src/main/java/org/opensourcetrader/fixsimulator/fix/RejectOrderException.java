package org.opensourcetrader.fixsimulator.fix;

public class RejectOrderException extends Exception {
    public RejectOrderException(String message) {
        super(message);
    }
}
