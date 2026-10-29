package hashbench;

import java.util.HashSet;
import java.util.Set;
import java.util.SplittableRandom;

/** Kiem thu nghiem thu KeyCodec, Hashing va Generator do TV2 phu trach. */
public final class TV2SelfTest {
    private TV2SelfTest() {
    }

    public static void main(String[] args) {
        System.out.println("=== KIEM THU MODULE TV2 (KeyCodec, Hashing, Generator) ===");
        check(Hashing.verifyConstant(), "Sai hang so Fibonacci");

        long[] ids = Generator.generatePresentIds(1L, 10000);
        String[] keys = Generator.idsToKeys(ids);
        String[] repeated = Generator.generatePresentKeys(1L, ids.length);
        Set<String> uniqueKeys = new HashSet<String>();
        for (int i = 0; i < ids.length; i++) {
            String key = keys[i];
            check(key.matches("[0-9A-Z]{12}"), "Dinh dang khoa khong hop le: " + key);
            check(KeyCodec.decode(key) == ids[i], "KeyCodec round-trip sai");
            check(uniqueKeys.add(key), "Khoa sinh bi trung");
            check(key.equals(repeated[i]), "Generator khong tat dinh theo seed");
            int hash = Hashing.hash(key, 12);
            check(hash >= 0 && hash < (1 << 12), "Hash nam ngoai [0, M)");
        }
        System.out.println("1. Encode/decode, regex, uniqueness, seed determinism, hash range: OK");

        long[] absentIds = Generator.generateAbsentIds(7L, 2000, ids);
        Set<Long> presentIdSet = new HashSet<Long>();
        for (int i = 0; i < ids.length; i++) {
            presentIdSet.add(Long.valueOf(ids[i]));
        }
        for (int i = 0; i < absentIds.length; i++) {
            check(!presentIdSet.contains(Long.valueOf(absentIds[i])),
                    "ID vang mat trung ID co mat");
        }
        String[] absent = Generator.idsToKeys(absentIds);
        String digest = Generator.computeSHA256(keys, absent);
        check(digest.matches("[0-9a-f]{64}"), "SHA-256 khong hop le");
        System.out.println("2. IDs vang mat va SHA-256 khoa ASCII: OK (" + digest + ")");

        int p = 12;
        int targetHome = 42;
        String keyForHome = Hashing.findKeyWithHome(targetHome, p,
                new SplittableRandom(12345L));
        check(Hashing.hash(keyForHome, p) == targetHome,
                "findKeyWithHome tra ve home sai");
        System.out.println("3. Tim khoa co home co dinh (" + targetHome + "): OK");

        int clusterCount = 2000;
        String[] clustered = Generator.generateClusteredKeys(1L, clusterCount, p);
        String[] clusteredAgain = Generator.generateClusteredKeys(1L, clusterCount, p);
        int clusterLimit = Math.max(1, (1 << p) / 64);
        int inCluster = 0;
        Set<String> uniqueClustered = new HashSet<String>();
        for (int i = 0; i < clustered.length; i++) {
            check(clustered[i].equals(clusteredAgain[i]),
                    "Sinh khoa cum khong tat dinh theo seed");
            check(uniqueClustered.add(clustered[i]), "Khoa cum bi trung");
            if (Hashing.hash(clustered[i], p) < clusterLimit) {
                inCluster++;
            }
        }
        check(inCluster >= (clusterCount + 9) / 10,
                "Ty le khoa cum nho hon 10%: " + inCluster);
        System.out.println("4. Khoa cum: " + inCluster + "/" + clusterCount
                + " co hash < " + clusterLimit + " (toi thieu 10%): OK");

        System.out.println("TV2SelfTest: ALL PASS");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
