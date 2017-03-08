package jep.test.synchronization;

import java.util.concurrent.atomic.AtomicInteger;

import jep.Jep;
import jep.JepConfig;
import jep.JepException;

/**
 * Tests that you can lock a PyJObject from within Python just like a
 * synchronized(object) {...} block in Java.
 * 
 * The test aggressively uses one lock object and uses multiple threads from
 * both Python and Java synchronizing against the same lock object at the same
 * time. If the underlying Jep code is not locking properly, then the value of
 * the AtomicInteger will not match what is expected and/or the test will
 * deadlock.
 * 
 * @author Nate Jensen
 */
public class TestCrossLangSync {
    
    protected Object lock = new Object();
//    protected Object lock = TestCrossLangSync.class;
    
    protected final AtomicInteger atomicInt = new AtomicInteger(0);

    private static final String PY_CODE = new StringBuilder(
            "def doIt(obj, atom, count):\n").append(
            "    with obj.synchronized():\n").append(
            "        startValue = atom.get()\n").append(
            "        for i in range(1, count):\n").append(
            "            atom.getAndIncrement()\n").append(
            "            if atom.get() != startValue + i:\n").append(
            "                raise ValueError(str(atom.get()) + ' != ' + str(startValue + i))\n").toString();

    public static void main(String[] args) throws Exception {
        TestCrossLangSync test = new TestCrossLangSync();
        int size = 16;

        TestThread[] tt = new TestThread[size * 2];

        for (int i = 0; i < size * 2; i += 2) {
            tt[i] = test.new PyThread(i);
            tt[i + 1] = test.new JavaThread(i);
        }

        for (int i = 0; i < size * 2; i++) {
            tt[i].start();
        }

        for (int i = 0; i < size * 2; i++) {
            tt[i].join();
            if (tt[i].e != null) {
                throw tt[i].e;
            }
        }
    }

    private abstract class TestThread extends Thread {

        protected int count;

        protected Exception e;

        protected TestThread(int count) {
            this.count = count;
        }
    }

    private class PyThread extends TestThread {

        protected PyThread(int count) {
            super(count);
        }

        @Override
        public void run() {
            try {
                try (Jep jep = new Jep(new JepConfig().addIncludePaths("."))) {
                    jep.eval(PY_CODE);
                    jep.set("lock", lock);
                    jep.set("atom", atomicInt);
                    jep.eval("doIt(lock, atom, " + count + ")");
                } catch (JepException e) {
                    throw new RuntimeException("Synchronization failed", e);
                }
            } catch (Exception e) {
                this.e = e;
            }
        }
    }

    private class JavaThread extends TestThread {

        protected JavaThread(int count) {
            super(count);
        }

        @Override
        public void run() {
            try {
                synchronized (lock) {
                    int startValue = atomicInt.get();
                    for (int i = 1; i < count; i++) {
                        atomicInt.getAndIncrement();
                        if (atomicInt.get() != (startValue + i)) {
                            throw new RuntimeException(
                                    "Synchronization failed " + atomicInt.get()
                                            + " != " + startValue + i);
                        }
                    }
                }
            } catch (Exception e) {
                this.e = e;
            }
        }
    }

}
