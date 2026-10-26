package hashbench;

/** Bảng băm separate chaining với một bucket cho mỗi home. */
public class SeparateChainingTable implements HashIndex, HashStats {
    private final Node[] buckets;
    private final int capacity;
    private final int p;
    private int size;

    public SeparateChainingTable(int p) {
        if (p < 1 || p > 30) {
            throw new IllegalArgumentException("p phải nằm trong khoảng 1..30");
        }
        this.p = p;
        this.capacity = 1 << p;
        this.buckets = new Node[capacity];
    }

    @Override
    public int put(String key, int value) {
        requireKey(key);
        if (value == NOT_FOUND) {
            throw new IllegalArgumentException("value không được bằng NOT_FOUND");
        }
        int bucket = Hashing.hash(key, p);
        int traversed = 0;
        for (Node node = buckets[bucket]; node != null; node = node.next) {
            traversed++;
            if (key.equals(node.key)) {
                Counters.addProbes(traversed);
                node.value = value;
                return UPDATED;
            }
        }
        Counters.addProbes(traversed);
        if (size == capacity) {
            return FULL;
        }
        buckets[bucket] = new Node(key, value, buckets[bucket]);
        size++;
        return INSERTED;
    }

    @Override
    public int get(String key) {
        requireKey(key);
        int bucket = Hashing.hash(key, p);
        int traversed = 0;
        for (Node node = buckets[bucket]; node != null; node = node.next) {
            traversed++;
            if (key.equals(node.key)) {
                Counters.addProbes(traversed);
                return node.value;
            }
        }
        Counters.addProbes(traversed);
        return NOT_FOUND;
    }

    @Override
    public boolean remove(String key) {
        requireKey(key);
        int bucket = Hashing.hash(key, p);
        int traversed = 0;
        Node previous = null;
        Node current = buckets[bucket];
        while (current != null) {
            traversed++;
            if (key.equals(current.key)) {
                if (previous == null) {
                    buckets[bucket] = current.next;
                } else {
                    previous.next = current.next;
                }
                size--;
                Counters.addProbes(traversed);
                return true;
            }
            previous = current;
            current = current.next;
        }
        Counters.addProbes(traversed);
        return false;
    }

    private static void requireKey(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key không được null");
        }
    }

    @Override public int size() { return size; }
    @Override public int capacity() { return capacity; }
    @Override public int tombCount() { return 0; }
    @Override public int emptyCount() {
        int empty = 0;
        for (int i = 0; i < buckets.length; i++) {
            if (buckets[i] == null) {
                empty++;
            }
        }
        return empty;
    }
    @Override public double dibMean() { return 0.0; }
    @Override public int dibMax() { return 0; }
    @Override public long rebuildCount() { return 0L; }
    @Override public long rebuildNanos() { return 0L; }
    @Override public long probeCount() { return Counters.probeCount; }

    /** Kiểm tra số node và vị trí bucket của từng khóa. */
    public void validate() {
        int count = 0;
        for (int i = 0; i < buckets.length; i++) {
            for (Node node = buckets[i]; node != null; node = node.next) {
                if (node.key == null || Hashing.hash(node.key, p) != i) {
                    throw new IllegalStateException("khoa sai bucket " + i);
                }
                count++;
            }
        }
        if (count != size) {
            throw new IllegalStateException("size khong khop so node");
        }
    }

    /** In trạng thái bảng để duyệt tay, không dùng trong vùng đo. */
    public String dump() {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < buckets.length; i++) {
            result.append(i).append(": ");
            Node node = buckets[i];
            if (node == null) {
                result.append("-");
            } else {
                while (node != null) {
                    result.append("key=").append(node.key).append(" value=")
                            .append(node.value);
                    node = node.next;
                    if (node != null) {
                        result.append(" -> ");
                    }
                }
            }
            result.append('\n');
        }
        return result.toString();
    }

    private static final class Node {
        private final String key;
        private int value;
        private Node next;

        private Node(String key, int value, Node next) {
            this.key = key;
            this.value = value;
            this.next = next;
        }
    }
}
