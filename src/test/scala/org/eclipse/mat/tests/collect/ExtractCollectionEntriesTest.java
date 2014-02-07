/*******************************************************************************
 * Copyright (c) 2010 SAP AG
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.tests.collect;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.query.SnapshotQuery;
import org.eclipse.mat.tests.TestSnapshots;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.VoidProgressListener;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the HashEntriesQuery
 *
 * @author ktsvetkov
 */
public class ExtractCollectionEntriesTest {

    @Test
    public void testHashMapEntries_Sun_JDK6() throws SnapshotException {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_32BIT, false);
        checkCollection(0x241957d0, 60, snapshot);
    }


    @Test
    public void testLinkedHashMapEntries_Sun_JDK6() throws SnapshotException {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_32BIT, false);
        checkCollection(0x24166638, 68, snapshot);
    }


    @Test
    public void testHashSetEntries_Sun_JDK6() throws SnapshotException {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_32BIT, false);
        checkCollection(0x241abf40, 2, snapshot);
    }

    @Test
    public void testLinkedHashSetEntries_Sun_JDK6() throws SnapshotException {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_32BIT, false);
        checkCollection(0x2416f090, 23, snapshot);
    }

    @Test
    public void testHashtableEntries_Sun_JDK6() throws SnapshotException {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_32BIT, false);
        checkCollection(0x2416b168, 19, snapshot);
    }


    @Test
    public void testPropertiesEntries_Sun_JDK6() throws SnapshotException {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_32BIT, false);
        checkCollection(0x24120b38, 53, snapshot);
    }

    @Test
    public void testWeakHashMapEntries_Sun_JDK6() throws SnapshotException {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_32BIT, false);
        checkCollection(0x2416b9f0, 11, snapshot);
    }

    @Test
    public void testThreadLocalMapEntries_Sun_JDK6() throws SnapshotException {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_32BIT, false);
        checkCollection(0x241a10b8, 2, snapshot);
    }

    @Test
    public void testConcurrentHashMapSegmentEntries_Sun_JDK6() throws SnapshotException {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_32BIT, false);
        checkCollection(0x24196fd8, 8, snapshot);
    }

    @Test
    public void testConcurrentHashMapEntries_Sun_JDK6() throws SnapshotException {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_32BIT, false);
        checkCollection(0x2419d490, 65, snapshot);
    }

    @Test
    public void testTreeMapEntries_Sun_JDK6() throws SnapshotException {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_32BIT, false);
        checkCollection(0x24185f68, 16, snapshot);

        checkCollection(0x24196458, 0, snapshot); // test zero-sized map
    }

    @Test
    public void testCustomCollection_Sun_JDK6() throws SnapshotException {
        ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_32BIT, false);
        long objAddress = 0x241957d0;
        int numEntries = 60;

        SnapshotQuery query = SnapshotQuery
                .parse("hash_entries 0x" + Long.toHexString(objAddress) + " -collection java.util.HashMap -array_attribute table -key_attribute key -value_attribute value", snapshot);
        IResult result = query.execute(new VoidProgressListener());
        IResultTable table = (IResultTable) result;
        int rowCount = table.getRowCount();

        assert rowCount == numEntries : MessageUtil.format("Expected to extract {0} entries from collection 0x{1} [{2}], but got {3} entries in the result",
                numEntries, Long.toHexString(objAddress), snapshot.getSnapshotInfo().getPath(), rowCount);
    }

    private void checkCollection(long objAddress, int numEntries, ISnapshot snapshot) throws SnapshotException {
        SnapshotQuery query = SnapshotQuery.parse("hash_entries 0x" + Long.toHexString(objAddress), snapshot);

        IResult result = query.execute(new VoidProgressListener());
        IResultTable table = (IResultTable) result;
        int rowCount = table.getRowCount();

        assert rowCount == numEntries : MessageUtil.format("Expected to extract {0} entries from collection 0x{1} [{2}], but got {3} entries in the result",
                numEntries, Long.toHexString(objAddress), snapshot.getSnapshotInfo().getPath(), rowCount);

        checkCollectionSize(objAddress, numEntries, snapshot);
    }

    /**
     * Also run the size query
     *
     * @param objAddress
     * @param numEntries
     * @param snapshot
     * @throws SnapshotException
     */
    private void checkCollectionSize(long objAddress, int numEntries, ISnapshot snapshot) throws SnapshotException {
        SnapshotQuery query2 = SnapshotQuery.parse("collections_grouped_by_size 0x" + Long.toHexString(objAddress), snapshot);
        IResult result2 = query2.execute(new VoidProgressListener());
        IResultTable table2 = (IResultTable) result2;
        int rowCount2 = table2.getRowCount();
        assertEquals(1, rowCount2);
        Object row = table2.getRow(0);
        int sizeBucket = (Integer) table2.getColumnValue(row, 0);
        assertEquals(numEntries, sizeBucket);

        checkCollectionFillRatio(objAddress, numEntries, snapshot);
        checkMapCollisionRatio(objAddress, numEntries, snapshot);
    }

    /**
     * Also run the fill ratio query
     *
     * @param objAddress
     * @param numEntries
     * @param snapshot
     * @throws SnapshotException
     */
    private void checkCollectionFillRatio(long objAddress, int numEntries, ISnapshot snapshot) throws SnapshotException {
        SnapshotQuery query2 = SnapshotQuery.parse("collection_fill_ratio 0x" + Long.toHexString(objAddress), snapshot);
        IResult result2 = query2.execute(new VoidProgressListener());
        IResultTable table2 = (IResultTable) result2;
        int rowCount2 = table2.getRowCount();
        if (snapshot.getClassOf(snapshot.mapAddressToId(objAddress)).getName().equals("java.util.TreeMap")) {
            // TreeMaps don't appear in the fill ratio report
            assertEquals(0, rowCount2);
        } else {
            assertEquals(1, rowCount2);
            Object row = table2.getRow(0);
            double v = (Double) table2.getColumnValue(row, 0);
            assertTrue(v > 0.0);
        }
    }

    /**
     * Also run the map collision ratio query
     *
     * @param objAddress
     * @param numEntries
     * @param snapshot
     * @throws SnapshotException
     */
    private void checkMapCollisionRatio(long objAddress, int numEntries, ISnapshot snapshot) throws SnapshotException {
        SnapshotQuery query2 = SnapshotQuery.parse("map_collision_ratio 0x" + Long.toHexString(objAddress), snapshot);
        IResult result2 = query2.execute(new VoidProgressListener());
        IResultTable table2 = (IResultTable) result2;
        int rowCount2 = table2.getRowCount();
        assertEquals(1, rowCount2);
        Object row = table2.getRow(0);
        double v = (Double) table2.getColumnValue(row, 0);
        assertTrue(v >= 0.0);
        // 100% collisions shouldn't be possible
        assertTrue(v < 1.0);
        // No collisions possible if no entries
        if (numEntries == 0)
            assertTrue(v == 0.0);
    }
}
