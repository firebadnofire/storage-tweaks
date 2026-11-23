package org.firebadnofire.storagetweaks.inventory;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class PlannerTest {
    private Stack item(int id, int count) {
        return count == 0 ? Stack.EMPTY : new Stack("item:" + id + ":nbt", id % 2 == 0 ? "Apple" : "stone", "item:" + id, count, id == 3 ? 1 : 64);
    }
    private Snapshot snapshot(Stack... items) {
        int[] limits = new int[items.length]; Arrays.fill(limits, 64);
        return new Snapshot(Arrays.asList(items), Stack.EMPTY, limits);
    }
    private int[] range(int start, int end) { int[] ids = new int[end - start]; for (int i=0;i<ids.length;i++) ids[i]=start+i; return ids; }
    private void validate(Plan plan, int[] permitted) {
        Set<Integer> allowed = new HashSet<>(); for (int slot : permitted) allowed.add(slot);
        Snapshot previous = plan.initial;
        for (Plan.Click click : plan.clicks) {
            assertEquals(previous, click.before);
            assertTrue(allowed.contains(click.slot));
            assertEquals(Planner.counts(plan.initial), Planner.counts(click.after));
            for (int slot = 0; slot < previous.slots.size(); slot++) {
                if (slot != click.slot) assertEquals(previous.slots.get(slot), click.after.slots.get(slot));
                assertTrue(click.after.slots.get(slot).count <= click.after.limit(slot, click.after.slots.get(slot)));
            }
            previous = click.after;
        }
        assertEquals(previous, plan.result);
        assertTrue(plan.result.cursor.empty());
    }
    @Test public void transferMergesBeforeEmptyAndLeavesRemainder() {
        Snapshot before = snapshot(item(0,64), item(0,0), item(0,60));
        Plan plan = Planner.transfer(before, new int[]{0}, new int[]{1,2});
        validate(plan, range(0,3));
        assertEquals(64,plan.result.slots.get(2).count);
        assertEquals(60,plan.result.slots.get(1).count);
        Plan partial = Planner.transfer(snapshot(item(0,20),item(0,60)),new int[]{0},new int[]{1});
        assertEquals(16,partial.result.slots.get(0).count);
    }
    @Test public void differentMetadataNeverMerges() {
        Stack enchanted = new Stack("sword:enchanted", "Sword", "sword", 1,1);
        Stack damaged = new Stack("sword:damaged", "Sword", "sword", 1,1);
        Plan plan = Planner.sort(snapshot(enchanted,damaged,Stack.EMPTY), range(0,3), false);
        validate(plan,range(0,3)); assertEquals(2,Planner.counts(plan.result).size());
    }
    @Test public void randomizedSortConservesAndNeverTouchesHotbar() {
        Random random = new Random(804341);
        for(int sample=0;sample<1500;sample++) {
            Stack[] contents = new Stack[36];
            for (int i=0;i<contents.length;i++) { int id=random.nextInt(8); contents[i]=item(id,random.nextInt(id==3 ? 2 : 65)); }
            Snapshot before=snapshot(contents);
            Plan plan=Planner.sort(before,range(9,36),false); validate(plan,range(9,36));
            for(int i=10;i<36;i++) assertTrue(Planner.order(false).compare(plan.result.slots.get(i-1),plan.result.slots.get(i))<=0);
            assertTrue(Planner.sort(plan.result,range(9,36),false).clicks.isEmpty());
            assertEquals(plan.result,Planner.sort(before,range(9,36),false).result);
        }
    }
    @Test public void randomizedTransfers() {
        Random random=new Random(711);
        for(int sample=0;sample<500;sample++) {
            Stack[] items=new Stack[20];
            for(int i=0;i<20;i++){ int id=random.nextInt(4);items[i]=item(id,random.nextInt(id==3?2:65)); }
            validate(Planner.transfer(snapshot(items),range(0,10),range(10,20)),range(0,20));
        }
    }
    @Test(expected=IllegalArgumentException.class) public void cursorMustBeEmpty() {
        Planner.sort(new Snapshot(Arrays.asList(Stack.EMPTY),item(0,1),new int[]{64}),new int[]{0},false);
    }
    @Test(expected=IllegalArgumentException.class) public void overlappingGroupsRejected() {
        Planner.transfer(snapshot(item(0,1)),new int[]{0},new int[]{0});
    }
    @Test public void customNamesLimitsAndFullInventory() {
        Stack custom=new Stack("a:named","A Custom","item:a",7,16);
        Snapshot before=snapshot(item(3,1),custom,item(0,64));
        Plan plan=Planner.sort(before,range(0,3),false);validate(plan,range(0,3));
        assertEquals(custom,plan.result.slots.get(0));
        assertTrue(Planner.transfer(snapshot(item(0,64),item(1,64)),new int[]{0},new int[]{1}).clicks.isEmpty());
    }
}
