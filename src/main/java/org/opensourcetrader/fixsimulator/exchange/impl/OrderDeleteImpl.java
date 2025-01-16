package org.opensourcetrader.fixsimulator.exchange.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.opensourcetrader.fixsimulator.exchange.OrderState;

@Getter
@Setter
@AllArgsConstructor
public class OrderDeleteImpl implements OrderState {
    //TODO Change the name to something like OrderStateImpl
    String orderId;
    double leavesQty;
    double qty;
}
