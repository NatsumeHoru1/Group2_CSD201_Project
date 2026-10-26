package hashbench;

import java.util.SplittableRandom;

/** Kiểm thử bảng separate chaining và tích hợp qua factory. */
public final class SeparateChainingSelfTest {
    private SeparateChainingSelfTest() {
    }

    public static void main(String[] args) {
        Main.countMode = true;
        SeparateChainingTable table = new SeparateChainingTable(4);
        SplittableRandom random = new SplittableRandom(77L);
        String first = Hashing.findKeyWithHome(3, 4, random);
        String second;
        do {
            second = Hashing.findKeyWithHome(3, 4, random);
        } while (first.equals(second));

        check(table.put(first, 10) == HashIndex.INSERTED, "insert first node");
        check(table.put(second, 20) == HashIndex.INSERTED, "insert collision node");
        check(table.get(first) == 10 && table.get(second) == 20, "find chained nodes");
        check(table.dump().split("\n")[3].startsWith("3: key=" + second + " value=20 -> key="
                + first), "new node is inserted at bucket head");
        check(table.put(first, 11) == HashIndex.UPDATED, "update existing node");
        check(table.get(first) == 11, "updated value");
        check(table.remove(second) && table.get(second) == HashIndex.NOT_FOUND,
                "remove head node");
        check(table.remove(first) && table.size() == 0, "remove remaining node");
        table.validate();

        HashIndex factory = HashIndexFactory.create("sc", "base", 4);
        check(factory instanceof SeparateChainingTable, "factory creates sc");
        check(factory.capacity() == 16, "fixed bucket capacity");
        for (int i = 0; i < 16; i++) {
            String key = KeyCodec.encode(i);
            check(factory.put(key, i) == HashIndex.INSERTED, "fill table " + i);
        }
        check(factory.put(KeyCodec.encode(16), 16) == HashIndex.FULL,
                "table does not grow beyond M");
        check(factory.put(KeyCodec.encode(0), 100) == HashIndex.UPDATED,
                "update remains possible when full");

        checkThrowsNull(table, "null key");
        if (table.probeCount() <= 0L) {
            throw new AssertionError("probeCount phải đếm node đã duyệt");
        }
        System.out.println("SeparateChainingSelfTest: ALL PASS");
    }

    private static void checkThrowsNull(HashIndex table, String message) {
        boolean putThrew = false;
        boolean getThrew = false;
        boolean removeThrew = false;
        try {
            table.put(null, 1);
        } catch (IllegalArgumentException expected) {
            putThrew = true;
        }
        try {
            table.get(null);
        } catch (IllegalArgumentException expected) {
            getThrew = true;
        }
        try {
            table.remove(null);
        } catch (IllegalArgumentException expected) {
            removeThrew = true;
        }
        check(putThrew && getThrew && removeThrew, message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
