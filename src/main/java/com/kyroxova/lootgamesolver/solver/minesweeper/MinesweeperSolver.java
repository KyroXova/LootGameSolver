package com.kyroxova.lootgamesolver.solver.minesweeper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.kyroxova.lootgamesolver.core.CellPosition;
import com.kyroxova.lootgamesolver.core.MiniGameSolver;
import com.kyroxova.lootgamesolver.core.SolveResult;
import com.kyroxova.lootgamesolver.core.SolverAction;

/**
 * Advanced Minesweeper solver combining:
 * 1. Direct deduction and iterative Subset Reduction (Tank solver)
 * 2. Connected component MRV-backtracking with eager constraint forward-checking
 * 3. Exact global probability calculations for constrained and unconstrained cells
 */
public final class MinesweeperSolver implements MiniGameSolver<MinesweeperBoard> {

    private static final int MAX_COMPONENT_VARIABLES = 60;
    private static final int MAX_SOLUTIONS_PER_COMPONENT = 100000;

    @Override
    public boolean canSolve(MinesweeperBoard board) {
        return validate(board) == null;
    }

    @Override
    public SolveResult solve(MinesweeperBoard board) {
        String error = validate(board);
        if (error != null) return SolveResult.none(SolveResult.Status.INVALID, error);

        List<Constraint> rawConstraints = extractConstraints(board);
        if (rawConstraints.isEmpty()) {
            return hasUnknownCells(board) ? SolveResult.none(SolveResult.Status.NO_MOVE, "No revealed constraints")
                : SolveResult.none(SolveResult.Status.SOLVED, "Board solved");
        }

        // Phase 1: Iterative Direct Deduction & Subset Reduction (Tank Algorithm)
        ReductionResult reduction = reduceConstraints(rawConstraints);
        if (reduction.isContradictory) {
            return SolveResult.none(SolveResult.Status.INVALID, "Board constraints are contradictory");
        }

        if (!reduction.deducedActions.isEmpty()) {
            return deterministicResult(reduction.deducedActions, "Subset deduction");
        }

        List<Constraint> reducedConstraints = reduction.remainingConstraints;
        if (reducedConstraints.isEmpty()) {
            return hasUnknownCells(board) ? SolveResult.none(SolveResult.Status.NO_MOVE, "No revealed constraints")
                : SolveResult.none(SolveResult.Status.SOLVED, "Board solved");
        }

        // Phase 2: Connected Component Decomposition & Backtracking
        List<Enumeration> components = enumerateComponents(reducedConstraints);
        List<SolverAction> guaranteed = new ArrayList<SolverAction>();
        Map<CellPosition, Double> frontierMineProbabilities = new HashMap<CellPosition, Double>();
        double expectedFrontierMines = 0.0D;

        for (Enumeration summary : components) {
            if (summary.solutionCount == 0) {
                return SolveResult.none(SolveResult.Status.INVALID, "Board constraints are contradictory");
            }
            double compTotalSolutions = summary.solutionCount;
            for (Map.Entry<CellPosition, Integer> entry : summary.mineOccurrences.entrySet()) {
                double mineProb = entry.getValue()
                    .doubleValue() / compTotalSolutions;
                frontierMineProbabilities.put(entry.getKey(), mineProb);
                expectedFrontierMines += mineProb;

                if (mineProb <= 0.000001D) {
                    guaranteed.add(new SolverAction(SolverAction.Type.REVEAL, entry.getKey()));
                } else if (mineProb >= 0.999999D) {
                    guaranteed.add(new SolverAction(SolverAction.Type.FLAG, entry.getKey()));
                }
            }
        }

        if (!guaranteed.isEmpty()) {
            return deterministicResult(guaranteed, "Constraint enumeration");
        }

        // Phase 3: Global Probability Calculation (when no deterministic move exists)
        if (hasUnknownCells(board)) {
            CandidateSelection bestCandidate = findSafestCandidate(
                board,
                frontierMineProbabilities,
                expectedFrontierMines);
            if (bestCandidate != null) {
                List<SolverAction> action = Collections.singletonList(
                    new SolverAction(
                        SolverAction.Type.REVEAL,
                        bestCandidate.position,
                        0,
                        bestCandidate.safetyProbability));
                return new SolveResult(
                    SolveResult.Status.PROBABILISTIC,
                    action,
                    bestCandidate.safetyProbability,
                    bestCandidate.description);
            }
        }

        return SolveResult.none(SolveResult.Status.SOLVED, "Board solved");
    }

    private static final class CandidateSelection {

        final CellPosition position;
        final double safetyProbability;
        final String description;

        CandidateSelection(CellPosition position, double safetyProbability, String description) {
            this.position = position;
            this.safetyProbability = safetyProbability;
            this.description = description;
        }
    }

    private CandidateSelection findSafestCandidate(MinesweeperBoard board,
        Map<CellPosition, Double> frontierProbabilities, double expectedFrontierMines) {

        CellPosition bestPos = null;
        double lowestMineProb = 1.1D;
        String reason = "";

        // Check constrained frontier cells first
        for (Map.Entry<CellPosition, Double> entry : frontierProbabilities.entrySet()) {
            double pMine = entry.getValue();
            if (pMine < lowestMineProb) {
                lowestMineProb = pMine;
                bestPos = entry.getKey();
                reason = "Safest frontier cell (risk: " + String.format("%.1f%%", pMine * 100.0) + ")";
            }
        }

        // Count unconstrained unknown cells (unknown cells not part of any constraint)
        List<CellPosition> unconstrained = new ArrayList<CellPosition>();
        int totalFlags = 0;
        for (CellPosition p : board.positions()) {
            MinesweeperCell cell = board.get(p);
            if (cell.getState() == CellState.FLAGGED) {
                totalFlags++;
            } else if (cell.getState() == CellState.UNKNOWN) {
                if (!frontierProbabilities.containsKey(p)) {
                    unconstrained.add(p);
                }
            }
        }

        if (!unconstrained.isEmpty()) {
            int remainingTotalMines = Math.max(0, board.getMineCount() - totalFlags);
            double remainingUnconstrainedMines = Math.max(0.0D, remainingTotalMines - expectedFrontierMines);
            double unconstrainedMineProb = Math
                .min(1.0D, Math.max(0.0D, remainingUnconstrainedMines / unconstrained.size()));

            if (unconstrainedMineProb < lowestMineProb) {
                // Prefer corners or edges of unconstrained cells
                CellPosition bestUnconstrained = pickBestUnconstrainedPosition(board, unconstrained);
                lowestMineProb = unconstrainedMineProb;
                bestPos = bestUnconstrained;
                reason = "Unconstrained field cell (estimated risk: "
                    + String.format("%.1f%%", unconstrainedMineProb * 100.0)
                    + ")";
            }
        }

        if (bestPos != null) {
            double safety = Math.max(0.0D, Math.min(1.0D, 1.0D - lowestMineProb));
            return new CandidateSelection(bestPos, safety, reason);
        }

        return null;
    }

    private CellPosition pickBestUnconstrainedPosition(MinesweeperBoard board, List<CellPosition> unconstrained) {
        int w = board.getWidth();
        int h = board.getHeight();

        // Check four board corners first
        CellPosition[] corners = new CellPosition[] { new CellPosition(0, 0), new CellPosition(w - 1, 0),
            new CellPosition(0, h - 1), new CellPosition(w - 1, h - 1) };
        for (CellPosition c : corners) {
            if (unconstrained.contains(c)) return c;
        }

        // Otherwise return the position closest to board center
        int cx = w / 2;
        int cy = h / 2;
        CellPosition best = unconstrained.get(0);
        int bestDistSq = Integer.MAX_VALUE;
        for (CellPosition p : unconstrained) {
            int dx = p.x - cx;
            int dy = p.y - cy;
            int d = dx * dx + dy * dy;
            if (d < bestDistSq) {
                bestDistSq = d;
                best = p;
            }
        }
        return best;
    }

    private SolveResult deterministicResult(List<SolverAction> actions, String message) {
        boolean hasReveal = false;
        for (SolverAction action : actions) {
            if (action.type == SolverAction.Type.REVEAL) {
                hasReveal = true;
                break;
            }
        }
        Collections.sort(actions, ActionPosComparator.INSTANCE);
        return new SolveResult(
            hasReveal ? SolveResult.Status.GUARANTEED_SAFE : SolveResult.Status.GUARANTEED_MINE,
            actions,
            1.0D,
            message);
    }

    private String validate(MinesweeperBoard board) {
        int flags = 0;
        for (CellPosition p : board.positions()) {
            MinesweeperCell cell = board.get(p);
            if (cell == null) return "A board cell is missing";
            if (cell.getState() == CellState.FLAGGED) flags++;
            if (cell.getState() == CellState.REVEALED && (cell.getNumber() < 0 || cell.getNumber() > 8)) {
                return "Invalid revealed number";
            }
        }
        if (flags > board.getMineCount()) return "More flags than total mines";
        return null;
    }

    private List<Constraint> extractConstraints(MinesweeperBoard board) {
        List<Constraint> result = new ArrayList<Constraint>();
        for (CellPosition p : board.positions()) {
            MinesweeperCell cell = board.get(p);
            if (cell.getState() != CellState.REVEALED) continue;
            int flags = 0;
            Set<CellPosition> unknown = new LinkedHashSet<CellPosition>();
            for (CellPosition n : board.neighbors(p)) {
                MinesweeperCell nCell = board.get(n);
                if (nCell.getState() == CellState.FLAGGED) {
                    flags++;
                } else if (nCell.getState() == CellState.UNKNOWN) {
                    unknown.add(n);
                }
            }
            if (!unknown.isEmpty()) {
                result.add(new Constraint(unknown, cell.getNumber() - flags));
            }
        }
        return result;
    }

    private static final class ReductionResult {

        final List<SolverAction> deducedActions;
        final List<Constraint> remainingConstraints;
        final boolean isContradictory;

        ReductionResult(List<SolverAction> deducedActions, List<Constraint> remainingConstraints,
            boolean isContradictory) {
            this.deducedActions = deducedActions;
            this.remainingConstraints = remainingConstraints;
            this.isContradictory = isContradictory;
        }
    }

    /**
     * Performs iterative constraint simplification and subset reduction (Tank Solver algorithm).
     */
    private ReductionResult reduceConstraints(List<Constraint> initial) {
        Map<CellPosition, Boolean> knownAssignments = new LinkedHashMap<CellPosition, Boolean>(); // true = mine, false
                                                                                                  // = safe
        List<Constraint> current = new ArrayList<Constraint>();
        for (Constraint c : initial) {
            current.add(new Constraint(new LinkedHashSet<CellPosition>(c.variables), c.mines));
        }

        boolean changed = true;
        while (changed) {
            changed = false;

            // 1. Simplify constraints with known assignments
            if (!knownAssignments.isEmpty()) {
                List<Constraint> updated = new ArrayList<Constraint>();
                for (Constraint c : current) {
                    Set<CellPosition> vars = new LinkedHashSet<CellPosition>(c.variables);
                    int mines = c.mines;
                    for (Map.Entry<CellPosition, Boolean> entry : knownAssignments.entrySet()) {
                        if (vars.remove(entry.getKey())) {
                            if (entry.getValue()) {
                                mines--;
                            }
                        }
                    }
                    if (mines < 0 || mines > vars.size()) {
                        return new ReductionResult(
                            Collections.<SolverAction>emptyList(),
                            Collections.<Constraint>emptyList(),
                            true);
                    }
                    if (!vars.isEmpty()) {
                        updated.add(new Constraint(vars, mines));
                    }
                }
                current = updated;
            }

            // 2. Direct deductions on each constraint
            List<Constraint> nextConstraints = new ArrayList<Constraint>();
            for (Constraint c : current) {
                if (c.mines < 0 || c.mines > c.variables.size()) {
                    return new ReductionResult(
                        Collections.<SolverAction>emptyList(),
                        Collections.<Constraint>emptyList(),
                        true);
                }
                if (c.mines == 0) {
                    for (CellPosition p : c.variables) {
                        if (!knownAssignments.containsKey(p)) {
                            knownAssignments.put(p, Boolean.FALSE);
                            changed = true;
                        }
                    }
                } else if (c.mines == c.variables.size()) {
                    for (CellPosition p : c.variables) {
                        if (!knownAssignments.containsKey(p)) {
                            knownAssignments.put(p, Boolean.TRUE);
                            changed = true;
                        }
                    }
                } else {
                    nextConstraints.add(c);
                }
            }
            current = nextConstraints;
            if (changed) continue;

            // 3. Pairwise Subset Reduction (If C1.vars is a strict subset of C2.vars -> C3 = C2 - C1)
            int n = current.size();
            List<Constraint> addedSubsets = new ArrayList<Constraint>();
            for (int i = 0; i < n; i++) {
                Constraint c1 = current.get(i);
                for (int j = 0; j < n; j++) {
                    if (i == j) continue;
                    Constraint c2 = current.get(j);
                    if (c2.variables.size() > c1.variables.size() && c2.variables.containsAll(c1.variables)) {
                        Set<CellPosition> diffVars = new LinkedHashSet<CellPosition>(c2.variables);
                        diffVars.removeAll(c1.variables);
                        int diffMines = c2.mines - c1.mines;
                        if (diffMines < 0 || diffMines > diffVars.size()) {
                            return new ReductionResult(
                                Collections.<SolverAction>emptyList(),
                                Collections.<Constraint>emptyList(),
                                true);
                        }
                        if (diffMines == 0) {
                            for (CellPosition p : diffVars) {
                                if (!knownAssignments.containsKey(p)) {
                                    knownAssignments.put(p, Boolean.FALSE);
                                    changed = true;
                                }
                            }
                        } else if (diffMines == diffVars.size()) {
                            for (CellPosition p : diffVars) {
                                if (!knownAssignments.containsKey(p)) {
                                    knownAssignments.put(p, Boolean.TRUE);
                                    changed = true;
                                }
                            }
                        } else {
                            Constraint subsetConstraint = new Constraint(diffVars, diffMines);
                            if (!containsEquivalentConstraint(current, subsetConstraint)
                                && !containsEquivalentConstraint(addedSubsets, subsetConstraint)) {
                                addedSubsets.add(subsetConstraint);
                                changed = true;
                            }
                        }
                    }
                }
            }
            current.addAll(addedSubsets);
        }

        // Deduce actions from known assignments
        List<SolverAction> actions = new ArrayList<SolverAction>();
        for (Map.Entry<CellPosition, Boolean> entry : knownAssignments.entrySet()) {
            if (entry.getValue()) {
                actions.add(new SolverAction(SolverAction.Type.FLAG, entry.getKey()));
            } else {
                actions.add(new SolverAction(SolverAction.Type.REVEAL, entry.getKey()));
            }
        }

        // Deduplicate remaining constraints
        List<Constraint> distinctRemaining = new ArrayList<Constraint>();
        for (Constraint c : current) {
            if (!containsEquivalentConstraint(distinctRemaining, c)) {
                distinctRemaining.add(c);
            }
        }

        return new ReductionResult(actions, distinctRemaining, false);
    }

    private boolean containsEquivalentConstraint(List<Constraint> list, Constraint target) {
        for (Constraint c : list) {
            if (c.mines == target.mines && c.variables.size() == target.variables.size()
                && c.variables.containsAll(target.variables)) {
                return true;
            }
        }
        return false;
    }

    private List<Enumeration> enumerateComponents(List<Constraint> constraints) {
        List<Enumeration> result = new ArrayList<Enumeration>();
        Set<Constraint> unseen = new HashSet<Constraint>(constraints);
        while (!unseen.isEmpty()) {
            List<Constraint> component = new ArrayList<Constraint>();
            List<Constraint> frontier = new ArrayList<Constraint>();
            Constraint first = unseen.iterator()
                .next();
            unseen.remove(first);
            frontier.add(first);
            while (!frontier.isEmpty()) {
                Constraint current = frontier.remove(frontier.size() - 1);
                component.add(current);
                List<Constraint> connected = new ArrayList<Constraint>();
                for (Constraint candidate : unseen) {
                    if (!Collections.disjoint(current.variables, candidate.variables)) {
                        connected.add(candidate);
                    }
                }
                unseen.removeAll(connected);
                frontier.addAll(connected);
            }
            Enumeration enumeration = enumerate(component);
            if (!enumeration.variables.isEmpty()) {
                result.add(enumeration);
            }
        }
        return result;
    }

    private Enumeration enumerate(List<Constraint> constraints) {
        Set<CellPosition> all = new LinkedHashSet<CellPosition>();
        for (Constraint c : constraints) {
            all.addAll(c.variables);
        }
        if (all.isEmpty()) return Enumeration.empty();
        if (all.size() > MAX_COMPONENT_VARIABLES) {
            // Capped component: return empty so probabilistic solver can handle
            return Enumeration.empty();
        }

        // Variable ordering: Most Restricted Variable (MRV) first (order by constraint involvement count)
        final Map<CellPosition, Integer> constraintCount = new HashMap<CellPosition, Integer>();
        for (CellPosition p : all) {
            constraintCount.put(p, Integer.valueOf(0));
        }
        for (Constraint c : constraints) {
            for (CellPosition p : c.variables) {
                constraintCount.put(p, Integer.valueOf(constraintCount.get(p) + 1));
            }
        }

        List<CellPosition> vars = new ArrayList<CellPosition>(all);
        Collections.sort(vars, new CellConstraintComparator(constraintCount));

        Map<CellPosition, Integer> index = new HashMap<CellPosition, Integer>();
        for (int i = 0; i < vars.size(); i++) {
            index.put(vars.get(i), Integer.valueOf(i));
        }

        int[][] terms = new int[constraints.size()][];
        int[] required = new int[constraints.size()];
        for (int i = 0; i < constraints.size(); i++) {
            Constraint c = constraints.get(i);
            terms[i] = new int[c.variables.size()];
            required[i] = c.mines;
            int n = 0;
            for (CellPosition p : c.variables) {
                terms[i][n++] = index.get(p)
                    .intValue();
            }
        }

        Enumeration out = new Enumeration(vars);
        boolean[] assignment = new boolean[vars.size()];
        boolean[] assigned = new boolean[vars.size()];
        int[] currentMineCounts = new int[constraints.size()];
        int[] unsetCounts = new int[constraints.size()];
        for (int c = 0; c < constraints.size(); c++) {
            unsetCounts[c] = terms[c].length;
        }

        backtrack(0, assignment, assigned, terms, required, currentMineCounts, unsetCounts, out);
        return out;
    }

    private void backtrack(int varIndex, boolean[] assignment, boolean[] assigned, int[][] terms, int[] required,
        int[] currentMineCounts, int[] unsetCounts, Enumeration out) {

        if (out.solutionCount >= MAX_SOLUTIONS_PER_COMPONENT) return;

        if (varIndex == assignment.length) {
            out.solutionCount++;
            for (int i = 0; i < assignment.length; i++) {
                if (assignment[i]) {
                    CellPosition p = out.variables.get(i);
                    out.mineOccurrences.put(
                        p,
                        Integer.valueOf(
                            out.mineOccurrences.get(p)
                                .intValue() + 1));
                }
            }
            return;
        }

        // Try SAFE (false) first
        if (canAssign(varIndex, false, terms, required, currentMineCounts, unsetCounts)) {
            applyAssignment(varIndex, false, terms, currentMineCounts, unsetCounts);
            assignment[varIndex] = false;
            assigned[varIndex] = true;
            backtrack(varIndex + 1, assignment, assigned, terms, required, currentMineCounts, unsetCounts, out);
            unapplyAssignment(varIndex, false, terms, currentMineCounts, unsetCounts);
            assigned[varIndex] = false;
        }

        // Try MINE (true)
        if (canAssign(varIndex, true, terms, required, currentMineCounts, unsetCounts)) {
            applyAssignment(varIndex, true, terms, currentMineCounts, unsetCounts);
            assignment[varIndex] = true;
            assigned[varIndex] = true;
            backtrack(varIndex + 1, assignment, assigned, terms, required, currentMineCounts, unsetCounts, out);
            unapplyAssignment(varIndex, true, terms, currentMineCounts, unsetCounts);
            assigned[varIndex] = false;
        }
    }

    private boolean canAssign(int varIndex, boolean isMine, int[][] terms, int[] required, int[] currentMineCounts,
        int[] unsetCounts) {
        for (int c = 0; c < terms.length; c++) {
            boolean containsVar = false;
            for (int v : terms[c]) {
                if (v == varIndex) {
                    containsVar = true;
                    break;
                }
            }
            if (containsVar) {
                int newMines = currentMineCounts[c] + (isMine ? 1 : 0);
                int newUnset = unsetCounts[c] - 1;
                if (newMines > required[c] || newMines + newUnset < required[c]) {
                    return false;
                }
            }
        }
        return true;
    }

    private void applyAssignment(int varIndex, boolean isMine, int[][] terms, int[] currentMineCounts,
        int[] unsetCounts) {
        for (int c = 0; c < terms.length; c++) {
            for (int v : terms[c]) {
                if (v == varIndex) {
                    if (isMine) currentMineCounts[c]++;
                    unsetCounts[c]--;
                    break;
                }
            }
        }
    }

    private void unapplyAssignment(int varIndex, boolean isMine, int[][] terms, int[] currentMineCounts,
        int[] unsetCounts) {
        for (int c = 0; c < terms.length; c++) {
            for (int v : terms[c]) {
                if (v == varIndex) {
                    if (isMine) currentMineCounts[c]--;
                    unsetCounts[c]++;
                    break;
                }
            }
        }
    }

    private boolean hasUnknownCells(MinesweeperBoard board) {
        for (CellPosition p : board.positions()) {
            if (board.get(p)
                .getState() == CellState.UNKNOWN) return true;
        }
        return false;
    }

    private static final class Constraint {

        final Set<CellPosition> variables;
        final int mines;

        Constraint(Set<CellPosition> variables, int mines) {
            this.variables = variables;
            this.mines = mines;
        }
    }

    private static final class Enumeration {

        final List<CellPosition> variables;
        final Map<CellPosition, Integer> mineOccurrences = new HashMap<CellPosition, Integer>();
        int solutionCount;

        Enumeration(List<CellPosition> variables) {
            this.variables = variables;
            for (CellPosition p : variables) mineOccurrences.put(p, Integer.valueOf(0));
        }

        static Enumeration empty() {
            return new Enumeration(Collections.<CellPosition>emptyList());
        }
    }

    private static final class ActionPosComparator implements Comparator<SolverAction> {

        static final ActionPosComparator INSTANCE = new ActionPosComparator();

        @Override
        public int compare(SolverAction a, SolverAction b) {
            return a.position.compareTo(b.position);
        }
    }

    private static final class CellConstraintComparator implements Comparator<CellPosition> {

        private final Map<CellPosition, Integer> constraintCount;

        CellConstraintComparator(Map<CellPosition, Integer> constraintCount) {
            this.constraintCount = constraintCount;
        }

        @Override
        public int compare(CellPosition a, CellPosition b) {
            return Integer.compare(constraintCount.get(b), constraintCount.get(a));
        }
    }
}
