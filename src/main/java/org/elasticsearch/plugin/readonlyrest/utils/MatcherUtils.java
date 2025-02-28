/*
 *    This file is part of ReadonlyREST.
 *
 *    ReadonlyREST is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation, either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    ReadonlyREST is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with ReadonlyREST.  If not, see http://www.gnu.org/licenses/
 */

package org.elasticsearch.plugin.readonlyrest.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Stack;
import java.util.regex.Pattern;

public class MatcherUtils {
    
    private static final int NOT_FOUND = -1;

    public static boolean matchAny(final String[] pattern, final String[] candidate) {

        for (int i = 0; i < pattern.length; i++) {
            final String string = pattern[i];
            if (matchAny(string, candidate)) {
                return true;
            }
        }

        return false;
    }

    public static boolean matchAll(final String[] pattern, final String[] candidate) {

        for (int i = 0; i < candidate.length; i++) {
            final String string = candidate[i];
            if (!matchAny(pattern, string)) {
                return false;
            }
        }

        return true;
    }

    public static boolean matchAny(final String pattern, final String[] candidate) {

        for (int i = 0; i < candidate.length; i++) {
            final String string = candidate[i];
            if (match(pattern, string)) {
                return true;
            }
        }

        return false;
    }

    public static List<String> getMatchAny(final String pattern, final String[] candidate) {

        final List<String> matches = new ArrayList<String>(candidate.length);

        for (int i = 0; i < candidate.length; i++) {
            final String string = candidate[i];
            if (match(pattern, string)) {
                matches.add(string);
            }
        }

        return matches;
    }

    public static boolean matchAny(final String pattern[], final String candidate) {

        for (int i = 0; i < pattern.length; i++) {
            final String string = pattern[i];
            if (match(string, candidate)) {
                return true;
            }
        }

        return false;
    }
    
    public static boolean matchAny(final Collection<String> pattern, final String candidate) {

        for (String string: pattern) {
            if (match(string, candidate)) {
                return true;
            }
        }

        return false;
    }

    public static boolean match(final String pattern, final String candidate) {

        if (pattern == null || candidate == null) {
            return false;
        }

        if (pattern.startsWith("/") && pattern.endsWith("/")) {
            // regex
            return Pattern.matches("^"+pattern.substring(1, pattern.length() - 1)+"$", candidate);
        } else if (pattern.length() == 1 && pattern.charAt(0) == '*') {
            return true;
        } else if (pattern.indexOf('?') == NOT_FOUND && pattern.indexOf('*') == NOT_FOUND) {
            return pattern.equals(candidate);
        } else {
            return simpleWildcardMatch(pattern, candidate);
        }
    }

    public static boolean containsWildcard(final String pattern) {
        if (pattern != null
                && (pattern.indexOf("*") > NOT_FOUND || pattern.indexOf("?") > NOT_FOUND || (pattern.startsWith("/") && pattern
                        .endsWith("/")))) {
            return true;
        }

        return false;
    }
    
    
    //All code below is copied (and slightly modified) from Apache Commons IO
    
    /*
     * Licensed to the Apache Software Foundation (ASF) under one or more
     * contributor license agreements.  See the NOTICE file distributed with
     * this work for additional information regarding copyright ownership.
     * The ASF licenses this file to You under the Apache License, Version 2.0
     * (the "License"); you may not use this file except in compliance with
     * the License.  You may obtain a copy of the License at
     *
     *      http://www.apache.org/licenses/LICENSE-2.0
     *
     * Unless required by applicable law or agreed to in writing, software
     * distributed under the License is distributed on an "AS IS" BASIS,
     * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     * See the License for the specific language governing permissions and
     * limitations under the License.
     */


    /**
     * Checks a filename to see if it matches the specified wildcard matcher
     * allowing control over case-sensitivity.
     * <p>
     * The wildcard matcher uses the characters '?' and '*' to represent a
     * single or multiple (zero or more) wildcard characters.
     * N.B. the sequence "*?" does not work properly at present in match strings.
     *
     * @param candidate  the filename to match on
     * @param pattern  the wildcard string to match against
     * @return true if the filename matches the wilcard string
     * @since 1.3
     */
    private static boolean simpleWildcardMatch(final String pattern, final String candidate) {
        if (candidate == null && pattern == null) {
            return true;
        }
        if (candidate == null || pattern == null) {
            return false;
        }

        final String[] wcs = splitOnTokens(pattern);
        boolean anyChars = false;
        int textIdx = 0;
        int wcsIdx = 0;
        final Stack<int[]> backtrack = new Stack<>();

        // loop around a backtrack stack, to handle complex * matching
        do {
            if (backtrack.size() > 0) {
                final int[] array = backtrack.pop();
                wcsIdx = array[0];
                textIdx = array[1];
                anyChars = true;
            }

            // loop whilst tokens and text left to process
            while (wcsIdx < wcs.length) {

                if (wcs[wcsIdx].equals("?")) {
                    // ? so move to next text char
                    textIdx++;
                    if (textIdx > candidate.length()) {
                        break;
                    }
                    anyChars = false;

                } else if (wcs[wcsIdx].equals("*")) {
                    // set any chars status
                    anyChars = true;
                    if (wcsIdx == wcs.length - 1) {
                        textIdx = candidate.length();
                    }

                } else {
                    // matching text token
                    if (anyChars) {
                        // any chars then try to locate text token
                        textIdx = checkIndexOf(candidate, textIdx, wcs[wcsIdx]);
                        if (textIdx == NOT_FOUND) {
                            // token not found
                            break;
                        }
                        final int repeat = checkIndexOf(candidate, textIdx + 1, wcs[wcsIdx]);
                        if (repeat >= 0) {
                            backtrack.push(new int[] {wcsIdx, repeat});
                        }
                    } else {
                        // matching from current position
                        if (!checkRegionMatches(candidate, textIdx, wcs[wcsIdx])) {
                            // couldnt match token
                            break;
                        }
                    }

                    // matched text token, move text index to end of matched token
                    textIdx += wcs[wcsIdx].length();
                    anyChars = false;
                }

                wcsIdx++;
            }

            // full match
            if (wcsIdx == wcs.length && textIdx == candidate.length()) {
                return true;
            }

        } while (backtrack.size() > 0);

        return false;
    }

    /**
     * Splits a string into a number of tokens.
     * The text is split by '?' and '*'.
     * Where multiple '*' occur consecutively they are collapsed into a single '*'.
     *
     * @param text  the text to split
     * @return the array of tokens, never null
     */
    private static String[] splitOnTokens(final String text) {
        // used by wildcardMatch
        // package level so a unit test may run on this

        if (text.indexOf('?') == NOT_FOUND && text.indexOf('*') == NOT_FOUND) {
            return new String[] { text };
        }

        final char[] array = text.toCharArray();
        final ArrayList<String> list = new ArrayList<>();
        final StringBuilder buffer = new StringBuilder();
        char prevChar = 0;
        for (final char ch : array) {
            if (ch == '?' || ch == '*') {
                if (buffer.length() != 0) {
                    list.add(buffer.toString());
                    buffer.setLength(0);
                }
                if (ch == '?') {
                    list.add("?");
                } else if (prevChar != '*') {// ch == '*' here; check if previous char was '*'
                    list.add("*");
                }
            } else {
                buffer.append(ch);
            }
            prevChar = ch;
        }
        if (buffer.length() != 0) {
            list.add(buffer.toString());
        }

        return list.toArray( new String[ list.size() ] );
    }
    
    /**
     * Checks if one string contains another starting at a specific index using the
     * case-sensitivity rule.
     * <p>
     * This method mimics parts of {@link String#indexOf(String, int)}
     * but takes case-sensitivity into account.
     *
     * @param str  the string to check, not null
     * @param strStartIndex  the index to start at in str
     * @param search  the start to search for, not null
     * @return the first index of the search String,
     *  -1 if no match or {@code null} string input
     * @throws NullPointerException if either string is null
     * @since 2.0
     */
    private static int checkIndexOf(final String str, final int strStartIndex, final String search) {
        final int endIndex = str.length() - search.length();
        if (endIndex >= strStartIndex) {
            for (int i = strStartIndex; i <= endIndex; i++) {
                if (checkRegionMatches(str, i, search)) {
                    return i;
                }
            }
        }
        return -1;
    }
    
    /**
     * Checks if one string contains another at a specific index using the case-sensitivity rule.
     * <p>
     * This method mimics parts of {@link String#regionMatches(boolean, int, String, int, int)}
     * but takes case-sensitivity into account.
     *
     * @param str  the string to check, not null
     * @param strStartIndex  the index to start at in str
     * @param search  the start to search for, not null
     * @return true if equal using the case rules
     * @throws NullPointerException if either string is null
     */
    private static boolean checkRegionMatches(final String str, final int strStartIndex, final String search) {
        return str.regionMatches(false, strStartIndex, search, 0, search.length());
    }

}
