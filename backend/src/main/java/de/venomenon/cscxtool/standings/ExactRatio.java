package de.venomenon.cscxtool.standings;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.List;

/** Exact arithmetic for statistical ranking; conversion to double is presentation only. */
record ExactRatio(BigInteger numerator, BigInteger denominator) implements Comparable<ExactRatio> {
    ExactRatio {
        if (denominator.signum() <= 0) throw new IllegalArgumentException("Positive denominator required");
        var gcd = numerator.gcd(denominator);
        numerator = numerator.divide(gcd);
        denominator = denominator.divide(gcd);
    }
    static ExactRatio of(long numerator, long denominator) {
        return new ExactRatio(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
    }
    ExactRatio add(ExactRatio other) {
        return new ExactRatio(numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)),
                denominator.multiply(other.denominator));
    }
    static ExactRatio mean(List<ExactRatio> values) {
        if (values.isEmpty()) return null;
        var sum = values.stream().reduce(of(0, 1), ExactRatio::add);
        return new ExactRatio(sum.numerator, sum.denominator.multiply(BigInteger.valueOf(values.size())));
    }
    double value() {
        return new BigDecimal(numerator).divide(new BigDecimal(denominator), MathContext.DECIMAL64).doubleValue();
    }
    @Override public int compareTo(ExactRatio other) {
        return numerator.multiply(other.denominator).compareTo(other.numerator.multiply(denominator));
    }
}
