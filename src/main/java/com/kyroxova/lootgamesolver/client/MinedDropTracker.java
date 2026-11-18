package com.kyroxova.lootgamesolver.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/** Tracks inventory increases observed during explorer mining, without treating old items as mined. */
final class MinedDropTracker {

    private static final class Entry {

        final ItemStack item;
        int previous, mined;

        Entry(ItemStack stack) {
            item = stack.copy();
        }
    }

    private final List<Entry> entries = new ArrayList<Entry>();
    private boolean initialized;
    private boolean dirty;

    static final class SavedEntry {

        String item;
        int metadata, total, mined;
    }

    List<SavedEntry> snapshot() {
        List<SavedEntry> saved = new ArrayList<SavedEntry>();
        for (Entry entry : entries) {
            if (entry.mined <= 0) continue;
            String id = (String) Item.itemRegistry.getNameForObject(entry.item.getItem());
            if (id == null) continue;
            SavedEntry value = new SavedEntry();
            value.item = id;
            value.metadata = entry.item.getItemDamage();
            value.total = entry.previous;
            value.mined = entry.mined;
            saved.add(value);
        }
        return saved;
    }

    void restore(ItemStack item, int total, int mined) {
        if (item == null || total <= 0 || mined <= 0 || mined > total || find(item) != null) return;
        Entry entry = new Entry(item);
        entry.previous = total;
        entry.mined = mined;
        entries.add(entry);
        initialized = true;
    }

    boolean dirty() {
        return dirty;
    }

    void saved() {
        dirty = false;
    }

    static ItemStack[] inventory(Minecraft mc) {
        ItemStack[] result = new ItemStack[36];
        if (mc != null && mc.thePlayer != null)
            for (int i = 0; i < result.length; i++) result[i] = mc.thePlayer.inventory.getStackInSlot(i);
        return result;
    }

    static boolean eligible(ItemStack stack) {
        if (stack == null || stack.hasTagCompound() || stack.hasDisplayName() || InventoryProtection.essential(stack))
            return false;
        String id = (String) Item.itemRegistry.getNameForObject(stack.getItem());
        return stack.getItem() instanceof ItemBlock || TrashPolicy.isOreOrDust(stack, id);
    }

    void clear() {
        entries.clear();
        initialized = false;
        dirty = false;
    }

    void observe(ItemStack[] inventory, boolean mining, Predicate<ItemStack> eligible) {
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

    boolean fullyTracked(ItemStack[] inventory, int slot) {
        if (slot < 0 || slot >= inventory.length || inventory[slot] == null) return false;
        ItemStack stack = inventory[slot];
        Entry entry = find(stack);
        if (entry == null) return false;
        int untracked = Math.max(0, count(inventory, stack) - entry.mined);
        for (int i = 0; i < slot; i++) if (matches(inventory[i], stack)) untracked -= inventory[i].stackSize;
        return untracked <= 0 && entry.mined >= stack.stackSize;
    }

    boolean containsTracked(ItemStack[] inventory, int slot) {
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
