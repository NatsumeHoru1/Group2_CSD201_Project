package hashbench.test;

import hashbench.HashIndex;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Trace CLI để HỌC/DƯỢT trace tay (không dùng trong vùng đo). In bảng sau từng thao tác.
 *
 *   java -cp hashbench\out hashbench.test.TraceCli --algo rh --variant base --p 3 put:A@7=1 put:B@7=2 put:C@0=3 rm:B get:C
 *
 * Mỗi khóa đặt tên bằng chữ; lần đầu nhắc tới phải ghi ô nhà: A@7 nghĩa là khóa tên A có home = 7.
 * Thao tác: put:TÊN[@home]=giá_trị | get:TÊN | rm:TÊN.   Tùy chọn: --tau 0.2 (lp-tomb có rebuild).
 */
public final class TraceCli {
    private TraceCli() {}

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true, "UTF-8"));
        String algo = "rh", variant = "base";
        int p = 3;
        double tau = Double.POSITIVE_INFINITY;
        java.util.List<String> ops = new java.util.ArrayList<String>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--algo")) algo = args[++i];
            else if (args[i].equals("--variant")) variant = args[++i];
            else if (args[i].equals("--p")) p = Integer.parseInt(args[++i]);
            else if (args[i].equals("--tau")) tau = Double.parseDouble(args[++i]);
            else ops.add(args[i]);
        }
        HashIndex t = Registry.viaFactory(algo, variant, p, tau);
        Set<Long> used = new HashSet<Long>();
        Map<String, Long> keyOf = new LinkedHashMap<String, Long>();
        Map<String, Integer> homeOf = new LinkedHashMap<String, Integer>();
        System.out.println("algo=" + algo + " variant=" + variant + " M=" + t.capacity() + " (p=" + p + ")");
        for (String op : ops) {
            String kind = op.substring(0, op.indexOf(':'));
            String rest = op.substring(op.indexOf(':') + 1);
            int val = 0;
            if (rest.contains("=")) { val = Integer.parseInt(rest.substring(rest.indexOf('=') + 1)); rest = rest.substring(0, rest.indexOf('=')); }
            String name = rest;
            if (rest.contains("@")) {
                name = rest.substring(0, rest.indexOf('@'));
                int home = Integer.parseInt(rest.substring(rest.indexOf('@') + 1));
                if (!keyOf.containsKey(name)) {
                    long k = TestUtil.keysWithHome(p, home, 1, used)[0];
                    keyOf.put(name, k); homeOf.put(name, home);
                    System.out.println("  khóa " + name + " = " + k + " (home " + home + ")");
                }
            }
            if (!keyOf.containsKey(name)) throw new IllegalArgumentException("khóa " + name + " chưa có home: viết " + name + "@h lần đầu");
            long key = keyOf.get(name);
            String res;
            if (kind.equals("put")) {
                int r = t.put(key, val);
                res = r == HashIndex.INSERTED ? "INSERTED" : r == HashIndex.UPDATED ? "UPDATED" : "FULL";
            } else if (kind.equals("get")) {
                int r = t.get(key);
                res = r == HashIndex.NOT_FOUND ? "NOT_FOUND" : String.valueOf(r);
            } else if (kind.equals("rm")) {
                res = t.remove(key) ? "true" : "false";
            } else throw new IllegalArgumentException("thao tác lạ: " + op);
            System.out.println("\n> " + op + "  ->  " + res + "   (size=" + t.size() + ")");
            print(t, keyOf, homeOf);
        }
    }

    private static void print(HashIndex t, Map<String, Long> keyOf, Map<String, Integer> homeOf) throws Exception {
        try {
            Method m = t.getClass().getMethod("dump");
            String d = (String) m.invoke(t);
            for (Map.Entry<String, Long> e : keyOf.entrySet()) d = d.replace("key=" + e.getValue() + " ", "key=" + e.getKey() + "(" + e.getValue() + ") ");
            System.out.print(d);
        } catch (NoSuchMethodException e) {
            System.out.println("  (lớp không có dump(); các khóa đang tìm thấy:)");
            for (Map.Entry<String, Long> en : keyOf.entrySet())
                if (t.get(en.getValue()) != HashIndex.NOT_FOUND) System.out.println("   " + en.getKey() + " home=" + homeOf.get(en.getKey()) + " value=" + t.get(en.getValue()));
        }
    }
}
