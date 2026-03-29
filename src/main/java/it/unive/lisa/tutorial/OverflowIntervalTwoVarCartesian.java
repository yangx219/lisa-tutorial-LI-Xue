package it.unive.lisa.tutorial;

import it.unive.lisa.analysis.combination.CartesianProduct;
import it.unive.lisa.analysis.nonrelational.value.ValueEnvironment;
import it.unive.lisa.analysis.value.ValueDomain;
import it.unive.lisa.symbolic.value.Identifier;
import it.unive.lisa.symbolic.value.ValueExpression;
import it.unive.lisa.util.representation.StringRepresentation;
import it.unive.lisa.util.representation.StructuredRepresentation;

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
        return new OverflowIntervalTwoVarCartesian(left, right);
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