package hashbench.test;

import hashbench.HashIndex;
import hashbench.test.ref.BuggyIndex;
import hashbench.test.ref.ReferenceIndex;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 6 cau hinh kiem thu, noi vao HashIndexFactory cua nhom. Thuat toan nao factory chua ho tro thi bi SKIP
 * (khong can sua file nay khi TV2/TV3/TV6 them nhanh vao factory). Ho tro ca hai chu ky:
 *   create(algo, variant, p)   va   create(algo, variant, p, tau)   (TV3 de xuat, xem TEAM_SYNC).
 */
public final class Registry {
    public interface Factory { HashIndex create(int p); }

    public static final class Impl {
        public final String name;
        public final boolean open, tomb, rebuild;
        public final Factory f;
        public Impl(String name, boolean open, boolean tomb, boolean rebuild, Factory f) {
            this.name = name; this.open = open; this.tomb = tomb; this.rebuild = rebuild; this.f = f;
        }
    }

    public static final double TAU = 0.20;
    public static final double NO_REBUILD = Double.POSITIVE_INFINITY;

    private Registry() {}

    /** Goi HashIndexFactory.create bang reflection de khong vo build khi chu ky doi. */
    static HashIndex viaFactory(String algo, String variant, int p, double tau) {
        Method m3 = null, m4 = null;
        for (Method m : hashbench.HashIndexFactory.class.getMethods()) {
            if (!m.getName().equals("create")) continue;
            Class<?>[] t = m.getParameterTypes();
            if (t.length == 3 && t[2] == int.class) m3 = m;
            if (t.length == 4 && t[2] == int.class && t[3] == double.class) m4 = m;
        }
        try {
            if (m4 != null) return (HashIndex) m4.invoke(null, algo, variant, p, tau);
            if (m3 != null) {
                if (!Double.isInfinite(tau)) throw new IllegalArgumentException("factory chua co tham so tau");
                return (HashIndex) m3.invoke(null, algo, variant, p);
            }
            throw new IllegalArgumentException("HashIndexFactory.create khong co chu ky quen thuoc");
        } catch (InvocationTargetException e) {
            Throwable c = e.getCause();
            if (c instanceof RuntimeException) throw (RuntimeException) c;
            throw new AssertionError(c);
        } catch (IllegalAccessException e) { throw new AssertionError(e); }
    }

    private static Impl cfg(final String name, final String algo, final String variant, final double tau,
                            boolean open, boolean tomb, boolean rebuild) {
        return new Impl(name, open, tomb, rebuild, new Factory() {
            public HashIndex create(int p) { return viaFactory(algo, variant, p, tau); }
        });
    }

    /** Tat ca 6 cau hinh (co the chua dung duoc; dung available() de loc). */
    public static List<Impl> all() {
        List<Impl> l = new ArrayList<Impl>();
        l.add(cfg("sc",              "sc",           "base",    NO_REBUILD, false, false, false));
        l.add(cfg("lp-tomb",         "lp-tomb",      "base",    NO_REBUILD, true,  true,  false));
        l.add(cfg("lp-tomb+rebuild", "lp-tomb",      "base",    TAU,        true,  true,  true));
        l.add(cfg("lp-backshift",    "lp-backshift", "base",    NO_REBUILD, true,  false, false));
        l.add(cfg("rh",              "rh",           "base",    NO_REBUILD, true,  false, false));
        l.add(cfg("rh-noearly",      "rh",           "noearly", NO_REBUILD, true,  false, false));
        return l;
    }

    /** Ly do khong dung duoc (null neu dung duoc). */
    public static String unavailableReason(Impl impl) {
        try { impl.f.create(4); return null; }
        catch (IllegalArgumentException e) { return e.getMessage(); }
        catch (Throwable t) { return t.toString(); }
    }

    public static List<Impl> reference() {
        List<Impl> l = new ArrayList<Impl>();
        l.add(new Impl("reference", false, false, false, new Factory() {
            public HashIndex create(int p) { return new ReferenceIndex(1 << p); }
        }));
        return l;
    }

    public static List<Impl> mutants() {
        List<Impl> l = new ArrayList<Impl>();
        for (final BuggyIndex.Mode md : BuggyIndex.Mode.values())
            l.add(new Impl("mutant:" + md, false, false, false, new Factory() {
                public HashIndex create(int p) { return new BuggyIndex(1 << p, md); }
            }));
        return l;
    }
}
