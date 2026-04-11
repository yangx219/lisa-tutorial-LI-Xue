package it.unive.lisa.tutorial;

import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.combination.CartesianProduct;
import it.unive.lisa.analysis.nonrelational.value.ValueEnvironment;
import it.unive.lisa.analysis.value.ValueDomain;
import it.unive.lisa.symbolic.value.Identifier;
import it.unive.lisa.symbolic.value.ValueExpression;
import it.unive.lisa.util.numeric.MathNumber;
import it.unive.lisa.util.numeric.MathNumberConversionException;
import it.unive.lisa.util.representation.StringRepresentation;
import it.unive.lisa.util.representation.StructuredRepresentation;

import java.util.HashSet;
import java.util.Set;

public class OverflowIntervalTwoVarCartesian
        extends CartesianProduct<
        OverflowIntervalTwoVarCartesian,
        ValueEnvironment<OverflowInterval>,
        TwoVarLinearInequality,
        ValueExpression,
        Identifier>
        implements ValueDomain<OverflowIntervalTwoVarCartesian> {

    public OverflowIntervalTwoVarCartesian() {
        this(new ValueEnvironment<>(new OverflowInterval()), new TwoVarLinearInequality());
    }

    public OverflowIntervalTwoVarCartesian(
            ValueEnvironment<OverflowInterval> left,
            TwoVarLinearInequality right) {
        super(left, right);
    }

    @Override
    public boolean knowsIdentifier(Identifier identifier) {
        return left.knowsIdentifier(identifier) || right.knowsIdentifier(identifier);
    }


    @Override
    public OverflowIntervalTwoVarCartesian mk(
            ValueEnvironment<OverflowInterval> left,
            TwoVarLinearInequality right) {
        OverflowIntervalTwoVarCartesian res =
                new OverflowIntervalTwoVarCartesian(left, right);
        try {
            return res.reduce();
        } catch (MathNumberConversionException | SemanticException e) {
            // Fallback to the unreduced product if reduction fails
            return res;
        }
    }
    private OverflowIntervalTwoVarCartesian reduce() throws MathNumberConversionException, SemanticException {
        ValueEnvironment<OverflowInterval> newLeft = this.left;
        TwoVarLinearInequality newRight = this.right;

        // 1. interval -> inequalities
        newRight = reduceIntervalsToInequalities(newLeft, newRight);

        // 2. inequalities -> interval
        newLeft = reduceInequalitiesToIntervals(newLeft, newRight);
        return new OverflowIntervalTwoVarCartesian(newLeft, newRight);
    }

    private boolean isUserVariable(Identifier id) {
        String name = id.getName();
        return !name.contains("@") && !name.contains("pp") && !name.equals("this");
    }
    /**
     * Propagate interval facts to the relational domain.
     *
     * Rules used:
     * 1) x in [l, h]  =>  x <= h   and   x >= l
     * 2) if upper(x) < lower(y), then x < y
     */
    private TwoVarLinearInequality reduceIntervalsToInequalities(
            ValueEnvironment<OverflowInterval> intervals,
            TwoVarLinearInequality inequalities) throws MathNumberConversionException {

        TwoVarLinearInequality result = inequalities;

        Set<Identifier> ids = new HashSet<>();
        for (Identifier id : intervals.getKeys()) {
            if (isUserVariable(id))
                ids.add(id);
        }

        // Unary bounds: x in [l, h]  =>  x <= h  and  -x <= -l
        for (Identifier id : ids) {
            OverflowInterval itv = intervals.getState(id);

            if (itv.isTop() || itv.isBottom())
                continue;

            MathNumber low = itv.interval.getLow();
            MathNumber high = itv.interval.getHigh();

            if (!low.isNaN() && !high.isNaN()) {
                int l = low.toInt();
                int h = high.toInt();

                // Add x <= h only if informative
                if (h != Integer.MAX_VALUE) {
                    result = result.addConstraint(
                            new TwoVarLinearInequality.Inequality(1, id, 0, null, h));
                }

                // Add x >= l as -x <= -l only if informative
                if (l != Integer.MIN_VALUE) {
                    result = result.addConstraint(
                            new TwoVarLinearInequality.Inequality(-1, id, 0, null, -l));
                }
            }
        }

        // Pairwise strict ordering:
        // if upper(x) < lower(y), then x - y <= -1
        for (Identifier x : ids) {
            OverflowInterval ix = intervals.getState(x);
            if (ix.isTop() || ix.isBottom())
                continue;

            int ux = ix.interval.getHigh().toInt();

            for (Identifier y : ids) {
                if (x.equals(y))
                    continue;

                OverflowInterval iy = intervals.getState(y);
                if (iy.isTop() || iy.isBottom())
                    continue;

                int ly = iy.interval.getLow().toInt();

                if (ux < ly) {
                    result = result.addConstraint(
                            new TwoVarLinearInequality.Inequality(1, x, -1, y, -1));
                }
            }
        }

        return result;
    }
    private ValueEnvironment<OverflowInterval> reduceInequalitiesToIntervals(
            ValueEnvironment<OverflowInterval> intervals,
            TwoVarLinearInequality inequalities) throws SemanticException {

        ValueEnvironment<OverflowInterval> result = intervals;

        for (TwoVarLinearInequality.Inequality ineq : inequalities.getConstraints()) {

            if (ineq.getX() != null && !isUserVariable(ineq.getX()))
                continue;

            // case 1: x <= c
            if (ineq.getX() != null
                    && ineq.getY() == null
                    && ineq.getA() == 1
                    && ineq.getB() == 0) {

                Identifier x = ineq.getX();
                int c = ineq.getC();

                OverflowInterval current = result.getState(x);
                if (!current.isBottom()) {

                    OverflowInterval bound =
                            new OverflowInterval(OverflowInterval.MIN_VAL, new MathNumber(c));

                    result = result.putState(x, current.glb(bound));
                }
            }

            // case 2: x >= c   (i.e. -x <= -c)
            if (ineq.getX() != null
                    && ineq.getY() == null
                    && ineq.getA() == -1
                    && ineq.getB() == 0) {

                Identifier x = ineq.getX();
                int c = -ineq.getC();

                OverflowInterval current = result.getState(x);
                if (!current.isBottom()) {

                    OverflowInterval bound =
                            new OverflowInterval(new MathNumber(c), OverflowInterval.MAX_VAL);

                    result = result.putState(x, current.glb(bound));
                }
            }
        }

        return result;
    }

    @Override
    public StructuredRepresentation representation() {
        if (isTop() || isBottom())
            return super.representation();

        StringBuilder sb = new StringBuilder();
        sb.append("OverflowIntervalTwoVarCartesian {\n");

        sb.append("  Overflow intervals: ");
        sb.append(left.representation().toString());
        sb.append("\n\n");

        sb.append("  Two-variable linear inequalities: ");
        sb.append(right.representation().toString());
        sb.append("\n");

        sb.append("}");
        return new StringRepresentation(sb.toString());
    }
}