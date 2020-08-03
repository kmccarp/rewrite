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
package org.openrewrite.java.tree

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.openrewrite.java.JavaParser
import org.openrewrite.java.firstMethodStatement

interface WhileLoopTest {

    @Test
    fun whileLoop(jp: JavaParser) {
        val a = jp.parse("""
            public class A {
                public void test() {
                    while ( true ) { }
                }
            }
        """)[0]

        val whileLoop = a.firstMethodStatement() as J.WhileLoop

        assertTrue(whileLoop.condition.tree is J.Literal)
        assertTrue(whileLoop.body is J.Block<*>)
        assertEquals("while ( true ) { }", whileLoop.printTrimmed())
    }

    @Test
    fun statementTerminatorForSingleLineWhileLoops(jp: JavaParser) {
        val a = jp.parse("""
            public class A {
                public void test() {
                    while(true) test();
                }
            }
        """)[0]

        val forLoop = a.classes[0].methods[0].body!!.statements[0] as J.WhileLoop
        assertEquals("while(true) test();", forLoop.printTrimmed())
    }
}
