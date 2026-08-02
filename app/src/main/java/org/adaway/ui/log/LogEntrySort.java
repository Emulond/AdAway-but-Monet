package org.adaway.ui.log;

import androidx.annotation.DrawableRes;

import org.adaway.R;

import java.util.Comparator;

/**
 * This enumerate represents the kind of sort available for {@link LogEntry}.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public enum LogEntrySort {
    ALPHABETICAL {
        @Override
        int getName() {
            return R.string.log_sort_alphabetical;
        }

        @Override
        int getIcon() {
            return R.drawable.baseline_sort_by_alpha_24;
        }

        @Override
        Comparator<LogEntry> comparator() {
            return LogEntry::compareTo;
        }
    },
    TOP_LEVEL_DOMAIN {
        @Override
        int getName() {
            return R.string.log_sort_top_level_domain;
        }

        @Override
        int getIcon() {
            return R.drawable.baseline_sort_24;
        }

        @Override
        Comparator<LogEntry> comparator() {
            return (entry1, entry2) -> {
                String[] split1 = entry1.getHost().split("\\.");
                String[] split2 = entry2.getHost().split("\\.");

                int index1 = split1.length - 1;
                int index2 = split2.length - 1;

                while (index1 >= 0 && index2 >= 0) {
                    String part1 = split1[index1];
                    String part2 = split2[index2];

                    int partCompare = part1.compareTo(part2);
                    if (partCompare != 0) {
                        return partCompare;
                    }

                    index1--;
                    index2--;
                }

                return index1 < 0 ? (index2 < 0 ? 0 : -1) : +1;
            };
        }
    };

    /**
     * Get the sort name.
     *
     * @return The sort name resource identifier.
     */
    abstract int getName();

    /**
     * Get the icon standing for the sort in use.
     *
     * @return The sort icon resource identifier.
     */
    @DrawableRes
    abstract int getIcon();

    /**
     * Get the sort comparator.
     *
     * @return The sort comparator.
     */
    abstract Comparator<LogEntry> comparator();
}
