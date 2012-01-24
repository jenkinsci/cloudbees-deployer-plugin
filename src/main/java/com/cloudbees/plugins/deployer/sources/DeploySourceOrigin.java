/*
 * The MIT License
 *
 * Copyright (c) 2011-2014, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.cloudbees.plugins.deployer.sources;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The origins from which files for deployment can be found.
 *
 * @since 4.0
 */
public enum DeploySourceOrigin {
    /**
     * The workspace of the currently executing build.
     */
    WORKSPACE(1),
    /**
     * The archived artifacts of a build.
     */
    RUN(0);

    /**
     * The priority for preference order.
     */
    private final int preference;

    /**
     * Constructor.
     *
     * @param preference The priority for preference order.
     */
    private DeploySourceOrigin(int preference) {
        this.preference = preference;
    }

    /**
     * Lazy singleton initialization.
     */
    private static final class ResourceHolder {
        /**
         * {@link DeploySourceOrigin#values()} as an immutable {@link Set}.
         */
        @NonNull
        public static final Set<DeploySourceOrigin> DEPLOYABLE_SOURCES =
                Collections.unmodifiableSet(new LinkedHashSet<DeploySourceOrigin>(Arrays.asList(values())));

        /**
         * {@link DeploySourceOrigin#values()} as an immutable {@link List} with highest
         * {@link DeploySourceOrigin#preference} first.
         */
        @NonNull
        public static final List<DeploySourceOrigin> DEPLOYABLE_SOURCES_IN_PREFERENCE_ORDER =
                createPreferenceOrder();

        /**
         * Returns {@link DeploySourceOrigin#values()} as an immutable {@link List} with highest
         * {@link DeploySourceOrigin#preference} first.
         *
         * @return {@link DeploySourceOrigin#values()} as an immutable {@link List} with highest
         *         {@link DeploySourceOrigin#preference} first.
         */
        @NonNull
        private static List<DeploySourceOrigin> createPreferenceOrder() {
            List<DeploySourceOrigin> sources = new ArrayList<DeploySourceOrigin>(Arrays.asList(values()));
            Collections.sort(sources, new Comparator<DeploySourceOrigin>() {
                public int compare(DeploySourceOrigin o1, DeploySourceOrigin o2) {
                    return o2.preference - o1.preference;
                }
            });
            return Collections.unmodifiableList(sources);
        }
    }

    /**
     * Returns {@link DeploySourceOrigin#values()} as an immutable {@link Set}
     *
     * @return {@link DeploySourceOrigin#values()} as an immutable {@link Set}
     */
    @NonNull
    public static Set<DeploySourceOrigin> all() {
        return ResourceHolder.DEPLOYABLE_SOURCES;
    }

    /**
     * Returns {@link DeploySourceOrigin#values()} as an immutable {@link List} with highest
     * {@link DeploySourceOrigin#preference} first.
     *
     * @return {@link DeploySourceOrigin#values()} as an immutable {@link List} with highest
     *         {@link DeploySourceOrigin#preference} first.
     */
    @NonNull
    public static List<DeploySourceOrigin> allInPreferenceOrder() {
        return ResourceHolder.DEPLOYABLE_SOURCES_IN_PREFERENCE_ORDER;
    }
}
