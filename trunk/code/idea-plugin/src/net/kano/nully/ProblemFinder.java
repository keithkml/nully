package net.kano.nully;

import java.util.List;

public interface ProblemFinder {
    List<PsiNullProblem> findProblems(MethodInfo info);
}
