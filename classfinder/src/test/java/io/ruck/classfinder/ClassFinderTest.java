/*
 * Copyright 2017 ruckc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ruck.classfinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author ruckc
 */
public class ClassFinderTest {

    @Test
    public void testGetClasses() {
        ClassFinder cf = ClassFinder.getInstance("missing");
        assertNotNull(cf);
        assertTrue(cf.getClasses().isEmpty());
        cf = ClassFinder.getInstance("test1");
        assertNotNull(cf);
        assertFalse(cf.getClasses().isEmpty());
        assertEquals(2, cf.getClasses().size());
        assertTrue(cf.getClasses().contains(java.util.List.class));
        assertTrue(cf.getClasses().contains(java.util.Random.class));
    }
}
