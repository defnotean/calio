package io.github.apace100.calio;

import net.minecraft.util.random.WeightedList;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * A filterable wrapper around a weighted list of entries.
 * <p>
 * Since {@link WeightedList} is final in MC 26.1, this class maintains its own entry list
 * and delegates to a fresh {@code WeightedList} only when picking a random element.
 * </p>
 */
public class FilterableWeightedList<U> {

    /** Internal record for storing element + weight together */
    public record Entry<U>(U data, int weight) {}

    private final List<Entry<U>> entries = new ArrayList<>();
    private Predicate<U> filter;

    public void add(U element, int weight) {
        entries.add(new Entry<>(element, weight));
    }

    public int size() {
        return entries.size();
    }

    public void addFilter(Predicate<U> filter) {
        if (hasFilter()) {
            this.filter = this.filter.and(filter);
        } else {
            setFilter(filter);
        }
    }

    public void setFilter(Predicate<U> filter) {
        this.filter = filter;
    }

    public void removeFilter() {
        this.filter = null;
    }

    public boolean hasFilter() {
        return this.filter != null;
    }

    /** Returns a stream of all elements (filtered if a filter is active). */
    public Stream<U> stream() {
        Stream<U> base = entries.stream().map(Entry::data);
        return filter != null ? base.filter(filter) : base;
    }

    /** Returns a stream of raw entries (filtered if a filter is active). */
    public Stream<Entry<U>> entryStream() {
        return entries.stream().filter(e -> filter == null || filter.test(e.data()));
    }

    /** Adds all entries from {@code other} to this list. */
    public void addAll(FilterableWeightedList<U> other) {
        other.entryStream().forEach(e -> add(e.data(), e.weight()));
    }

    @Deprecated
    public U pickRandom(java.util.Random random) {
        return pickRandom();
    }

    /**
     * Picks a random element using {@link WeightedList}, respecting the active filter.
     */
    public U pickRandom() {
        List<Entry<U>> filtered = entryStream().toList();
        if (filtered.isEmpty()) {
            throw new RuntimeException("Cannot pick from an empty FilterableWeightedList");
        }

        // Delegate to WeightedList with a fresh builder
        WeightedList.Builder<U> builder = new WeightedList.Builder<>();
        for (Entry<U> e : filtered) {
            builder.add(e.data(), e.weight());
        }
        WeightedList<U> list = builder.build();
        return list.getRandom(net.minecraft.util.RandomSource.create())
            .orElseThrow(() -> new RuntimeException("WeightedList.getRandom returned empty"));
    }

    public FilterableWeightedList<U> copy() {
        FilterableWeightedList<U> copied = new FilterableWeightedList<>();
        copied.addAll(this);
        return copied;
    }

}
