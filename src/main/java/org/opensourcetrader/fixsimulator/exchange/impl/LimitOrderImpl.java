package org.opensourcetrader.fixsimulator.exchange.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.opensourcetrader.fixsimulator.exchange.Order;
import org.opensourcetrader.fixsimulator.exchange.Side;

import java.math.BigDecimal;
import java.util.Objects;

@Getter @Setter
public class LimitOrderImpl implements Order {

    private double quantity;
    private double remainingQty;
    private BigDecimal price;
    private String clOrdId;
    private Side side;
    private String orderId;

    LimitOrderImpl(double quantity, BigDecimal price,
                   String clOrdId, Side side, String orderId){
        this.quantity = quantity;
        this.price = price;
        this.clOrdId = clOrdId;
        this.side = side;
        this.orderId = orderId;
        this.remainingQty = quantity;
    }

    @Override
    public boolean equals(Object o){
        if (this == o) {
            return true;
        }
        if (o ==null|| getClass() != o.getClass()){
            return  false;
        }

        LimitOrderImpl that = (LimitOrderImpl) o;
        return  orderId.equals(that.getOrderId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId);
    }


}
