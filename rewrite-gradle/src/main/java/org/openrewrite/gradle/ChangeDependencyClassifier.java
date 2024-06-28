/*
 * Copyright 2021 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.gradle;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.gradle.util.ChangeStringLiteral;
import org.openrewrite.gradle.util.Dependency;
import org.openrewrite.gradle.util.DependencyStringNotationConverter;
import org.openrewrite.groovy.GroovyVisitor;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;
import org.openrewrite.semver.DependencyMatcher;

import java.util.List;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

@Value
@EqualsAndHashCode(callSuper = false)
public class ChangeDependencyClassifier extends Recipe {
    @Option(displayName = "Group",
            description = "The first part of a dependency coordinate `com.google.guava:guava:VERSION`. This can be a glob expression.",
            example = "com.fasterxml.jackson*")
    String groupId;

    @Option(displayName = "Artifact",
            description = "The second part of a dependency coordinate `com.google.guava:guava:VERSION`. This can be a glob expression.",
            example = "jackson-module*")
    String artifactId;

    @Option(displayName = "New classifier",
            description = "A qualification classifier for the dependency.",
            example = "sources",
            required = false)
    @Nullable
    String newClassifier;

    @Option(displayName = "Dependency configuration",
            description = "The dependency configuration to search for dependencies in.",
            example = "api",
            required = false)
    @Nullable
    String configuration;

    @Override
    public String getDisplayName() {
        return "Change a Gradle dependency classifier";
    }

    @Override
    public String getInstanceNameSuffix() {
        return "`%s:%s` to `%s`".formatted(groupId, artifactId, newClassifier);
    }

    @Override
    public String getDescription() {
        return "Finds dependencies declared in `build.gradle` files.";
    }

    @Override
    public Validated<Object> validate() {
        return super.validate().and(DependencyMatcher.build(groupId + ":" + artifactId));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new IsBuildGradle<>(), new GroovyVisitor<ExecutionContext>() {
            final DependencyMatcher depMatcher = requireNonNull(DependencyMatcher.build(groupId + ":" + artifactId).getValue());
            final MethodMatcher dependencyDsl = new MethodMatcher("DependencyHandlerSpec *(..)");

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (!dependencyDsl.matches(m) || !(StringUtils.isBlank(configuration) || m.getSimpleName().equals(configuration))) {
                    return m;
                }

                List<Expression> depArgs = m.getArguments();
                if (depArgs.getFirst() instanceof J.Literal) {
                    String gav = (String) ((J.Literal) depArgs.getFirst()).getValue();
                    if (gav != null) {
                        Dependency dependency = DependencyStringNotationConverter.parse(gav);
                        if (dependency != null && dependency.getVersion() != null && !Objects.equals(newClassifier, dependency.getClassifier()) &&
                            depMatcher.matches(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion())) {
                            Dependency newDependency = dependency.withClassifier(newClassifier);
                            m = m.withArguments(ListUtils.mapFirst(m.getArguments(), arg -> ChangeStringLiteral.withStringValue((J.Literal) arg, newDependency.toStringNotation())));
                        }
                    }
                } else if (depArgs.getFirst() instanceof G.MapEntry) {
                    G.MapEntry classifierEntry = null;
                    String groupId = null;
                    String artifactId = null;
                    String version = null;
                    String classifier = null;

                    String groupDelimiter = "'";
                    G.MapEntry mapEntry = null;
                    String classifierStringDelimiter = null;
                    int index = 0;
                    for (Expression e : depArgs) {
                        if (!(e instanceof G.MapEntry)) {
                            continue;
                        }
                        G.MapEntry arg = (G.MapEntry) e;
                        if (!(arg.getKey() instanceof J.Literal) || !(arg.getValue() instanceof J.Literal)) {
                            continue;
                        }
                        J.Literal key = (J.Literal) arg.getKey();
                        J.Literal value = (J.Literal) arg.getValue();
                        if (!(key.getValue() instanceof String) || !(value.getValue() instanceof String)) {
                            continue;
                        }
                        String keyValue = (String) key.getValue();
                        String valueValue = (String) value.getValue();
                        if ("group".equals(keyValue)) {
                            groupId = valueValue;
                            if (value.getValueSource() != null) {
                                groupDelimiter = value.getValueSource().substring(0, value.getValueSource().indexOf(valueValue));
                            }
                        } else if ("name".equals(keyValue)) {
                            if (index > 0 && mapEntry == null) {
                                mapEntry = arg;
                            }
                            artifactId = valueValue;
                        } else if ("version".equals(keyValue)) {
                            version = valueValue;
                        } else if ("classifier".equals(keyValue)) {
                            if (value.getValueSource() != null) {
                                classifierStringDelimiter = value.getValueSource().substring(0, value.getValueSource().indexOf(valueValue));
                            }
                            classifierEntry = arg;
                            classifier = valueValue;
                        }
                        index++;
                    }
                    if (groupId == null || artifactId == null
                        || (version == null && !depMatcher.matches(groupId, artifactId))
                        || (version != null && !depMatcher.matches(groupId, artifactId, version))
                        || Objects.equals(newClassifier, classifier)) {
                        return m;
                    }

                    if (classifier == null) {
                        String delimiter = groupDelimiter;
                        List<Expression> args = m.getArguments();
                        J.Literal keyLiteral = new J.Literal(Tree.randomId(), mapEntry == null ? Space.EMPTY : mapEntry.getKey().getPrefix(), Markers.EMPTY, "classifier", "classifier", null, JavaType.Primitive.String);
                        J.Literal valueLiteral = new J.Literal(Tree.randomId(), mapEntry == null ? Space.EMPTY : mapEntry.getValue().getPrefix(), Markers.EMPTY, newClassifier, delimiter + newClassifier + delimiter, null, JavaType.Primitive.String);
                        args.add(new G.MapEntry(Tree.randomId(), mapEntry == null ? Space.EMPTY : mapEntry.getPrefix(), Markers.EMPTY, JRightPadded.build(keyLiteral), valueLiteral, null));
                        m = m.withArguments(args);
                    } else {
                        G.MapEntry finalClassifier = classifierEntry;
                        if (newClassifier == null) {
                            m = m.withArguments(ListUtils.map(m.getArguments(), arg -> arg == finalClassifier ? null : arg));
                        } else {
                            String delimiter = classifierStringDelimiter; // `classifierStringDelimiter` cannot be null
                            m = m.withArguments(ListUtils.map(m.getArguments(), arg -> {
                                if (arg == finalClassifier) {
                                    return finalClassifier.withValue(((J.Literal) finalClassifier.getValue())
                                            .withValue(newClassifier)
                                            .withValueSource(delimiter + newClassifier + delimiter));
                                }
                                return arg;
                            }));
                        }
                    }

                }

                return m;
            }
        });
    }

}
