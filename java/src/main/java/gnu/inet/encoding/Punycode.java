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
 * This class offers static methods for encoding/decoding strings
 * using the Punycode algorithm.
 * <ul>
 * <li>RFC3492 Punycode
 * </ul>
 * Note that this implementation only supports 16-bit Unicode code
 * points.
 */
public class Punycode {
  private Punycode() {
    // prevent construction
  }

  /* Punycode parameters */
  private static final int TMIN = 1;
  private static final int TMAX = 26;
  private static final int BASE = 36;
  private static final int INITIAL_N = 128;
  private static final int INITIAL_BIAS = 72;
  private static final int DAMP = 700;
  private static final int SKEW = 38;
  private static final char DELIMITER = '-';

  /**
   * Punycodes a unicode string.
   *
   * @param input Unicode string.
   * @return Punycoded string.
   * @throws PunycodeException if invalid input or overflow
   */
  public static String encode(final String input) throws PunycodeException {
    int n = INITIAL_N;
    int delta = 0;
    int bias = INITIAL_BIAS;
    StringBuilder output = new StringBuilder();

    // Copy all basic code points to the output
    int b = 0;
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      if (isBasic(c)) {
        output.append(c);
        b++;
      }
    }

    // Append delimiter
    if (b > 0) {
      output.append(DELIMITER);
    }

    int h = b;
    while (h < input.length()) {
      int m = Integer.MAX_VALUE;

      // Find the minimum code point >= n
      for (int i = 0; i < input.length(); i++) {
        int c = input.charAt(i);
        if (c >= n && c < m) {
          m = c;
        }
      }

      if (m - n > (Integer.MAX_VALUE - delta) / (h + 1)) {
        throw new PunycodeException(PunycodeException.OVERFLOW);
      }
      delta = delta + (m - n) * (h + 1);
      n = m;

      for (int j = 0; j < input.length(); j++) {
        int c = input.charAt(j);
        if (c < n) {
          delta++;
          if (0 == delta) {
            throw new PunycodeException(PunycodeException.OVERFLOW);
          }
        }
        if (c == n) {
          int q = delta;

          for (int k = BASE;; k += BASE) {
            int t;
            if (k <= bias) {
              t = TMIN;
            } else if (k >= bias + TMAX) {
              t = TMAX;
            } else {
              t = k - bias;
            }
            if (q < t) {
              break;
            }
            output.append((char) digit2codepoint(t + (q - t) % (BASE - t)));
            q = (q - t) / (BASE - t);
          }

          output.append((char) digit2codepoint(q));
          bias = adapt(delta, h + 1, h == b);
          delta = 0;
          h++;
        }
      }

      delta++;
      n++;
    }

    return output.toString();
  }

  /**
   * Decode a punycoded string.
   *
   * @param input Punycode string
   * @return Unicode string.
   * @throws PunycodeException if overflow/bad input
   */
  public static String decode(final String input) throws PunycodeException {
    int n = INITIAL_N;
    int i = 0;
    int bias = INITIAL_BIAS;
    StringBuilder output = new StringBuilder();

    int d = input.lastIndexOf(DELIMITER);
    if (d > 0) {
      for (int j = 0; j < d; j++) {
        char c = input.charAt(j);
        if (!isBasic(c)) {
          throw new PunycodeException(PunycodeException.BAD_INPUT);
        }
        output.append(c);
      }
      d++;
    } else {
      d = 0;
    }

    while (d < input.length()) {
      final int oldI = i;
      int w = 1;

      for (int k = BASE;; k += BASE) {
        if (d == input.length()) {
          throw new PunycodeException(PunycodeException.BAD_INPUT);
        }
        int c = input.charAt(d++);
        int digit = codepoint2digit(c);
        if (digit > (Integer.MAX_VALUE - i) / w) {
          throw new PunycodeException(PunycodeException.OVERFLOW);
        }

        i = i + digit * w;

        int t;
        if (k <= bias) {
          t = TMIN;
        } else if (k >= bias + TMAX) {
          t = TMAX;
        } else {
          t = k - bias;
        }
        if (digit < t) {
          break;
        }
        w = w * (BASE - t);
      }

      bias = adapt(i - oldI, output.length() + 1, oldI == 0);

      if (i / (output.length() + 1) > Integer.MAX_VALUE - n) {
        throw new PunycodeException(PunycodeException.OVERFLOW);
      }

      n = n + i / (output.length() + 1);
      i = i % (output.length() + 1);
      output.insert(i, (char) n);
      i++;
    }

    return output.toString();
  }

  /**
   * Delta bias adaptation.
   * @param initialDelta delta
   * @param numPoints total number of code points encoded/decoded so far
   * @param first if first delta
   * @return bias
   * @see <a href="http://tools.ietf.org/html/rfc3492#section-3.4">RFC 3492 section 3.4</a>
   *
   */
  public static int adapt(final int initialDelta, final int numPoints, final boolean first) {
    int delta;
    if (first) {
      delta = initialDelta / DAMP;
    } else {
      delta = initialDelta / 2;
    }

    delta = delta + (delta / numPoints);

    int k = 0;
    while (delta > ((BASE - TMIN) * TMAX) / 2) {
      delta = delta / (BASE - TMIN);
      k = k + BASE;
    }

    return k + ((BASE - TMIN + 1) * delta) / (delta + SKEW);
  }

  /**
   * If the code point is a basic code point (0..0x7F).
   * @param c code point
   * @return {@code true} if basic, otherwise {@code false}
   * @see <a href="http://tools.ietf.org/html/rfc3492#section-5">RFC 3492 section 5</a>
   */
  public static boolean isBasic(final char c) {
    return c < 0x80;
  }

  /**
   * Converts digit values to code point.
   * @param d digit value
   * @return code point
   * @throws PunycodeException if bad input
   */
  public static int digit2codepoint(final int d) throws PunycodeException {
    if (d < 26) {
      // 0..25 : 'a'..'z'
      return d + 'a';
    } else if (d < 36) {
      // 26..35 : '0'..'9';
      return d - 26 + '0';
    } else {
      throw new PunycodeException(PunycodeException.BAD_INPUT);
    }
  }

  /**
   * Converts code points to digit values.
   * @param c code point
   * @return digit value
   * @throws PunycodeException if bad input
   */
  public static int codepoint2digit(final int c) throws PunycodeException {
    if (c - '0' < 10) {
      // '0'..'9' : 26..35
      return c - '0' + 26;
    } else if (c - 'a' < 26) {
      // 'a'..'z' : 0..25
      return c - 'a';
    } else {
      throw new PunycodeException(PunycodeException.BAD_INPUT);
    }
  }
}
