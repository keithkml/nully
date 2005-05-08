package net.kano.nully.analysis;

import net.kano.nully.NonNull;

import java.util.List;

public interface ProblemFinder {
    @NonNull List<PsiNullProblem> findProblems(@NonNull AnalysisInfo info);
}
