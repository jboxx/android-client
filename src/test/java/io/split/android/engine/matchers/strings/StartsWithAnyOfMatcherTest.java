package io.split.android.engine.matchers.strings;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.split.android.engine.matchers.strings.StartsWithAnyOfMatcher;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class StartsWithAnyOfMatcherTest {
    @Test
    public void works_for_sets() {
        Set<String> set = new HashSet<>();
        set.add("first");
        set.add("second");

        StartsWithAnyOfMatcher matcher = new StartsWithAnyOfMatcher(set);

        works(matcher);
    }

    @Test
    public void works_for_lists() {
        List<String> list = new ArrayList<>();
        list.add("first");
        list.add("second");

        StartsWithAnyOfMatcher matcher = new StartsWithAnyOfMatcher(list);

        works(matcher);
    }

    private void works(StartsWithAnyOfMatcher matcher) {
        assertThat(matcher.match(null, null, null, null), is(false));
        assertThat(matcher.match("", null, null, null), is(false));
        assertThat(matcher.match("foo", null, null, null), is(false));
        assertThat(matcher.match("firstsecond", null, null, null), is(true));
        assertThat(matcher.match("firt", null, null, null), is(false));
        assertThat(matcher.match("secondfirst", null, null, null), is(true));
    }


    @Test
    public void works_for_empty_paramter() {
        List<String> list = new ArrayList<>();

        StartsWithAnyOfMatcher matcher = new StartsWithAnyOfMatcher(list);

        assertThat(matcher.match(null, null, null, null), is(false));
        assertThat(matcher.match("", null, null, null), is(false));
        assertThat(matcher.match("foo", null, null, null), is(false));
        assertThat(matcher.match("firstsecond", null, null, null), is(false));
        assertThat(matcher.match("firt", null, null, null), is(false));
        assertThat(matcher.match("secondfirst", null, null, null), is(false));
    }
}
