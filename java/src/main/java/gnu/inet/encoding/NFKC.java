/* Copyright (C) 2004-2013 Free Software Foundation, Inc.
   Author: Oliver Hitz

   This file is part of GNU Libidn.

   GNU Libidn is free software: you can redistribute it and/or
   modify it under the terms of either:

     * the GNU Lesser General Public License as published by the Free
       Software Foundation; either version 3 of the License, or (at
       your option) any later version.

   or

     * the GNU General Public License as published by the Free
       Software Foundation; either version 2 of the License, or (at
       your option) any later version.

   or both in parallel, as here.

   GNU Libidn is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
   General Public License for more details.

   You should have received copies of the GNU General Public License and
   the GNU Lesser General Public License along with this program.  If
   not, see <http://www.gnu.org/licenses/>. */

package gnu.inet.encoding;

/**
 * This class offers static methods for Unicode NFKC string normalization.
 */
public class NFKC {
  private NFKC() {
    // avoid construction
  }

  /**
   * Applies NFKC normalization to a string.
   *
   * @param in The string to normalize.
   * @return An NFKC normalized string.
   */
  public static String normalizeNFKC(final String in) {
    final int len = in.length();
    final StringBuilder out = new StringBuilder(len);

    for (int i = 0; i < len; i++) {
      final char code = in.charAt(i);

      // In Unicode 3.0, Hangul was defined as the block from U+AC00
      // to U+D7A3, however, since Unicode 3.2 the block extends until
      // U+D7AF. The decomposeHangul function only decomposes until
      // U+D7A3. Should this be changed?
      if (code >= 0xAC00 && code <= 0xD7AF) {
        out.append(decomposeHangul(code));
      } else {
        int index = decomposeIndex(code);
        if (index == -1) {
          out.append(code);
        } else {
          out.append(DecompositionMappings.MAPPINGS[index]);
        }
      }
    }

    // Bring the StringBuilder into canonical order.
    canonicalOrdering(out);

    // Do the canonical composition.
    int lastCc = 0;
    int lastStart = 0;

    for (int i = 0; i < out.length(); i++) {
      int cc = combiningClass(out.charAt(i));

      if (i > 0 && (lastCc == 0 || lastCc != cc)) {
          // Try to combine characters
          char a = out.charAt(lastStart);
          char b = out.charAt(i);

          int c = compose(a, b);

          if (c != -1) {
              out.setCharAt(lastStart, (char) c);
              out.deleteCharAt(i);
              i--;

          if (i == lastStart) {
            lastCc = 0;
          } else {
            lastCc = combiningClass(out.charAt(i - 1));
          }
          continue;
        }
      }

      if (cc == 0) {
          lastStart = i;
      }

      lastCc = cc;
    }

    return out.toString();
  }


  /**
   * Returns the index inside the decomposition table, implemented
   * using a binary search.
   *
   * @param c Character to look up.
   * @return Index if found, -1 otherwise.
   */
  static int decomposeIndex(final char c) {
    int start = 0;
    int end = DecompositionKeys.KEYS.length / 2;

    while (true) {
      int half = (start + end) / 2;
      int code = DecompositionKeys.KEYS[half * 2];

      if (c == code) {
        return DecompositionKeys.KEYS[half * 2 + 1];
      }
      if (half == start) {
        // Character not found
        return -1;
      } else if (c > code) {
        start = half;
      } else {
        end = half;
      }
    }
  }

  /**
   * Returns the combining class of a given character.
   *
   * @param c The character.
   * @return The combining class.
   */
  static int combiningClass(final char c) {
    int h = c >> 8;
    int l = c & 0xff;

    int i = CombiningClass.CLASSES_IDX[h];
    if (i > -1) {
      return CombiningClass.CLASSES[i][l];
    } else {
      return 0;
    }
  }

  /**
   * Rearranges characters in a StringBuilder in order to respect the
   * canonical ordering properties.
   *
   * @param in The StringBuilder to rearrange.
   */
  static void canonicalOrdering(final StringBuilder in) {
    if (in.length() == 0) {
      return;
    }

    boolean isOrdered = false;

    while (!isOrdered) {
      isOrdered = true;

      int lastCC = combiningClass(in.charAt(0));

      for (int i = 0; i < in.length() - 1; i++) {
        int nextCC = combiningClass(in.charAt(i + 1));
        if (nextCC != 0 && lastCC > nextCC) {
          for (int j = i + 1; j > 0; j--) {
            if (combiningClass(in.charAt(j - 1)) <= nextCC) {
              break;
            }
            char t = in.charAt(j);
            in.setCharAt(j, in.charAt(j - 1));
            in.setCharAt(j - 1, t);
            isOrdered = false;
          }
          nextCC = lastCC;
        }
        lastCC = nextCC;
      }
    }
  }

  /**
   * Returns the index inside the composition table.
   *
   * @param a Character to look up.
   * @return Index if found, -1 otherwise.
   */
  static int composeIndex(final char a) {
    if (a >> 8 >= Composition.COMPOSE_PAGE.length) {
      return -1;
    }
    int ap = Composition.COMPOSE_PAGE[a >> 8];
    if (ap == -1) {
      return -1;
    }
    return Composition.COMPOSE_DATA[ap][a & 0xff];
  }

  /**
   * Tries to compose two characters canonically.
   *
   * @param a First character.
   * @param b Second character.
   * @return The composed character or -1 if no composition could be
   * found.
   */
  private static int compose(final char a, final char b) {
    final int h = composeHangul(a, b);
    if (h != -1) {
      return h;
    }

    final int ai = composeIndex(a);

    if (ai >= Composition.SINGLE_FIRST_START && ai < Composition.SINGLE_SECOND_START) {
      if (b == Composition.SINGLE_FIRST[ai - Composition.SINGLE_FIRST_START][0]) {
        return Composition.SINGLE_FIRST[ai - Composition.SINGLE_FIRST_START][1];
      } else {
        return -1;
      }
    }

    final int bi = composeIndex(b);

    if (bi >= Composition.SINGLE_SECOND_START) {
      if (a == Composition.SINGLE_SECOND[bi - Composition.SINGLE_SECOND_START][0]) {
        return Composition.SINGLE_SECOND[bi - Composition.SINGLE_SECOND_START][1];
      } else {
        return -1;
      }
    }

    if (ai >= 0 && ai < Composition.MULTI_SECOND_START
        && bi >= Composition.MULTI_SECOND_START && bi < Composition.SINGLE_FIRST_START) {
      final char[] f = Composition.MULTI_FIRST[ai];

      if (bi - Composition.MULTI_SECOND_START < f.length) {
        final char r = f[bi - Composition.MULTI_SECOND_START];
        if (r == 0) {
          return -1;
        } else {
          return r;
        }
      }
    }

    return -1;
  }

  /*
   * Entire hangul code copied from:
   * http://www.unicode.org/unicode/reports/tr15/
   *
   * Several hangul specific constants
   */
  private static final int S_BASE = 0xAC00;
  private static final int L_BASE = 0x1100;
  private static final int V_BASE = 0x1161;
  private static final int T_BASE = 0x11A7;
  private static final int L_COUNT = 19;
  private static final int V_COUNT = 21;
  private static final int T_COUNT = 28;
  private static final int N_COUNT = V_COUNT * T_COUNT;
  private static final int S_COUNT = L_COUNT * N_COUNT;

  /**
   * Decomposes a hangul character.
   *
   * @param s A character to decompose.
   * @return A string containing the hangul decomposition of the input
   * character. If no hangul decomposition can be found, a string
   * containing the character itself is returned.
   */
  private static String decomposeHangul(final char s) {
    final int sIndex = s - S_BASE;
    if (sIndex < 0 || sIndex >= S_COUNT) {
      return String.valueOf(s);
    }
    final StringBuilder result = new StringBuilder(3);
    final int l = L_BASE + sIndex / N_COUNT;
    final int v = V_BASE + (sIndex % N_COUNT) / T_COUNT;
    final int t = T_BASE + sIndex % T_COUNT;
    result.append((char) l);
    result.append((char) v);
    if (t != T_BASE) {
      result.append((char) t);
    }
    return result.toString();
  }

  /**
   * Composes two hangul characters.
   *
   * @param a First character.
   * @param b Second character.
   * @return Returns the composed character or -1 if the two
   * characters cannot be composed.
   */
  private static int composeHangul(final char a, final char b) {
    // 1. check to see if two current characters are L and V
    final int lIndex = a - L_BASE;
    if (0 <= lIndex && lIndex < L_COUNT) {
      final int vIndex = b - V_BASE;
      if (0 <= vIndex && vIndex < V_COUNT) {
        // make syllable of form LV
        return S_BASE + (lIndex * V_COUNT + vIndex) * T_COUNT;
      }
    }

    // 2. check to see if two current characters are LV and T
    final int sIndex = a - S_BASE;
    if (0 <= sIndex && sIndex < S_COUNT && (sIndex % T_COUNT) == 0) {
      final int tIndex = b - T_BASE;
      if (0 <= tIndex && tIndex <= T_COUNT) {
        // make syllable of form LVT
        return a + tIndex;
      }
    }
    return -1;
  }
}
