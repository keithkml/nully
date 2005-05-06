package net.kano.nully.analysis;

import java.util.List;

public interface ProblemFinder {
    List<PsiNullProblem> findProblems(AnalysisInfo info);
}
