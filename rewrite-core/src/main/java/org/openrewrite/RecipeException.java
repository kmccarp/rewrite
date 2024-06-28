/*
 * Copyright 2020 the original author or authors.
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
package org.openrewrite;

/**
 * When possible, {@link Recipe} should validate themselves prior to execution to prove
 * that they are runnable without failure. But in some cases, recipes may interact with
 * external systems that may fail even though the inputs validate. In those cases,
 * recipes may throw {@link RecipeException}.
 */
public class RecipeException extends RuntimeException {
    public RecipeException(Throwable cause) {
        super(cause);
    }

    public RecipeException(String message) {
        super(message);
    }

    public RecipeException(String message, Object... args) {
        super(message.formatted(args));
    }

    public RecipeException(Throwable cause, String message, Object... args) {
        super(message.formatted(args), cause);
    }
}
