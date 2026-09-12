package dev.shop.common.event;
import java.math.BigDecimal;
import java.util.*;
public record SagaData(UUID userId, List<Line> lines, BigDecimal total, PaymentMode paymentMode, String reason) {
    public enum PaymentMode { SUCCESS, DECLINE, CANCEL }
    public record Line(UUID productId, int quantity, BigDecimal unitPrice) {
        public Line { if(productId==null || quantity<1 || quantity>1000 || unitPrice==null || unitPrice.signum()<=0) throw new IllegalArgumentException("Invalid order line"); }
    }
    public SagaData {
        if(userId==null || lines==null || lines.isEmpty() || lines.size()>20 || total==null || total.signum()<=0 || paymentMode==null) throw new IllegalArgumentException("Invalid Saga data");
        lines=List.copyOf(lines);
        if(lines.stream().map(Line::productId).distinct().count()!=lines.size()) throw new IllegalArgumentException("Duplicate product lines");
        BigDecimal computed=lines.stream().map(l->l.unitPrice().multiply(BigDecimal.valueOf(l.quantity()))).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(computed.compareTo(total)!=0) throw new IllegalArgumentException("Incorrect order total");
    }
    public SagaData withReason(String value) { return new SagaData(userId,lines,total,paymentMode,value); }
}
