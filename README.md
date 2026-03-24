# TAS Project 2026

## Authors
- LI Mengxiao
- YANG Xue

## Implemented Domains
- `OverflowInterval` (non-relational domain)
- `TwoVarLinearInequality` (relational domain, to be completed)

## Domain 1: OverflowInterval

**Implementation file:** `src/main/java/it/unive/lisa/tutorial/OverflowInterval.java`  
**Test file:** `src/test/java/it/unive/lisa/tutorial/OverflowIntervalTest.java`  
**IMP program:** `inputs/overflow_interval.imp`

`OverflowInterval` is a bounded interval domain for 32-bit signed integers. It is based on the standard interval domain shown during the course and adapts it to machine arithmetic with overflow.

### Main idea

Instead of using unbounded mathematical integers, this domain restricts values to the machine range:

- `Integer.MIN_VALUE = -2147483648`
- `Integer.MAX_VALUE = 2147483647`

The lattice is still interval-based, but:

- `TOP = [Integer.MIN_VALUE, Integer.MAX_VALUE]`
- `BOTTOM` represents an unreachable state

Arithmetic operations are first evaluated using interval arithmetic, and then normalized with respect to machine bounds. If the result exceeds the 32-bit signed range, it is conservatively approximated by `TOP`.

This implementation is inspired by the overflow-aware setting discussed in the course material and in the wrapped-interval reference paper, but it adopts a simpler sound approximation that remains compatible with LiSA's standard non-relational lattice structure.

### Implemented operations

The domain currently implements:

- lattice operations: `top`, `bottom`, `lessOrEqual`, `lub`, `glb`, `widening`
- evaluation of integer constants
- unary negation
- binary arithmetic operators: addition, subtraction, multiplication, division
- comparison satisfiability: `==`, `!=`, `<`, `<=`, `>`, `>=`
- branch refinement through `assumeBinaryExpression`

### Test program and observed results

The file `inputs/overflow_interval.imp` contains several small programs used to validate the behavior of the domain.

- `basic()`:
  precise arithmetic is preserved:
  `x = [5,5]`, `y = [-5,-5]`, `z = [7,7]`

- `addOverflow()`:
  `2147483647 + 1` overflows, so the result is approximated as `TOP`

- `negOverflow()`:
  negating `Integer.MIN_VALUE` causes overflow, so the result is `TOP`

- `division()`:
  `0 / 5` is precisely analyzed as `[0,0]`

- `divByZero()`:
  division by zero yields `BOTTOM`

- `mulOverflow()`:
  `50000 * 50000` exceeds the 32-bit range, so the result is `TOP`

- `branches()`:
  the comparison `x < y` is recognized as satisfied, and the final result is precise

- `refine()` and `refineRange()`:
  branch assumptions refine intervals inside conditionals; for example, in `refineRange()` the analysis narrows `x` to `[1,9]` in the branch guarded by `x < 10` and `x > 0`

## Domain 2: TwoVarLinearInequality


## Cartesian Product

**Product test file:** to be completed  
**IMP program:** to be completed



## Notes

The repository history is intended to clearly show the contribution of each group member through separate commits on the implemented components.
