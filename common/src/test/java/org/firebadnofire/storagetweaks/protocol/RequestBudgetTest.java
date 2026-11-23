package org.firebadnofire.storagetweaks.protocol;

import org.junit.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class RequestBudgetTest {
    @Test public void allowsBurstButBoundsWorkAndResetsAfterSecond() {
        RequestBudget budget=new RequestBudget();
        for(int i=0;i<40;i++) assertTrue(budget.allow(100));
        assertFalse(budget.allow(101));
        assertFalse(budget.allow(1_000_000_099L));
        assertTrue(budget.allow(1_000_000_100L));
    }
    @Test public void concurrentCallsCannotExceedBudget() throws Exception {
        RequestBudget budget=new RequestBudget();AtomicInteger accepted=new AtomicInteger();
        ExecutorService threads=Executors.newFixedThreadPool(4);
        try {
            java.util.List<Future<?>> tasks=new java.util.ArrayList<>();
            for(int i=0;i<4;i++) tasks.add(threads.submit(() -> {
                for(int j=0;j<100;j++) if(budget.allow(1)) accepted.incrementAndGet();
            }));
            for(Future<?> task:tasks) task.get();
            assertEquals(40,accepted.get());
        } finally { threads.shutdownNow(); }
    }
}
