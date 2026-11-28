package com.kyroxova.lootgamesolver.client.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import com.kyroxova.lootgamesolver.client.storage.InventoryProtection;
import com.kyroxova.lootgamesolver.client.storage.TrashPolicy;

/** Tracks inventory increases observed during explorer mining, without treating old items as mined. */
public final class MinedDropTracker {

    private static final class Entry {

        final ItemStack item;
        int previous, mined;
        boolean protectedReward;

        Entry(ItemStack stack) {
            item = stack.copy();
        }
    }

    private final List<Entry> entries = new ArrayList<Entry>();
    private boolean initialized;
    private boolean dirty;

    public static final class SavedEntry {

        String item;
        int metadata, total, mined;
        boolean protectedReward;
    }

    public List<SavedEntry> snapshot() {
        List<SavedEntry> saved = new ArrayList<SavedEntry>();
        for (Entry entry : entries) {
            if (entry.mined <= 0 && !entry.protectedReward) continue;
            String id = (String) Item.itemRegistry.getNameForObject(entry.item.getItem());
            if (id == null) continue;
            SavedEntry value = new SavedEntry();
            value.item = id;
            value.metadata = entry.item.getItemDamage();
            value.total = entry.previous;
            value.mined = entry.mined;
            value.protectedReward = entry.protectedReward;
            saved.add(value);
        }
        return saved;
    }

    public void restore(ItemStack item, int total, int mined) {
        restore(item, total, mined, false);
    }

    public void restore(ItemStack item, int total, int mined, boolean protectedReward) {
        if (item == null || total <= 0 || mined < 0 || mined > total) return;
        Entry existing = find(item);
        if (existing != null) {
            existing.protectedReward |= protectedReward;
            return;
        }
        if (mined == 0 && !protectedReward) return;
        Entry entry = new Entry(item);
        entry.previous = total;
        entry.mined = mined;
        entry.protectedReward = protectedReward;
        entries.add(entry);
        initialized = true;
    }

    public void protectReward(ItemStack stack) {
        if (stack == null) return;
        Entry entry = find(stack);
        if (entry == null) {
            entry = new Entry(stack);
            entry.previous = stack.stackSize;
            entries.add(entry);
        }
        if (!entry.protectedReward) {
            entry.protectedReward = true;
            dirty = true;
        }
    }

    public boolean isProtectedReward(ItemStack stack) {
        Entry entry = stack == null ? null : find(stack);
        return entry != null && entry.protectedReward;
    }

    public boolean dirty() {
        return dirty;
    }

    public void saved() {
        dirty = false;
    }

    public static ItemStack[] inventory(Minecraft mc) {
        ItemStack[] result = new ItemStack[36];
        if (mc != null && mc.thePlayer != null)
            for (int i = 0; i < result.length; i++) result[i] = mc.thePlayer.inventory.getStackInSlot(i);
        return result;
    }

    public static boolean eligible(ItemStack stack) {
        if (stack == null || stack.hasTagCompound() || stack.hasDisplayName() || InventoryProtection.essential(stack))
            return false;
        String id = (String) Item.itemRegistry.getNameForObject(stack.getItem());
        return stack.getItem() instanceof ItemBlock
            || TrashPolicy.isBrokenBlockOrTrash(stack, id, stack.getItemDamage());
    }

    public void clear() {
        entries.clear();
        initialized = false;
        dirty = false;
    }

    public void observe(ItemStack[] inventory, boolean mining, Predicate<ItemStack> eligible) {
        for (ItemStack stack : inventory) {
            if (stack == null) continue;
            if (find(stack) == null) entries.add(new Entry(stack));
        }
        for (Entry entry : entries) {
            int old = entry.previous;
            int current = count(inventory, entry.item);
            int oldMined = entry.mined;
            if (initialized && mining && current > old && eligible.test(entry.item)) entry.mined += current - old;
            if (current < old) entry.mined = Math.max(0, entry.mined - (old - current));
            entry.mined = Math.min(entry.mined, current);
            entry.previous = current;
            if (oldMined != entry.mined || (entry.mined > 0 && old != current)) dirty = true;
        }
        initialized = true;
    }

    public boolean fullyTracked(ItemStack[] inventory, int slot) {
        if (slot < 0 || slot >= inventory.length || inventory[slot] == null) return false;
        ItemStack stack = inventory[slot];
        Entry entry = find(stack);
        if (entry == null || entry.protectedReward || entry.mined <= 0) return false;
        int untracked = Math.max(0, count(inventory, stack) - entry.mined);
        for (int i = 0; i < slot; i++) if (matches(inventory[i], stack)) untracked -= inventory[i].stackSize;
        return untracked <= 0;
    }

    public boolean containsTracked(ItemStack[] inventory, int slot) {
        if (slot < 0 || slot >= inventory.length || inventory[slot] == null) return false;
        ItemStack stack = inventory[slot];
        Entry entry = find(stack);
        if (entry == null || entry.mined == 0) return false;
        int untracked = Math.max(0, count(inventory, stack) - entry.mined);
        for (int i = 0; i < slot; i++) if (matches(inventory[i], stack)) untracked -= inventory[i].stackSize;
        return untracked < stack.stackSize;
    }

    private Entry find(ItemStack stack) {
        for (Entry entry : entries) if (matches(entry.item, stack)) return entry;
        return null;
    }

    private static int count(ItemStack[] inventory, ItemStack stack) {
        int total = 0;
        for (ItemStack item : inventory) if (matches(item, stack)) total += item.stackSize;
        return total;
    }

    private static boolean matches(ItemStack first, ItemStack second) {
        return first != null && second != null
            && first.isItemEqual(second)
            && ItemStack.areItemStackTagsEqual(first, second);
    }
}
