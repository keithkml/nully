package net.kano.nully.plugin.analysis;

import net.kano.nully.annotations.NonNull;

import java.util.Collection;

public interface ProblemFinder<P extends NullyProblem<?>> {
    @NonNull Collection<P> findProblems(@NonNull AnalysisContext context);
}
