package it.unive.lisa.tutorial;

import it.unive.lisa.analysis.Lattice;
import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.SemanticOracle;
import it.unive.lisa.analysis.lattices.Satisfiability;
import it.unive.lisa.analysis.nonrelational.value.BaseNonRelationalValueDomain;
import it.unive.lisa.analysis.nonrelational.value.ValueEnvironment;
import it.unive.lisa.program.cfg.ProgramPoint;
import it.unive.lisa.symbolic.value.Constant;
import it.unive.lisa.symbolic.value.Identifier;
import it.unive.lisa.symbolic.value.ValueExpression;
import it.unive.lisa.symbolic.value.operator.AdditionOperator;
import it.unive.lisa.symbolic.value.operator.DivisionOperator;
import it.unive.lisa.symbolic.value.operator.MultiplicationOperator;
import it.unive.lisa.symbolic.value.operator.SubtractionOperator;
import it.unive.lisa.symbolic.value.operator.binary.*;
import it.unive.lisa.symbolic.value.operator.unary.NumericNegation;
import it.unive.lisa.symbolic.value.operator.unary.UnaryOperator;
import it.unive.lisa.util.numeric.IntInterval;
import it.unive.lisa.util.numeric.MathNumber;
import it.unive.lisa.util.representation.StringRepresentation;
import it.unive.lisa.util.representation.StructuredRepresentation;

import java.util.Objects;

/**
 * Interval domain with 32-bit signed integer overflow semantics.
 */
public class OverflowInterval
		implements BaseNonRelationalValueDomain<OverflowInterval> {

	public static final MathNumber MIN_VAL = new MathNumber(Integer.MIN_VALUE);
	public static final MathNumber MAX_VAL = new MathNumber(Integer.MAX_VALUE);

	public static final OverflowInterval TOP = new OverflowInterval(MIN_VAL, MAX_VAL);
	public static final OverflowInterval BOTTOM = new OverflowInterval(MathNumber.NaN, MathNumber.NaN);
	public static final OverflowInterval ZERO = new OverflowInterval(MathNumber.ZERO, MathNumber.ZERO);

	public final IntInterval interval;

	public OverflowInterval(MathNumber low, MathNumber high) {
		this.interval = new IntInterval(low, high);
	}

	public OverflowInterval(IntInterval interval) {
		this.interval = interval;
	}

	public OverflowInterval() {
		this(MIN_VAL, MAX_VAL);
	}

	private OverflowInterval normalize(MathNumber low, MathNumber high) {
		if (low.compareTo(MIN_VAL) >= 0 && high.compareTo(MAX_VAL) <= 0)
			return new OverflowInterval(low, high);
		return TOP;
	}

	@Override
	public OverflowInterval top() {
		return TOP;
	}

	@Override
	public OverflowInterval bottom() {
		return BOTTOM;
	}

	@Override
	public boolean isTop() {
		return !isBottom()
				&& interval.getLow().compareTo(MIN_VAL) == 0
				&& interval.getHigh().compareTo(MAX_VAL) == 0;
	}

	@Override
	public boolean isBottom() {
		return this == BOTTOM || Objects.equals(interval, BOTTOM.interval);
	}

	@Override
	public boolean lessOrEqualAux(OverflowInterval other) throws SemanticException {
		return other.interval.includes(interval);
	}

	@Override
	public OverflowInterval lubAux(OverflowInterval other) throws SemanticException {
		MathNumber newLow = interval.getLow().min(other.interval.getLow());
		MathNumber newHigh = interval.getHigh().max(other.interval.getHigh());
		return normalize(newLow, newHigh);
	}

	@Override
	public OverflowInterval glbAux(OverflowInterval other) {
		MathNumber newLow = interval.getLow().max(other.interval.getLow());
		MathNumber newHigh = interval.getHigh().min(other.interval.getHigh());
		if (newLow.compareTo(newHigh) > 0)
			return bottom();
		return normalize(newLow, newHigh);
	}

	@Override
	public OverflowInterval wideningAux(OverflowInterval other) throws SemanticException {
		// Widen toward machine bounds instead of ±∞.
		MathNumber newLow = other.interval.getLow().compareTo(interval.getLow()) < 0
				? MIN_VAL
				: interval.getLow();
		MathNumber newHigh = other.interval.getHigh().compareTo(interval.getHigh()) > 0
				? MAX_VAL
				: interval.getHigh();
		return normalize(newLow, newHigh);
	}

	@Override
	public StructuredRepresentation representation() {
		if (isBottom())
			return Lattice.bottomRepresentation();
		if (isTop())
			return new StringRepresentation("[MIN, MAX]");
		return new StringRepresentation(interval.toString());
	}

	@Override
	public String toString() {
		return representation().toString();
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(interval);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof OverflowInterval))
			return false;
		return Objects.equals(interval, ((OverflowInterval) o).interval);
	}

	@Override
	public OverflowInterval evalNonNullConstant(Constant constant, ProgramPoint pp, SemanticOracle oracle) {
		return top();
	}

	@Override
	public OverflowInterval evalUnaryExpression(UnaryOperator operator, OverflowInterval arg, ProgramPoint pp, SemanticOracle oracle) {
		return top();
	}

	@Override
	public OverflowInterval evalBinaryExpression(BinaryOperator operator, OverflowInterval left, OverflowInterval right, ProgramPoint pp, SemanticOracle oracle) {
		return top();
	}

	@Override
	public Satisfiability satisfiesBinaryExpression(BinaryOperator operator, OverflowInterval left, OverflowInterval right, ProgramPoint pp, SemanticOracle oracle) {
		return Satisfiability.UNKNOWN;
	}
}
