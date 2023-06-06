package org.openrewrite;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.table.CollidingSourceFiles;

import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

@Value
@EqualsAndHashCode(callSuper = true)
public class FindCollidingSourceFiles extends ScanningRecipe<FindCollidingSourceFiles.Accumulator>{

    transient CollidingSourceFiles collidingSourceFiles = new CollidingSourceFiles(this);

    @Override
    public String getDisplayName() {
        return "Find colliding source files";
    }

    @Override
    public String getDescription() {
        return "Finds source files which share a path with another source file. " +
               "There should always be exactly one source file per path within a repository. " +
               "This is a diagnostic for finding problems in OpenRewrite parsers/build plugins.";
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(@Nullable Tree tree, ExecutionContext executionContext) {
                assert tree instanceof SourceFile;
                Path p = ((SourceFile) tree).getSourcePath();
                if(acc.getSourcePaths().contains(p)) {
                    acc.getDuplicates().add(p);
                } else {
                    acc.getSourcePaths().add(p);
                }
                return tree;
            }
        };
    }

    @Override
    public Collection<? extends SourceFile> generate(Accumulator acc, ExecutionContext ctx) {
        acc.getSourcePaths().clear(); // we don't need this anymore, might as well free the memory sooner
        return super.generate(acc, ctx);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext executionContext) {
                if(tree instanceof SourceFile) {
                    Path p = ((SourceFile) tree).getSourcePath();
                    if(acc.getDuplicates().contains(p)) {
                        collidingSourceFiles.insertRow(executionContext, new CollidingSourceFiles.Row(
                                p.toString(),
                                tree.getClass().toString()
                        ));
                        return SearchResult.found(tree, "Duplicate source file " + p);
                    }
                }
                return tree;
            }
        };
    }

    @Value
    static class Accumulator {
        Set<Path> sourcePaths = new LinkedHashSet<>();
        Set<Path> duplicates = new LinkedHashSet<>();
    }
}