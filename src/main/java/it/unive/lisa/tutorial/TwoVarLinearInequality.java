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

import java.util.*;
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
    public boolean lessOrEqual(TwoVarLinearInequality other) throws SemanticException {
        if (this.isBottom())
            return true;
        if (other.isTop())
            return true;
        if (this.isTop())
            return other.isTop();
        if (other.isBottom())
            return this.isBottom();

        Set<Inequality> thisClosed = close(this.inequalities);
        Set<Inequality> otherClosed = close(other.inequalities);

        for (Inequality target : otherClosed) {
            boolean found = false;
            for (Inequality mine : thisClosed) {
                if (mine.entails(target)) {
                    found = true;
                    break;
                }
            }

            if (!found)
                return false;
        }

        return true;
    }

    @Override
    public TwoVarLinearInequality lub(TwoVarLinearInequality other) throws SemanticException {
        if (this.isBottom())
            return other;
        if (other.isBottom())
            return this;
        if (this.isTop() || other.isTop())
            return TOP;

        Set<Inequality> result = new HashSet<>();

        for (Inequality ineq1 : this.inequalities) {
            for (Inequality ineq2 : other.inequalities) {

                if (ineq1.sameLeftPart(ineq2)) {
                    // keep the larger c
                    int newC = Math.max(ineq1.getC(), ineq2.getC());

                    result.add(new Inequality(
                            ineq1.getA(),
                            ineq1.getX(),
                            ineq1.getB(),
                            ineq1.getY(),
                            newC
                    ));
                }
            }
        }

        return fromClosedSet(result);
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

            return fromClosedSet(newSet);
        }

        // Case 2: x = y
        if (valueExpression instanceof Identifier) {
            Identifier other = (Identifier) valueExpression;
            //   x - y <= 0
            newSet.add(new Inequality(1, identifier, -1, other, 0));
            //   -x + y <= 0
            newSet.add(new Inequality(-1, identifier, 1, other, 0));

            return fromClosedSet(newSet);
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

                        return fromClosedSet(newSet);
                    }

                    // Case 4: x = y - c
                    if (operator instanceof SubtractionOperator) {
                        // x - y <= -c
                        newSet.add(new Inequality(1, identifier, -1, other, -value));
                        // -x + y <= c
                        newSet.add(new Inequality(-1, identifier, 1, other, value));

                        return fromClosedSet(newSet);
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

                    return fromClosedSet(newSet);
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

                            return fromClosedSet(newSet);
                        }

                        // x = a*y - c
                        if (operator instanceof SubtractionOperator) {
                            // x - a*y <= -c
                            newSet.add(new Inequality(1, identifier, -coeff, other, -value));
                            // -x + a*y <= c
                            newSet.add(new Inequality(-1, identifier, coeff, other, value));

                            return fromClosedSet(newSet);
                        }
                    }
                }
            }
        }

        return fromClosedSet(newSet);
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

            if (operator instanceof ComparisonLe) {
                // x - y <= 0
                newSet.add(new Inequality(1, x, -1, y, 0));
                return fromClosedSet(newSet);
            }

            if (operator instanceof ComparisonLt) {
                // x - y <= -1
                newSet.add(new Inequality(1, x, -1, y, -1));
                return fromClosedSet(newSet);
            }

            if (operator instanceof ComparisonGe) {
                // y - x <= 0
                newSet.add(new Inequality(1, y, -1, x, 0));
                return fromClosedSet(newSet);
            }

            if (operator instanceof ComparisonGt) {
                // y - x <= -1
                newSet.add(new Inequality(1, y, -1, x, -1));
                return fromClosedSet(newSet);
            }

            if (operator instanceof ComparisonEq) {
                // x - y <= 0
                newSet.add(new Inequality(1, x, -1, y, 0));
                // y - x <= 0
                newSet.add(new Inequality(1, y, -1, x, 0));
                return fromClosedSet(newSet);
            }
        }

        // Case 2: comparisons between an identifier and a constant
        if (left instanceof Identifier && right instanceof Constant) {
            Identifier x = (Identifier) left;
            Constant constant = (Constant) right;

            if (constant.getValue() instanceof Integer) {
                int value = (Integer) constant.getValue();

                if (operator instanceof ComparisonLe) {
                    // x <= c
                    newSet.add(new Inequality(1, x, 0, null, value));
                    return fromClosedSet(newSet);
                }

                if (operator instanceof ComparisonLt) {
                    // x <= c - 1
                    newSet.add(new Inequality(1, x, 0, null, value - 1));
                    return fromClosedSet(newSet);
                }

                if (operator instanceof ComparisonGe) {
                    // -x <= -c
                    newSet.add(new Inequality(-1, x, 0, null, -value));
                    return fromClosedSet(newSet);
                }

                if (operator instanceof ComparisonGt) {
                    // -x <= -(c + 1)
                    newSet.add(new Inequality(-1, x, 0, null, -(value + 1)));
                    return fromClosedSet(newSet);
                }

                if (operator instanceof ComparisonEq) {
                    // x <= c
                    newSet.add(new Inequality(1, x, 0, null, value));
                    // -x <= -c
                    newSet.add(new Inequality(-1, x, 0, null, -value));
                    return fromClosedSet(newSet);
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
        if (isBottom())
            return Satisfiability.BOTTOM;

        if (!(valueExpression instanceof BinaryExpression))
            return Satisfiability.UNKNOWN;

        BinaryExpression binary = (BinaryExpression) valueExpression;
        BinaryOperator operator = binary.getOperator();

        if (binary.getLeft() instanceof Identifier && binary.getRight() instanceof Identifier) {
            Identifier x = (Identifier) binary.getLeft();
            Identifier y = (Identifier) binary.getRight();

            // x <= y
            if (operator instanceof ComparisonLe) {
                if (entailsVarLe(x, y))
                    return Satisfiability.SATISFIED;

                // y - x <= -1  =>  x <= y is impossible
                if (containsEntailing(new Inequality(1, y, -1, x, -1)))
                    return Satisfiability.NOT_SATISFIED;

                return Satisfiability.UNKNOWN;
            }

            // x < y   =>   x - y <= -1
            if (operator instanceof ComparisonLt) {
                if (containsEntailing(new Inequality(1, x, -1, y, -1)))
                    return Satisfiability.SATISFIED;

                // y <= x  =>  x < y is impossible
                if (entailsVarLe(y, x))
                    return Satisfiability.NOT_SATISFIED;

                return Satisfiability.UNKNOWN;
            }

            // x >= y
            if (operator instanceof ComparisonGe) {
                if (entailsVarLe(y, x))
                    return Satisfiability.SATISFIED;

                // x - y <= -1  =>  x >= y is impossible
                if (containsEntailing(new Inequality(1, x, -1, y, -1)))
                    return Satisfiability.NOT_SATISFIED;

                return Satisfiability.UNKNOWN;
            }

            // x > y   =>   y - x <= -1
            if (operator instanceof ComparisonGt) {
                if (containsEntailing(new Inequality(1, y, -1, x, -1)))
                    return Satisfiability.SATISFIED;

                // x <= y  =>  x > y is impossible
                if (entailsVarLe(x, y))
                    return Satisfiability.NOT_SATISFIED;

                return Satisfiability.UNKNOWN;
            }

            // x == y
            if (operator instanceof ComparisonEq) {
                boolean xy = entailsVarLe(x, y);
                boolean yx = entailsVarLe(y, x);

                if (xy && yx)
                    return Satisfiability.SATISFIED;

                if (containsEntailing(new Inequality(1, x, -1, y, -1))
                        || containsEntailing(new Inequality(1, y, -1, x, -1)))
                    return Satisfiability.NOT_SATISFIED;

                return Satisfiability.UNKNOWN;
            }
        }

        if (binary.getLeft() instanceof Identifier && binary.getRight() instanceof Constant) {
            Identifier x = (Identifier) binary.getLeft();
            Constant constant = (Constant) binary.getRight();

            if (!(constant.getValue() instanceof Integer))
                return Satisfiability.UNKNOWN;

            int value = (Integer) constant.getValue();

            // x <= c
            if (operator instanceof ComparisonLe) {
                if (entailsUpperBound(x, value))
                    return Satisfiability.SATISFIED;

                // x >= value + 1  =>  x <= value is impossible
                if (entailsLowerBound(x, value + 1))
                    return Satisfiability.NOT_SATISFIED;

                return Satisfiability.UNKNOWN;
            }

            // x < c   =>   x <= c - 1
            if (operator instanceof ComparisonLt) {
                if (entailsUpperBound(x, value - 1))
                    return Satisfiability.SATISFIED;

                // x >= c  =>  x < c is impossible
                if (entailsLowerBound(x, value))
                    return Satisfiability.NOT_SATISFIED;

                return Satisfiability.UNKNOWN;
            }

            // x >= c
            if (operator instanceof ComparisonGe) {
                if (entailsLowerBound(x, value))
                    return Satisfiability.SATISFIED;

                // x <= value - 1  =>  x >= value is impossible
                if (entailsUpperBound(x, value - 1))
                    return Satisfiability.NOT_SATISFIED;

                return Satisfiability.UNKNOWN;
            }

            // x > c   =>   x >= c + 1
            if (operator instanceof ComparisonGt) {
                if (entailsLowerBound(x, value + 1))
                    return Satisfiability.SATISFIED;

                // x <= c  =>  x > c is impossible
                if (entailsUpperBound(x, value))
                    return Satisfiability.NOT_SATISFIED;

                return Satisfiability.UNKNOWN;
            }

            // x == c
            if (operator instanceof ComparisonEq) {
                boolean upper = entailsUpperBound(x, value);
                boolean lower = entailsLowerBound(x, value);

                if (upper && lower)
                    return Satisfiability.SATISFIED;

                if (entailsUpperBound(x, value - 1) || entailsLowerBound(x, value + 1))
                    return Satisfiability.NOT_SATISFIED;

                return Satisfiability.UNKNOWN;
            }
        }

        return Satisfiability.UNKNOWN;
    }

    @Override
    public TwoVarLinearInequality pushScope(ScopeToken scopeToken) throws SemanticException {
        return this;
    }

    @Override
    public TwoVarLinearInequality popScope(ScopeToken scopeToken) throws SemanticException {
        return this;
    }

    @Override
    public StructuredRepresentation representation() {
        if (isTop())
            return Lattice.topRepresentation();

        if (isBottom())
            return Lattice.bottomRepresentation();

        Set<Inequality> closed = close(inequalities);

        StringBuilder sb = new StringBuilder();
        sb.append("TwoVarLinearInequality{\n");

        Set<Identifier> userVars = new TreeSet<>(Comparator.comparing(Identifier::getName));
        for (Inequality ineq : closed) {
            if (ineq.getX() != null && isUserVariable(ineq.getX()))
                userVars.add(ineq.getX());
            if (ineq.getY() != null && isUserVariable(ineq.getY()))
                userVars.add(ineq.getY());
        }

        for (Identifier id : userVars) {
            sb.append("  ").append(id.getName()).append(" -> {\n");

            Set<Inequality> related = new TreeSet<>(Comparator.comparing(Inequality::toString));
            for (Inequality ineq : closed) {
                boolean show = true;

                if (ineq.getX() != null && !isUserVariable(ineq.getX()))
                    show = false;
                if (ineq.getY() != null && !isUserVariable(ineq.getY()))
                    show = false;

                if (show && ineq.involves(id))
                    related.add(ineq);
            }

            for (Inequality ineq : related) {
                sb.append("    ").append(ineq.toString()).append(",\n");
            }

            sb.append("  }\n");
        }

        sb.append("}");
        return new StringRepresentation(sb.toString());
    }

    private boolean isUserVariable(Identifier id) {
        String name = id.getName();
        return !name.contains("@") && !name.contains("pp") && !name.equals("this");
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
        Set<Inequality> normalized = new HashSet<>();
        for (Inequality ineq : set)
            normalized.add(ineq.normalize());

        for (Inequality ineq : normalized) {
            if (ineq.isUnsatisfiable()) {
                Set<Inequality> bottomSet = new HashSet<>();
                bottomSet.add(new Inequality(0, null, 0, null, -1));
                return bottomSet;
            }
        }

        Set<Inequality> cleaned = removeTrivial(normalized);
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


    //checks whether the current state contains an inequality that entails the target one
    private boolean containsEntailing(Inequality target) {
        Set<Inequality> closed = close(inequalities);
        for (Inequality ineq : closed ) {
            if (ineq.entails(target))
                return true;
        }
        return false;
    }

    //returns true if the current state entails x <= bound
    private boolean entailsUpperBound(Identifier x, int bound) {
        return containsEntailing(new Inequality(1, x, 0, null, bound));
    }

    //returns true if the current state entails x >= bound
    private boolean entailsLowerBound(Identifier x, int bound) {
        return containsEntailing(new Inequality(-1, x, 0, null, -bound));
    }

    //returns true if the current state entails x <= y
    private boolean entailsVarLe(Identifier x, Identifier y) {
        return containsEntailing(new Inequality(1, x, -1, y, 0));
    }
    //paper：define9
    private Set<Inequality> result(Set<Inequality> set) {
        Set<Inequality> generated = new HashSet<>();

        // iterate over all pairs of inequalities
        for (Inequality i1 : set) {
            for (Inequality i2 : set) {

                if (i1 == i2)
                    continue;

                // find common variables between i1 and i2
                Set<Identifier> common = new HashSet<>(i1.variables());
                common.retainAll(i2.variables());

                // try eliminating each common variable
                for (Identifier pivot : common) {

                    int c1 = i1.coefficientOf(pivot);
                    int c2 = i2.coefficientOf(pivot);

                    // only eliminate if coefficients have opposite signs
                    if (c1 == 0 || c2 == 0 || c1 * c2 >= 0)
                        continue;

                    // perform elimination
                    Inequality newIneq = eliminateVariable(i1, i2, pivot);

                    if (newIneq != null)
                        generated.add(newIneq.normalize());
                }
            }
        }

        return sanitize(generated);
    }
    private void accumulate(Map<Identifier, Integer> coeffs,
                            Identifier id,
                            int value,
                            Identifier pivot) {

        if (id == null || value == 0 || id.equals(pivot))
            return;

        coeffs.merge(id, value, Integer::sum);
    }
    /**
     * Eliminates a variable (pivot) from two inequalities.
     *
     * Example:
     *      x - y <= 0
     *      y - z <= 0
     *
     * pivot = y
     * result = x - z <= 0
     */
    private Inequality eliminateVariable(Inequality i1, Inequality i2, Identifier pivot) {

        int p1 = i1.coefficientOf(pivot);
        int p2 = i2.coefficientOf(pivot);

        if (p1 == 0 || p2 == 0 || p1 * p2 >= 0)
            return null;

        // scale to cancel pivot
        int m1 = Math.abs(p2);
        int m2 = Math.abs(p1);

        // new constant
        int newC = m1 * i1.getC() + m2 * i2.getC();

        Map<Identifier, Integer> coeffs = new HashMap<>();

        // accumulate coefficients except pivot
        accumulate(coeffs, i1.getX(), m1 * i1.getA(), pivot);
        accumulate(coeffs, i1.getY(), m1 * i1.getB(), pivot);

        accumulate(coeffs, i2.getX(), m2 * i2.getA(), pivot);
        accumulate(coeffs, i2.getY(), m2 * i2.getB(), pivot);

        coeffs.entrySet().removeIf(e -> e.getValue() == 0);

        Identifier x = null, y = null;
        int a = 0, b = 0;

        Iterator<Map.Entry<Identifier, Integer>> it = coeffs.entrySet().iterator();

        if (it.hasNext()) {
            var e1 = it.next();
            x = e1.getKey();
            a = e1.getValue();
        }

        if (it.hasNext()) {
            var e2 = it.next();
            y = e2.getKey();
            b = e2.getValue();
        }

        return new Inequality(a, x, b, y, newC).normalize();
    }
    /**
     * Computes the closure of a set of inequalities.
     * We repeatedly generate new inequalities using the result operator
     * until no new information can be derived.
     */
    private Set<Inequality> close(Set<Inequality> set) {
        Set<Inequality> current = sanitize(set);

        while (true) {
            // if already bottom, stop immediately
            if (isBottomSet(current))
                return current;

            Set<Inequality> next = new HashSet<>(current);
            next.addAll(result(current));
            next = sanitize(next);

            if (next.equals(current))
                return next;

            current = next;
        }
    }

    private boolean isBottomSet(Set<Inequality> set) {
        return set.size() == 1 && set.iterator().next().isUnsatisfiable();
    }

    private boolean isUnsat(Set<Inequality> set) {
        for (Inequality ineq : set) {
            if (ineq.isUnsatisfiable())
                return true;
        }
        return false;
    }
    private TwoVarLinearInequality fromClosedSet(Set<Inequality> set) {
        Set<Inequality> closed = close(set);

        if (isUnsat(closed))
            return bottom();

        return new TwoVarLinearInequality(closed);
    }

    public TwoVarLinearInequality addConstraint(Inequality ineq) {
        Set<Inequality> newSet = new HashSet<>(this.inequalities);
        newSet.add(ineq.normalize());
        return fromClosedSet(newSet);
    }

    public Set<Inequality> getConstraints() {
        return new HashSet<>(close(this.inequalities));
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
        public int coefficientOf(Identifier id) {
            if (x != null && x.equals(id))
                return a;

            if (y != null && y.equals(id))
                return b;

            return 0;
        }
        //Greatest Common Divisor
        private static int gcd(int a, int b) {
            a = Math.abs(a);
            b = Math.abs(b);
            while (b != 0) {
                int t = a % b;
                a = b;
                b = t;
            }
            return a == 0 ? 1 : a;
        }

        public Inequality normalize() {
            int na = a;
            int nb = b;
            int nc = c;
            Identifier nx = x;
            Identifier ny = y;

            // compute gcd of coefficients and constant
            int g = gcd(gcd(na, nb), nc);
            if (g != 0) {
                na /= g;
                nb /= g;
                nc /= g;
            }

            // canonical ordering of variables:
            // if both variables are present, keep them ordered by name
            if (nx != null && ny != null && nx.getName().compareTo(ny.getName()) > 0) {
                Identifier tmpId = nx;
                nx = ny;
                ny = tmpId;

                int tmpCoeff = na;
                na = nb;
                nb = tmpCoeff;
            }

            return new Inequality(na, nx, nb, ny, nc);
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
