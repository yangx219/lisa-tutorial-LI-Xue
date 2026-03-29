package it.unive.lisa.tutorial;

import it.unive.lisa.analysis.Lattice;
import it.unive.lisa.analysis.ScopeToken;
import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.SemanticOracle;
import it.unive.lisa.analysis.lattices.Satisfiability;
import it.unive.lisa.analysis.value.ValueDomain;
import it.unive.lisa.program.cfg.ProgramPoint;
import it.unive.lisa.symbolic.value.Identifier;
import it.unive.lisa.symbolic.value.ValueExpression;
import it.unive.lisa.symbolic.value.operator.AdditionOperator;
import it.unive.lisa.symbolic.value.operator.MultiplicationOperator;
import it.unive.lisa.symbolic.value.operator.SubtractionOperator;
import it.unive.lisa.symbolic.value.operator.binary.BinaryOperator;
import it.unive.lisa.util.representation.StringRepresentation;
import it.unive.lisa.util.representation.StructuredRepresentation;
import it.unive.lisa.symbolic.value.Constant;
import it.unive.lisa.symbolic.value.BinaryExpression;
import it.unive.lisa.symbolic.SymbolicExpression;
import it.unive.lisa.symbolic.value.operator.binary.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

// ax + by ≤ c
public class TwoVarLinearInequality implements ValueDomain<TwoVarLinearInequality>{
    public static final TwoVarLinearInequality TOP = new TwoVarLinearInequality(Collections.emptySet());// TOP = {}
    public static final TwoVarLinearInequality BOTTOM =
            new TwoVarLinearInequality(Collections.singleton(new Inequality(0, null, 0, null, -1)));
    private final Set<Inequality> inequalities;
    //Constructor
    public TwoVarLinearInequality() {
        this.inequalities = new HashSet<>();
    }
    public TwoVarLinearInequality(Set<Inequality> inequalities) {
        this.inequalities = new HashSet<>(inequalities);
    }

    @Override
    public TwoVarLinearInequality top() {
        return TOP;
    }
    @Override
    public TwoVarLinearInequality bottom() {
        return BOTTOM;
    }
    @Override
    public boolean isTop() {
        return inequalities.isEmpty();
    }
    @Override
    public boolean isBottom() {
        return inequalities.size() == 1
                && inequalities.iterator().next().isUnsatisfiable();
    }


    @Override
    public boolean lessOrEqual(TwoVarLinearInequality twoVarLinearInequality) throws SemanticException {
        return false;
    }

    @Override
    public TwoVarLinearInequality lub(TwoVarLinearInequality twoVarLinearInequality) throws SemanticException {
        return null;
    }

    /**
     * - x = c
     * - x = y
     * - x = y + c
     * - x = y - c
     * - x = a*y
     * - x = a*y + c
     * - x = a*y - c
     ***/
    @Override
    public TwoVarLinearInequality assign(Identifier identifier, ValueExpression valueExpression, ProgramPoint programPoint, SemanticOracle semanticOracle) throws SemanticException {
        if (isBottom())
            return this;

        TwoVarLinearInequality cleaned = forgetIdentifier(identifier);
        Set<Inequality> newSet = new HashSet<>(cleaned.inequalities);

        // Case 1: x = c
        if (valueExpression instanceof Constant) {
            Constant constant = (Constant) valueExpression;

            if (constant.getValue() instanceof Integer) {
                int value = (Integer) constant.getValue();
                newSet.add(new Inequality(1, identifier, 0, null, value));
                newSet.add(new Inequality(-1, identifier, 0, null, -value));
            }

            return new TwoVarLinearInequality(sanitize(newSet));
        }

        // Case 2: x = y
        if (valueExpression instanceof Identifier) {
            Identifier other = (Identifier) valueExpression;
            //   x - y <= 0
            newSet.add(new Inequality(1, identifier, -1, other, 0));
            //   -x + y <= 0
            newSet.add(new Inequality(-1, identifier, 1, other, 0));

            return new TwoVarLinearInequality(sanitize(newSet));
        }

        // Binary-expression cases
        if (valueExpression instanceof BinaryExpression) {
            BinaryExpression binary = (BinaryExpression) valueExpression;
            BinaryOperator operator = binary.getOperator();

            if (binary.getLeft() instanceof Identifier && binary.getRight() instanceof Constant) {
                Identifier other = (Identifier) binary.getLeft();
                Constant constant = (Constant) binary.getRight();

                if (constant.getValue() instanceof Integer) {
                    int value = (Integer) constant.getValue();

                    // Case 3: x = y + c
                    if (operator instanceof AdditionOperator) {
                        // x - y <= c
                        newSet.add(new Inequality(1, identifier, -1, other, value));
                        // -x + y <= -c
                        newSet.add(new Inequality(-1, identifier, 1, other, -value));

                        return new TwoVarLinearInequality(sanitize(newSet));
                    }

                    // Case 4: x = y - c
                    if (operator instanceof SubtractionOperator) {
                        // x - y <= -c
                        newSet.add(new Inequality(1, identifier, -1, other, -value));
                        // -x + y <= c
                        newSet.add(new Inequality(-1, identifier, 1, other, value));

                        return new TwoVarLinearInequality(sanitize(newSet));
                    }
                }
            }

            // Case 5: x = a*y
            if (operator instanceof MultiplicationOperator && binary.getLeft() instanceof Constant && binary.getRight() instanceof Identifier) {
                Constant coeffConst = (Constant) binary.getLeft();
                Identifier other = (Identifier) binary.getRight();

                if (coeffConst.getValue() instanceof Integer) {
                    int coeff = (Integer) coeffConst.getValue();

                    newSet.add(new Inequality(1, identifier, -coeff, other, 0));
                    newSet.add(new Inequality(-1, identifier, coeff, other, 0));

                    return new TwoVarLinearInequality(sanitize(newSet));
                }
            }

            // Case 6: x = a*y + c
            // Case 7: x = a*y - c
            if ((operator instanceof AdditionOperator || operator instanceof SubtractionOperator)
                    && binary.getLeft() instanceof BinaryExpression
                    && binary.getRight() instanceof Constant) {

                BinaryExpression leftExpr = (BinaryExpression) binary.getLeft();
                Constant constant = (Constant) binary.getRight();

                if (leftExpr.getOperator() instanceof MultiplicationOperator
                        && leftExpr.getLeft() instanceof Constant
                        && leftExpr.getRight() instanceof Identifier
                        && constant.getValue() instanceof Integer) {

                    Constant coeffConst = (Constant) leftExpr.getLeft();
                    Identifier other = (Identifier) leftExpr.getRight();

                    if (coeffConst.getValue() instanceof Integer) {
                        int coeff = (Integer) coeffConst.getValue();
                        int value = (Integer) constant.getValue();

                        // x = a*y + c
                        if (operator instanceof AdditionOperator) {
                            // x - a*y <= c
                            newSet.add(new Inequality(1, identifier, -coeff, other, value));
                            // -x + a*y <= -c
                            newSet.add(new Inequality(-1, identifier, coeff, other, -value));

                            return new TwoVarLinearInequality(sanitize(newSet));
                        }

                        // x = a*y - c
                        if (operator instanceof SubtractionOperator) {
                            // x - a*y <= -c
                            newSet.add(new Inequality(1, identifier, -coeff, other, -value));
                            // -x + a*y <= c
                            newSet.add(new Inequality(-1, identifier, coeff, other, value));

                            return new TwoVarLinearInequality(sanitize(newSet));
                        }
                    }
                }
            }
        }

        // Unsupported expressions are handled conservatively:
        // forget the assigned variable and keep all remaining constraints.
        return new TwoVarLinearInequality(sanitize(newSet));
    }

    @Override
    public TwoVarLinearInequality smallStepSemantics(ValueExpression valueExpression, ProgramPoint programPoint, SemanticOracle semanticOracle) throws SemanticException {
        return this;
    }
    /*
     * - x <= y
     * - x >= y
     * - x == y
     * - x <= c
     * - x >= c
     * - x == c
     */
    @Override
    public TwoVarLinearInequality assume(ValueExpression valueExpression, ProgramPoint programPoint, ProgramPoint programPoint1, SemanticOracle semanticOracle) throws SemanticException {
        if (isBottom())
            return this;

        if (!(valueExpression instanceof BinaryExpression))
            return this;

        BinaryExpression binary = (BinaryExpression) valueExpression;
        BinaryOperator operator = binary.getOperator();
        SymbolicExpression left = binary.getLeft();
        SymbolicExpression right = binary.getRight();

        Set<Inequality> newSet = new HashSet<>(inequalities);

        // Case 1: comparisons between two identifiers
        if (left instanceof Identifier && right instanceof Identifier) {
            Identifier x = (Identifier) left;
            Identifier y = (Identifier) right;

            if (operator instanceof ComparisonLe || operator instanceof ComparisonLt) {
                // x - y <= 0
                newSet.add(new Inequality(1, x, -1, y, 0));
                return new TwoVarLinearInequality(sanitize(newSet));
            }

            if (operator instanceof ComparisonGe || operator instanceof ComparisonGt) {
                // y - x <= 0
                newSet.add(new Inequality(1, y, -1, x, 0));
                return new TwoVarLinearInequality(sanitize(newSet));
            }

            if (operator instanceof ComparisonEq) {
                // x - y <= 0
                newSet.add(new Inequality(1, x, -1, y, 0));
                // y - x <= 0
                newSet.add(new Inequality(1, y, -1, x, 0));
                return new TwoVarLinearInequality(sanitize(newSet));
            }
        }

        // Case 2: comparisons between an identifier and a constant
        if (left instanceof Identifier && right instanceof Constant) {
            Identifier x = (Identifier) left;
            Constant constant = (Constant) right;

            if (constant.getValue() instanceof Integer) {
                int value = (Integer) constant.getValue();

                if (operator instanceof ComparisonLe || operator instanceof ComparisonLt) {
                    // x <= c
                    newSet.add(new Inequality(1, x, 0, null, value));
                    return new TwoVarLinearInequality(sanitize(newSet));
                }

                if (operator instanceof ComparisonGe || operator instanceof ComparisonGt) {
                    // -x <= -c
                    newSet.add(new Inequality(-1, x, 0, null, -value));
                    return new TwoVarLinearInequality(sanitize(newSet));
                }

                if (operator instanceof ComparisonEq) {
                    // x <= c
                    newSet.add(new Inequality(1, x, 0, null, value));
                    // -x <= -c
                    newSet.add(new Inequality(-1, x, 0, null, -value));
                    return new TwoVarLinearInequality(sanitize(newSet));
                }
            }
        }

        return this;
    }

    @Override
    public boolean knowsIdentifier(Identifier identifier) {
        for (Inequality ineq : inequalities) {
            if (ineq.involves(identifier))
                return true;
        }
        return false;
    }

    @Override
    public TwoVarLinearInequality forgetIdentifier(Identifier identifier) throws SemanticException {
        if (isTop() || isBottom())
            return this;

        Set<Inequality> updated = removeInvolving(inequalities, identifier);
        updated = sanitize(updated);
        return new TwoVarLinearInequality(updated);
    }

    @Override
    public TwoVarLinearInequality forgetIdentifiersIf(Predicate<Identifier> predicate) throws SemanticException {
        if (isTop() || isBottom())
            return this;

        Set<Identifier> toForget = new HashSet<>();
        for (Inequality ineq : inequalities) {
            toForget.addAll(ineq.variables());
        }

        TwoVarLinearInequality result = this;
        for (Identifier id : toForget) {
            if (predicate.test(id))
                result = result.forgetIdentifier(id);
        }

        return result;
    }

    @Override
    public Satisfiability satisfies(ValueExpression valueExpression, ProgramPoint programPoint, SemanticOracle semanticOracle) throws SemanticException {
        return null;
    }

    @Override
    public TwoVarLinearInequality pushScope(ScopeToken scopeToken) throws SemanticException {
        return null;
    }

    @Override
    public TwoVarLinearInequality popScope(ScopeToken scopeToken) throws SemanticException {
        return null;
    }

    @Override
    public StructuredRepresentation representation() {
        if (isTop())
            return Lattice.topRepresentation();

        if (isBottom())
            return Lattice.bottomRepresentation();

        return new StringRepresentation(inequalities.toString());
    }
    //helper and debug
    @Override
    public String toString() {
        return representation().toString();
    }

    //removes trivial inequalities from the set
    private Set<Inequality> removeTrivial(Set<Inequality> set) {
        Set<Inequality> result = new HashSet<>();
        for (Inequality ineq : set) {
            if (!ineq.isTrivial())
                result.add(ineq);
        }
        return result;
    }
    //Keeps only the tightest inequalities for each left-hand side
    private Set<Inequality> tighten(Set<Inequality> set) {
        Set<Inequality> result = new HashSet<>();

        for (Inequality current : set) {
            boolean shouldAdd = true;
            Set<Inequality> toRemove = new HashSet<>();

            for (Inequality existing : result) {
                if (current.sameLeftPart(existing)) {
                    if (current.getC() <= existing.getC()) {
                        // current is stronger → remove existing
                        toRemove.add(existing);
                    } else {
                        // existing is stronger → skip current
                        shouldAdd = false;
                    }
                }
            }
            result.removeAll(toRemove);
            if (shouldAdd)
                result.add(current);
        }

        return result;
    }

    /**
     * cleans a set of inequalities by:
     * 1. Checking for inconsistency (unsatisfiable constraint)
     * 2. Removing trivial constraints
     * 3. Keeping only the tightest constraints
     */
    private Set<Inequality> sanitize(Set<Inequality> set) {
        for (Inequality ineq : set) {
            if (ineq.isUnsatisfiable()) {
                Set<Inequality> bottomSet = new HashSet<>();
                bottomSet.add(new Inequality(0, null, 0, null, -1));
                return bottomSet;
            }
        }

        Set<Inequality> cleaned = removeTrivial(set);
        cleaned = tighten(cleaned);
        return cleaned;
    }
    // removes all inequalities that involve a given identifier
    private Set<Inequality> removeInvolving(Set<Inequality> set, Identifier id) {
        Set<Inequality> result = new HashSet<>();
        for (Inequality ineq : set) {
            if (!ineq.involves(id))
                result.add(ineq);
        }
        return result;
    }



    public static class Inequality {
        private final int a;
        private final Identifier x;
        private final int b;
        private final Identifier y;
        private final int c;

        public Inequality(int a, Identifier x, int b, Identifier y, int c) {
            this.a = a;
            this.x = x;
            this.b = b;
            this.y = y;
            this.c = c;
        }

        public int getA() {
            return a;
        }
        public Identifier getX() {
            return x;
        }
        public int getB() {
            return b;
        }
        public Identifier getY() {
            return y;
        }
        public int getC() {
            return c;
        }
        //checks whether this inequality contains the given identifier
        public boolean involves(Identifier id) {
            return (x != null && x.equals(id)) || (y != null && y.equals(id));
        }
        //checks  0 <= +
        public boolean isTrivial() {
            return a == 0 && b == 0 && c >= 0;
        }
        //checks 0>-
        public boolean isUnsatisfiable() {
            return a == 0 && b == 0 && c < 0;
        }

        public boolean sameLeftPart(Inequality other) {
            return a == other.a
                    && b == other.b
                    && Objects.equals(x, other.x)
                    && Objects.equals(y, other.y);
        }
        //to remove redundant constraints
        public boolean entails(Inequality other) {
            return sameLeftPart(other) && c <= other.c;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (!(obj instanceof Inequality))
                return false;

            Inequality other = (Inequality) obj;
            return a == other.a
                    && b == other.b
                    && c == other.c
                    && Objects.equals(x, other.x)
                    && Objects.equals(y, other.y);
        }
        //set of variables that appear in this inequality
        public Set<Identifier> variables() {
            Set<Identifier> vars = new HashSet<>();
            if (x != null && a != 0)
                vars.add(x);
            if (y != null && b != 0)
                vars.add(y);
            return vars;
        }

        @Override
        public int hashCode() {
            return Objects.hash(a, x, b, y, c);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();

            boolean hasLeft = false;

            if (x != null && a != 0) {
                if (a == 1)
                    sb.append(x.getName());
                else if (a == -1)
                    sb.append("-").append(x.getName());
                else
                    sb.append(a).append("*").append(x.getName());
                hasLeft = true;
            }

            if (y != null && b != 0) {
                if (hasLeft) {
                    if (b > 0)
                        sb.append(" + ");
                    else
                        sb.append(" - ");
                } else if (b < 0) {
                    sb.append("-");
                }

                int absB = Math.abs(b);
                if (absB == 1)
                    sb.append(y.getName());
                else
                    sb.append(absB).append("*").append(y.getName());
            }

            if (!hasLeft && (y == null || b == 0))
                sb.append("0");

            sb.append(" <= ").append(c);
            return sb.toString();
        }
    }
}
