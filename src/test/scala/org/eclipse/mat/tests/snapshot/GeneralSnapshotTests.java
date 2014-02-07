/*******************************************************************************
 * Copyright (c) 2010,2011 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.tests.snapshot;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.SetInt;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotFactory;
import org.eclipse.mat.snapshot.model.*;
import org.eclipse.mat.snapshot.model.GCRootInfo.Type;
import org.eclipse.mat.snapshot.query.SnapshotQuery;
import org.eclipse.mat.tests.TestSnapshots;
import org.eclipse.mat.util.VoidProgressListener;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

@RunWith(value = Parameterized.class)
public class GeneralSnapshotTests {
    enum Methods {
        NONE,
        FRAMES_ONLY,
        RUNNING_METHODS,
        ALL_METHODS
    }

    final Methods hasMethods;

    enum Stacks {
        NONE,
        FRAMES_AND_OBJECTS
    }

    ;
    final Stacks stackInfo;

    @Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {TestSnapshots.SUN_JDK6_32BIT, Stacks.NONE},
                {TestSnapshots.SUN_JDK5_64BIT, Stacks.NONE},
                {TestSnapshots.SUN_JDK6_18_32BIT, Stacks.FRAMES_AND_OBJECTS},
                {TestSnapshots.SUN_JDK6_18_64BIT, Stacks.FRAMES_AND_OBJECTS},
                {TestSnapshots.SUN_JDK5_13_32BIT, Stacks.NONE},
        });
    }

    public GeneralSnapshotTests(String snapshotname, Stacks s) {
        snapshot = TestSnapshots.getSnapshot(snapshotname, false);
        hasMethods = Methods.NONE;
        stackInfo = s;
    }

    final ISnapshot snapshot;

    @Test
    public void stacks1() throws SnapshotException {
        int frames = 0;
        int foundTop = 0;
        int foundNotTop = 0;
        SetInt objs = new SetInt();
        Collection<IClass> tClasses = snapshot.getClassesByName("java.lang.Thread", true);
        if (tClasses != null) for (IClass thrdcls : tClasses) {
            for (int o : thrdcls.getObjectIds()) {
                objs.add(o);
            }
        }
        /*
         *  PHD+javacore sometimes doesn't mark javacore threads as type Thread as
         *  javacore thread id is not a real object id  
         */
        for (int o : snapshot.getGCRoots()) {
            for (GCRootInfo g : snapshot.getGCRootInfo(o)) {
                if (g.getType() == Type.THREAD_OBJ) {
                    objs.add(o);
                }
            }
        }
        for (int o : objs.toArray()) {
            IThreadStack stk = snapshot.getThreadStack(o);
            if (stk != null) {
                int i = 0;
                for (IStackFrame frm : stk.getStackFrames()) {
                    int os[] = frm.getLocalObjectsIds();
                    if (os != null) {
                        if (i == 0)
                            foundTop += os.length;
                        else
                            foundNotTop += os.length;
                    }
                    ++i;
                    ++frames;
                }
            }
        }
        // If there were some frames, and some frames had some objects
        // then a topmost frame should have some objects
        if (frames > 0 && foundNotTop > 0) {
            assertTrue("Expected some objects on top of stack", foundTop > 0);
        }
        if (this.stackInfo != Stacks.NONE) {
            assertTrue(frames > 0);
            if (this.stackInfo == Stacks.FRAMES_AND_OBJECTS)
                assertTrue(foundNotTop > 0 || foundTop > 0);
        }
    }

    @Test
    public void totalClasses() throws SnapshotException {
        int nc = snapshot.getClasses().size();
        int n = snapshot.getSnapshotInfo().getNumberOfClasses();
        assertEquals("Total classes", n, nc);
    }

    @Test
    public void totalObjects() throws SnapshotException {
        int no = 0;
        for (IClass cls : snapshot.getClasses()) {
            no += cls.getNumberOfObjects();
        }
        int n = snapshot.getSnapshotInfo().getNumberOfObjects();
        assertEquals("Total objects", n, no);
    }

    @Test
    public void totalHeapSize() throws SnapshotException {
        long total = 0;
        for (IClass cls : snapshot.getClasses()) {
            total += snapshot.getHeapSize(cls.getObjectIds());
        }
        long n = snapshot.getSnapshotInfo().getUsedHeapSize();
        assertEquals("Total heap size", n, total);
    }

    @Test
    public void objectSizes() throws SnapshotException {
        long total = 0;
        for (IClass cls : snapshot.getClasses()) {
            long prev = -1;
            for (int o : cls.getObjectIds()) {
                IObject obj = snapshot.getObject(o);
                long n = obj.getUsedHeapSize();
                long n2 = snapshot.getHeapSize(o);
                if (n != n2) {
                    assertEquals("snapshot object heap size / object heap size " + obj, n, n2);
                }
                total += n;
                if (prev >= 0) {
                    if (prev != n && !cls.isArrayType() && !(obj instanceof IClass)) {
                        // This might not be a problem as variable sized plain objects
                        // are now permitted using the array index to record the alternative sizes.
                        // However, the current dumps don't appear to have them, so test for it here.
                        // Future dumps may make this test fail.
                        assertEquals("Variable size plain objects " + cls + " " + obj, prev, n);
                    }
                } else if (!(obj instanceof IClass)) {
                    // IClass objects are variably sized, so don't track those
                    prev = n;
                }
                assertEquals("All instance of a class must be of that type", cls, obj.getClazz());
            }
        }
        long n = snapshot.getSnapshotInfo().getUsedHeapSize();
        assertEquals("Total heap size", n, total);
    }

    @Test
    public void topComponents() throws SnapshotException {
        SnapshotQuery query = SnapshotQuery.lookup("component_report_top", snapshot);
        query.setArgument("aggressive", true);
        IResult result = query.execute(new VoidProgressListener());
        assertTrue(result != null);
    }

    @Test
    public void testMethods() throws SnapshotException {
        int methods = 0;
        int methodsWithObjects = 0;
        for (IClass cls : snapshot.getClasses()) {
            if (cls.getName().contains("(") || cls.getName().equals("<stack frame>")) {
                ++methods;
                if (cls.getObjectIds().length > 0)
                    ++methodsWithObjects;
            }
        }
        if (hasMethods == Methods.ALL_METHODS) {
            assertTrue(methods > 0);
            assertTrue(methods > methodsWithObjects);
        } else if (hasMethods == Methods.RUNNING_METHODS) {
            assertTrue(methods > 0);
            assertEquals(methods, methodsWithObjects);
        } else if (hasMethods == Methods.FRAMES_ONLY) {
            assertEquals(1, methods);
            assertTrue(methodsWithObjects > 0);
        } else {
            assertEquals(0, methodsWithObjects);
            assertEquals(0, methods);
        }
    }

    @Test
    public void testClassLoaders() throws SnapshotException {
        assertTrue(snapshot.getSnapshotInfo().getNumberOfClassLoaders() > 1);
    }

    @Test
    public void testRegressionReport() throws SnapshotException {
        SnapshotQuery query = SnapshotQuery.parse("default_report org.eclipse.mat.tests:regression", snapshot);
        IResult t = query.execute(new VoidProgressListener());
        assertNotNull(t);
    }

    @Test
    public void testPerformanceReport() throws SnapshotException {
        SnapshotQuery query = SnapshotQuery.parse("default_report org.eclipse.mat.tests:performance", snapshot);
        IResult t = query.execute(new VoidProgressListener());
        assertNotNull(t);
    }

    @Test
    public void testLeakSuspectsReport() throws SnapshotException {
        SnapshotQuery query = SnapshotQuery.parse("default_report org.eclipse.mat.api:suspects", snapshot);
        IResult t = query.execute(new VoidProgressListener());
        assertNotNull(t);
    }

    @Test
    public void testOverviewReport() throws SnapshotException {
        SnapshotQuery query = SnapshotQuery.parse("default_report org.eclipse.mat.api:overview", snapshot);
        IResult t = query.execute(new VoidProgressListener());
        assertNotNull(t);
    }

    @Test
    public void testTopComponentsReport() throws SnapshotException {
        SnapshotQuery query = SnapshotQuery.parse("default_report org.eclipse.mat.api:top_components", snapshot);
        IResult t = query.execute(new VoidProgressListener());
        assertNotNull(t);
    }

    /**
     * Test caching of snapshots
     *
     * @throws SnapshotException
     */
    @Test
    public void reload1() throws SnapshotException {
        String path = snapshot.getSnapshotInfo().getPath();
        File file = new File(path);
        ISnapshot sn2 = SnapshotFactory.openSnapshot(file, new VoidProgressListener());
        try {
            ISnapshot sn3 = SnapshotFactory.openSnapshot(file, new VoidProgressListener());
            try {
                assertSame(sn2, sn3);
            } finally {
                // Do not call ISnapshot.dispose()
                SnapshotFactory.dispose(sn3);
            }
            assertEquals(snapshot.getHeapSize(0), sn3.getHeapSize(0));
        } finally {
            SnapshotFactory.dispose(sn2);
        }
    }

    /**
     * Test aliasing of two dumps.
     * Find a dump with an absolute path and a relative path which is the same
     * when converted to absolute.
     *
     * @throws SnapshotException
     * @throws IOException
     */
    @Test
    public void reload2() throws SnapshotException, IOException {
        // Get a path to a dump
        String path = snapshot.getSnapshotInfo().getPath();
        // Get the absolute path version
        File file1 = (new File(path)).getAbsoluteFile();
        // convert the absolute path to a relative path to the appropriate drive/current directory
        for (File root : File.listRoots()) {
            if (file1.getPath().startsWith(root.getPath())) {
                // Found a root e.g. C:\
                String rootPath = root.getPath();
                // Strip off the slash e.g. C:
                File drive = new File(rootPath.substring(0, rootPath.length() - 1));
                // Find the current directory for the drive e.g. C:\workspace\org.eclipse.mat.tests
                File current = drive.getAbsoluteFile();
                // e.g. C:\workspace
                current = current.getParentFile();
                // e.g. C:
                File newPrefix = drive;

                while (current != null) {
                    if (newPrefix == drive)
                        // e.g. C:..
                        newPrefix = new File(drive.getPath() + "..");
                    else
                        // e.g. C:..\..
                        newPrefix = new File(newPrefix, "..");
                    // e.g. C:\workspace
                    current = current.getParentFile();
                }
                ;
                // The relative path
                File newPath = new File(newPrefix, file1.getPath().substring(rootPath.length()));
                // The equivalent absolute path
                File f2 = newPath.getAbsoluteFile();
                /*
                 * Check that the complex path manipulations worked.
                 * This should be true for Windows and Linux. 
                 */
                assumeTrue(newPath.exists());
                assumeTrue(f2.exists());
                ISnapshot sn2 = SnapshotFactory.openSnapshot(f2, new VoidProgressListener());
                try {
                    ISnapshot sn3 = SnapshotFactory.openSnapshot(newPath, new VoidProgressListener());
                    try {
                        assertTrue(sn3.getHeapSize(0) >= 0);
                        // Do a complex operation which requires the dump still
                        // to be open and alive
                        assertNotNull(sn3.getObject(sn2.getClassOf(0).getClassLoaderId()).getOutboundReferences());
                    } finally {
                        SnapshotFactory.dispose(sn3);
                    }
                    assertTrue(sn2.getHeapSize(0) >= 0);
                    // Do a complex operation which requires the dump still to be open and alive 
                    assertNotNull(sn2.getObject(sn2.getClassOf(0).getClassLoaderId()).getOutboundReferences());
                } finally {
                    SnapshotFactory.dispose(sn2);
                }
            }
        }
    }
}
