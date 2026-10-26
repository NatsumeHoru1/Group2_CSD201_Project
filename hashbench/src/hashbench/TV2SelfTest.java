package hashbench;

import java.util.HashSet;
import java.util.Set;
import java.util.SplittableRandom;

/** Kiểm thử nghiệm thu KeyCodec, Hashing và Generator do TV2 phụ trách. */
public final class TV2SelfTest {
    private TV2SelfTest() {
    }

    public static void main(String[] args) {
        System.out.println("=== KIỂM THỬ MODULE TV2 (KeyCodec, Hashing, Generator) ===");
        check(Hashing.verifyConstant(), "Sai hằng số Fibonacci");

        long[] ids = Generator.generatePresentIds(1L, 10000);
        String[] keys = Generator.idsToKeys(ids);
        String[] repeated = Generator.generatePresentKeys(1L, ids.length);
        Set<String> uniqueKeys = new HashSet<String>();
        for (int i = 0; i < ids.length; i++) {
            String key = keys[i];
            check(key.matches("[0-9A-Z]{12}"), "Định dạng khóa không hợp lệ: " + key);
            check(KeyCodec.decode(key) == ids[i], "KeyCodec round-trip sai");
            check(uniqueKeys.add(key), "Khóa sinh bị trùng");
            check(key.equals(repeated[i]), "Generator không tất định theo seed");
            int hash = Hashing.hash(key, 12);
            check(hash >= 0 && hash < (1 << 12), "Hash nằm ngoài [0, M)");
        }
        System.out.println("1. Encode/decode, regex, uniqueness, seed determinism, hash range: OK");

        long[] absentIds = Generator.generateAbsentIds(7L, 2000, ids);
        Set<Long> presentIdSet = new HashSet<Long>();
        for (int i = 0; i < ids.length; i++) {
            presentIdSet.add(Long.valueOf(ids[i]));
        }
        for (int i = 0; i < absentIds.length; i++) {
            check(!presentIdSet.contains(Long.valueOf(absentIds[i])),
                    "ID vắng mặt trùng ID có mặt");
        }
        String[] absent = Generator.idsToKeys(absentIds);
        String digest = Generator.computeSHA256(keys, absent);
        check(digest.matches("[0-9a-f]{64}"), "SHA-256 không hợp lệ");
        System.out.println("2. IDs vắng mặt và SHA-256 khóa ASCII: OK (" + digest + ")");

        int p = 12;
        int targetHome = 42;
        String keyForHome = Hashing.findKeyWithHome(targetHome, p,
                new SplittableRandom(12345L));
        check(Hashing.hash(keyForHome, p) == targetHome,
                "findKeyWithHome trả về home sai");
        System.out.println("3. Tìm khóa có home cố định (" + targetHome + "): OK");

        int clusterCount = 2000;
        String[] clustered = Generator.generateClusteredKeys(1L, clusterCount, p);
        String[] clusteredAgain = Generator.generateClusteredKeys(1L, clusterCount, p);
        int clusterLimit = Math.max(1, (1 << p) / 64);
        int inCluster = 0;
        Set<String> uniqueClustered = new HashSet<String>();
        for (int i = 0; i < clustered.length; i++) {
            check(clustered[i].equals(clusteredAgain[i]),
                    "Sinh khóa cụm không tất định theo seed");
            check(uniqueClustered.add(clustered[i]), "Khóa cụm bị trùng");
            if (Hashing.hash(clustered[i], p) < clusterLimit) {
                inCluster++;
            }
        }
        check(inCluster >= (clusterCount + 9) / 10,
                "Tỷ lệ khóa cụm nhỏ hơn 10%: " + inCluster);
        System.out.println("4. Khóa cụm: " + inCluster + "/" + clusterCount
                + " có hash < " + clusterLimit + " (tối thiểu 10%): OK");

        System.out.println("TV2SelfTest: ALL PASS");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
