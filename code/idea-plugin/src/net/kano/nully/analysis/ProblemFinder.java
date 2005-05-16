package net.kano.nully.analysis;

import net.kano.nully.NonNull;

import java.util.Collection;

public interface ProblemFinder<P extends NullyProblem<?>> {
    @NonNull Collection<P> findProblems(@NonNull AnalysisContext context);
}
