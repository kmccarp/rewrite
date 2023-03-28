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
package org.openrewrite.java.internal;

import lombok.Value;
import org.openrewrite.internal.lang.Nullable;
import org.xerial.snappy.Snappy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class JavaTypeCache implements Cloneable {

    // empirical value: below this size, the compressed key is larger or only slightly smaller
    // although also note that a String object has a 24 bytes overhead vs. the 16 bytes of a BytesKey object
    public static final int COMPRESSION_THRESHOLD = 50;

    @Value
    private static class BytesKey {
        byte[] data;
    }

    Map<Object, Object> sourceTypeCache = new HashMap<>();
    Map<Object, Object> classTypeCache = new HashMap<>();

    @Nullable
    public <T> T get(String signature) {
        Object key = key(signature);
        Object t = sourceTypeCache.get(key);
        //noinspection unchecked
        return (T) (t != null ? t : classTypeCache.get(key));
    }

    public void put(String signature, Object o) {
        // to err on the safe side add to `sourceTypeCache` so it gets removed in `clear()`
        put(JavaTypeSource.SOURCE, signature, o);
    }

    public void put(JavaTypeSource source, String signature, Object o) {
        if (source == JavaTypeSource.SOURCE) {
            sourceTypeCache.put(key(signature), o);
        } else {
            classTypeCache.put(key(signature), o);
        }
    }

    private Object key(String signature) {
        if (signature.length() > COMPRESSION_THRESHOLD) {
            try {
                return new BytesKey(Snappy.compress(signature.getBytes(StandardCharsets.UTF_8)));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return signature;
    }

    public void clear() {
        // the `classTypeCache` is not cleared so that it can be reused
        sourceTypeCache.clear();
    }

    public int size() {
        return sourceTypeCache.size() + classTypeCache.size();
    }

    @Override
    public JavaTypeCache clone() {
        try {
            JavaTypeCache clone = (JavaTypeCache) super.clone();
            clone.classTypeCache = new HashMap<>(this.classTypeCache);
            clone.sourceTypeCache = new HashMap<>(this.sourceTypeCache);
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }
}
