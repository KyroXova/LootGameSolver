package com.kyroxova.lootgamesolver.solver.minesweeper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.kyroxova.lootgamesolver.core.CellPosition;
import com.kyroxova.lootgamesolver.core.MiniGameSolver;
import com.kyroxova.lootgamesolver.core.SolveResult;
import com.kyroxova.lootgamesolver.core.SolverAction;

/**
 * A board-only Minesweeper solver. It first performs direct deductions, then
 * enumerates each connected constraint component. Enumeration is capped to
 * keep client latency predictable; capped components are reported as unknown.
 */
public final class MinesweeperSolver implements MiniGameSolver<MinesweeperBoard> {

    private static final int MAX_COMPONENT_VARIABLES = 22;

    public boolean canSolve(MinesweeperBoard board) {
        return validate(board) == null;
    }

    public SolveResult solve(MinesweeperBoard board) {
        String error = validate(board);
        if (error != null) return SolveResult.none(SolveResult.Status.INVALID, error);

        List<SolverAction> direct = directActions(board);
        if (!direct.isEmpty()) return deterministicResult(direct, "Basic deduction");

        List<Constraint> constraints = constraints(board);
        if (constraints.isEmpty())
            return hasUnknownCells(board) ? SolveResult.none(SolveResult.Status.NO_MOVE, "No revealed constraints")
                : SolveResult.none(SolveResult.Status.SOLVED, "Board solved");

        List<Enumeration> components = enumerateComponents(constraints);
        List<SolverAction> guaranteed = new ArrayList<SolverAction>();
        CellPosition safest = null;
        double safestProbability = -1D;
        for (Enumeration summary : components) {
            if (summary.solutionCount == 0)
                return SolveResult.none(SolveResult.Status.INVALID, "Board constraints are contradictory");
            for (Map.Entry<CellPosition, Integer> entry : summary.mineOccurrences.entrySet()) {
                double mineProbability = entry.getValue()
                    .doubleValue() / summary.solutionCount;
                if (mineProbability == 0D) guaranteed.add(new SolverAction(SolverAction.Type.REVEAL, entry.getKey()));
                else if (mineProbability == 1D)
                    guaranteed.add(new SolverAction(SolverAction.Type.FLAG, entry.getKey()));
                double safety = 1D - mineProbability;
                if (safety > safestProbability) {
                    safestProbability = safety;
                    safest = entry.getKey();
                }
            }
        }
        if (!guaranteed.isEmpty()) return deterministicResult(guaranteed, "Constraint enumeration");
        if (safest != null) {
            List<SolverAction> action = Collections
                .singletonList(new SolverAction(SolverAction.Type.REVEAL, safest, 0, safestProbability));
            return new SolveResult(
                SolveResult.Status.PROBABILISTIC,
                action,
                safestProbability,
                "No guaranteed move; probability is local to the constrained frontier");
        }
        if (hasUnknownCells(board)) {
            CellPosition fallback = findBestFallbackCell(board);
            if (fallback != null) {
                List<SolverAction> action = Collections
                    .singletonList(new SolverAction(SolverAction.Type.REVEAL, fallback, 0, 0.5D));
                return new SolveResult(
                    SolveResult.Status.PROBABILISTIC,
                    action,
                    0.5D,
                    "Frontier component untractable; revealing candidate cell");
            }
        }
        return SolveResult.none(SolveResult.Status.SOLVED, "Board solved");
    }

    private CellPosition findBestFallbackCell(MinesweeperBoard board) {
        for (CellPosition p : board.positions()) {
            MinesweeperCell cell = board.get(p);
            if (cell.getState() == CellState.REVEALED) {
                for (CellPosition n : board.neighbors(p)) {
                    if (board.get(n)
                        .getState() == CellState.UNKNOWN) {
                        return n;
                    }
                }
            }
        }
        for (CellPosition p : board.positions()) {
            if (board.get(p)
                .getState() == CellState.UNKNOWN) {
                return p;
            }
        }
        return null;
    }

    private SolveResult deterministicResult(List<SolverAction> actions, String message) {
        boolean hasReveal = false;
        for (SolverAction action : actions) if (action.type == SolverAction.Type.REVEAL) {
            hasReveal = true;
            break;
        }
        Collections.sort(actions, new Comparator<SolverAction>() {

            public int compare(SolverAction a, SolverAction b) {
                return a.position.compareTo(b.position);
            }
        });
        return new SolveResult(
            hasReveal ? SolveResult.Status.GUARANTEED_SAFE : SolveResult.Status.GUARANTEED_MINE,
            actions,
            1D,
            message);
    }

    private String validate(MinesweeperBoard board) {
        int flags = 0;
        for (CellPosition p : board.positions()) {
            MinesweeperCell cell = board.get(p);
            if (cell == null) return "A board cell is missing";
            if (cell.getState() == CellState.FLAGGED) flags++;
            if (cell.getState() == CellState.REVEALED && (cell.getNumber() < 0 || cell.getNumber() > 8))
                return "Invalid revealed number";
        }
        if (flags > board.getMineCount()) return "More flags than total mines";
        for (Constraint c : constraints(board))
            if (c.mines < 0 || c.mines > c.variables.size()) return "A clue contradicts its neighbors";
        return null;
    }

    private List<SolverAction> directActions(MinesweeperBoard board) {
        Set<CellPosition> safe = new LinkedHashSet<CellPosition>();
        Set<CellPosition> mines = new LinkedHashSet<CellPosition>();
        for (Constraint c : constraints(board)) {
            if (c.mines == 0) safe.addAll(c.variables);
            else if (c.mines == c.variables.size()) mines.addAll(c.variables);
        }
        safe.removeAll(mines);
        List<SolverAction> actions = new ArrayList<SolverAction>();
        for (CellPosition p : mines) actions.add(new SolverAction(SolverAction.Type.FLAG, p));
        for (CellPosition p : safe) actions.add(new SolverAction(SolverAction.Type.REVEAL, p));
        return actions;
    }

    private List<Constraint> constraints(MinesweeperBoard board) {
        List<Constraint> result = new ArrayList<Constraint>();
        for (CellPosition p : board.positions()) {
            MinesweeperCell cell = board.get(p);
            if (cell.getState() != CellState.REVEALED) continue;
            int flags = 0;
            Set<CellPosition> unknown = new LinkedHashSet<CellPosition>();
            for (CellPosition n : board.neighbors(p)) {
                if (board.get(n)
                    .getState() == CellState.FLAGGED) flags++;
                else if (board.get(n)
                    .getState() == CellState.UNKNOWN) unknown.add(n);
            }
            if (!unknown.isEmpty()) result.add(new Constraint(unknown, cell.getNumber() - flags));
        }
        return result;
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
                for (Constraint candidate : unseen)
                    if (!Collections.disjoint(current.variables, candidate.variables)) connected.add(candidate);
                unseen.removeAll(connected);
                frontier.addAll(connected);
            }
            Enumeration enumeration = enumerate(component);
            if (!enumeration.variables.isEmpty()) result.add(enumeration);
        }
        return result;
    }

    private Enumeration enumerate(List<Constraint> constraints) {
        Set<CellPosition> all = new LinkedHashSet<CellPosition>();
        for (Constraint c : constraints) all.addAll(c.variables);
        if (all.size() > MAX_COMPONENT_VARIABLES) return Enumeration.empty();
        List<CellPosition> vars = new ArrayList<CellPosition>(all);
        Map<CellPosition, Integer> index = new HashMap<CellPosition, Integer>();
        for (int i = 0; i < vars.size(); i++) index.put(vars.get(i), Integer.valueOf(i));
        int[][] terms = new int[constraints.size()][];
        int[] required = new int[constraints.size()];
        for (int i = 0; i < constraints.size(); i++) {
            Constraint c = constraints.get(i);
            terms[i] = new int[c.variables.size()];
            required[i] = c.mines;
            int n = 0;
            for (CellPosition p : c.variables) terms[i][n++] = index.get(p)
                .intValue();
        }
        Enumeration out = new Enumeration(vars);
        boolean[] assignment = new boolean[vars.size()];
        visit(0, assignment, terms, required, out);
        return out;
    }

    private void visit(int at, boolean[] assignment, int[][] terms, int[] required, Enumeration out) {
        if (!possible(at, assignment, terms, required)) return;
        if (at == assignment.length) {
            out.solutionCount++;
            for (int i = 0; i < assignment.length; i++) if (assignment[i]) out.mineOccurrences.put(
                out.variables.get(i),
                Integer.valueOf(
                    out.mineOccurrences.get(out.variables.get(i))
                        .intValue() + 1));
            return;
        }
        assignment[at] = false;
        visit(at + 1, assignment, terms, required, out);
        assignment[at] = true;
        visit(at + 1, assignment, terms, required, out);
    }

    private boolean possible(int assigned, boolean[] assignment, int[][] terms, int[] required) {
        for (int c = 0; c < terms.length; c++) {
            int trueCount = 0, unset = 0;
            for (int variable : terms[c]) {
                if (variable < assigned && assignment[variable]) trueCount++;
                else if (variable >= assigned) unset++;
            }
            if (trueCount > required[c] || trueCount + unset < required[c]) return false;
        }
        return true;
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
}
