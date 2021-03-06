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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Iterator;

/**
 * Generates table classes NFKC tables.
 */
public class GenerateNFKC {
  private GenerateNFKC() {
    // prevent construction
  }

  private static String stripComment(final String in) {
    final int c = in.indexOf('#');
    if (c == -1) {
      return in;
    } else {
      return in.substring(0, c);
    }
  }

  private static String[] split(final String in, final char sep) {
    int c = 0;
    for (int i = 0; i < in.length(); i++) {
      if (in.charAt(i) == sep) {
        c++;
      }
    }

    final String[] out = new String[c + 1];
    c = 0;
    int l = 0;
    for (int i = 0; i < in.length(); i++) {
      if (in.charAt(i) == sep) {
        if (l >= i) {
          out[c] = "";
        } else {
          out[c] = in.substring(l, i);
        }
        l = i + 1;
        c++;
      }
    }
    if (l < in.length()) {
      out[c] = in.substring(l);
    }
    return out;
  }

  private static boolean isCompatibilityMapping(final CharSequence in) {
    return in.length() > 0 && in.charAt(0) == '<';
  }

  private static String stripCompatibilityTag(final String in) {
    return in.substring(in.indexOf('>') + 2);
  }

  static String toJavaString(final String in) {
    final StringBuilder out = new StringBuilder();
    final String[] chars = split(in, ' ');
    for (final String aChar : chars) {
      if ("005C".equals(aChar)) {
        out.append("\\\\");
      } else if ("0022".equals(aChar)) {
        out.append("\\\"");
      } else {
        out.append("\\u");
        out.append(aChar);
      }
    }
    return out.toString();
  }

  private static String decompose(final String in, final SortedMap<String, String> mappings) {
    final StringBuilder out = new StringBuilder();
    final String[] cols = split(in, ' ');

    for (final String col : cols) {
      if (mappings.containsKey(col)) {
        if (out.length() > 0) {
          out.append(" ");
        }
        out.append(decompose(mappings.get(col), mappings));
      } else {
        if (out.length() > 0) {
          out.append(" ");
        }
        out.append(col);
      }
    }

    return out.toString();
  }

  /**
   * Main method.
   * @param arg command line arguments
   * @throws Exception if error
   */
  public static void main(final String[] arg) throws Exception {
    // Check if the unicode files exist
    File f1 = new File("CompositionExclusions.txt");
    File f2 = new File("UnicodeData.txt");
    if (!f1.exists() || !f2.exists()) {
      System.err.println("Unable to find UnicodeData.txt or CompositionExclusions.txt.");
      System.err.println("Please download the latest version of these file from:");
      System.err.println("http://www.unicode.org/Public/UNIDATA/");
      System.exit(1);
    }

    // Read CompositionExclusions
    SortedSet<String> exclusions = readCompositionExclusions();

    // Read UnicodeData
    SortedMap<String, String> canonical = new TreeMap<String, String>();
    SortedMap<String, String> compatibility = new TreeMap<String, String>();
    SortedMap<Integer, String> combiningClasses = new TreeMap<Integer, String>();

    readUnicodeData(exclusions, canonical, compatibility, combiningClasses);

    // Recursively apply compatibility mappings
    while (true) {
      boolean replaced = false;

      for (String k : compatibility.keySet()) {
        String v = compatibility.get(k);

        String d = decompose(v, compatibility);
        if (!d.equals(v)) {
          replaced = true;
          compatibility.put(k, d);
        }
      }

      if (!replaced) {
        break;
      }
    }

    // Eliminate duplicate mappings
    SortedMap<String, Integer> compatibilityKeys = new TreeMap<String, Integer>();
    List<String> compatibilityMappings = new ArrayList<String>();

    for (String k : compatibility.keySet()) {
      String v = compatibility.get(k);

      int index = compatibilityMappings.indexOf(v);
      if (index == -1) {
        index = compatibilityMappings.size();
        compatibilityMappings.add(v);
      }
      compatibilityKeys.put(k, new Integer(index));
    }

    // Create composition tables
    SortedMap<String, Integer> firstMap = new TreeMap<String, Integer>();
    SortedMap<String, Integer> secondMap = new TreeMap<String, Integer>();

    updateCompositionTables(canonical, combiningClasses, firstMap, secondMap);

    SortedMap<String, String[]> singleFirstComposition = new TreeMap<String, String[]>();
    SortedMap<String, String[]> singleSecondComposition = new TreeMap<String, String[]>();
    SortedMap<String, SortedMap<String, String>> complexComposition = new TreeMap<String, SortedMap<String, String>>();

    int composeLookupMax = 0;
    for (String k : canonical.keySet()) {
      String v = canonical.get(k);

      String[] s = split(v, ' ');

      if (s.length == 2) {
        Integer first = firstMap.get(s[0]);
        Integer second = secondMap.get(s[1]);

        if (first.intValue() == 1) {
          singleFirstComposition.put(s[0], new String[]{s[1], k});
          composeLookupMax = Math.max(composeLookupMax, Integer.parseInt(s[0], 16));
        } else if (second.intValue() == 1) {
          singleSecondComposition.put(s[1], new String[]{s[0], k});
          composeLookupMax = Math.max(composeLookupMax, Integer.parseInt(s[1], 16));
        } else {
          if (complexComposition.containsKey(s[0])) {
            SortedMap<String, String> m = complexComposition.get(s[0]);
            if (m.containsKey(s[1])) {
              System.err.println("? ambiguous canonical mapping for " + s[0]);
              System.exit(1);
            }
            m.put(s[1], k);
          } else {
            SortedMap<String, String> m = new TreeMap<String, String>();
            m.put(s[1], k);
            complexComposition.put(s[0], m);
          }
          composeLookupMax = Math.max(composeLookupMax, Integer.parseInt(s[0], 16));
          composeLookupMax = Math.max(composeLookupMax, Integer.parseInt(s[1], 16));
        }
      }
    }

    System.out.print("Creating CombiningClass.java...");

    // Dump combining classes
    printCombiningClass(combiningClasses);

    System.out.println(" Ok.");
    System.out.print("Creating DecompositionKeys.java...");

    // Dump compatibility decomposition
    printDecompositionKeys(compatibilityKeys);

    System.out.println(" Ok.");
    System.out.print("Creating DecompositionMappings.java...");

    printDecompositionMappings(compatibilityMappings);

    System.out.println(" Ok.");
    System.out.print("Creating Composition.java...");

    // Dump canonical composition
    printCanonicalComposition(singleFirstComposition, singleSecondComposition, complexComposition, composeLookupMax);

    System.out.println(" Ok.");
  }

  private static void updateCompositionTables(final SortedMap<String, String> canonical,
                                              final SortedMap<Integer, String> combiningClasses,
                                              final SortedMap<String, Integer> firstMap,
                                              final SortedMap<String, Integer> secondMap) {
    Iterator<String> i = canonical.keySet().iterator();
    while (i.hasNext()) {
      String k = i.next();
      String v = canonical.get(k);

      String[] s = split(v, ' ');

      if (s.length == 2) {
        // If both characters have the same combining class, they
        // won't be combined (in the sequence AB, B is blocked from
        // A if both have the same combining class)
        String cc1 = combiningClasses.get(new Integer(Integer.parseInt(s[0], 16)));
        String cc2 = combiningClasses.get(new Integer(Integer.parseInt(s[1], 16)));
        if (cc1 != null || (cc1 != null && cc1.equals(cc2))) {
          // Ignore this composition
          i.remove();
          continue;
        }

        if (firstMap.containsKey(s[0])) {
          Integer c = firstMap.get(s[0]);
          firstMap.put(s[0], new Integer(c.intValue() + 1));
        } else {
          firstMap.put(s[0], new Integer(1));
        }

        if (secondMap.containsKey(s[1])) {
          Integer c = secondMap.get(s[1]);
          secondMap.put(s[1], new Integer(c.intValue() + 1));
        } else {
          secondMap.put(s[1], new Integer(1));
        }
      } else if (s.length > 2) {
        System.err.println("? wrong canonical mapping for " + k);
        System.exit(1);
      }
    }
  }

  private static SortedSet<String> readCompositionExclusions() throws IOException {
    SortedSet<String> exclusions = new TreeSet<String>();
    BufferedReader r = new BufferedReader(new FileReader("CompositionExclusions.txt"));
    String line;
    while (null != (line = r.readLine())) {
      line = stripComment(line);
      line = line.trim();
      if (line.length() == 0) {
        // Empty line
      } else if (line.length() == 4) {
        exclusions.add(line);
      } else {
        // Skip code points > 0xffff
      }
    }
    r.close();
    return exclusions;
  }

  private static void readUnicodeData(final SortedSet<String> exclusions,
                                      final SortedMap<String, String> canonical,
                                      final SortedMap<String, String> compatibility,
                                      final SortedMap<Integer, String> combiningClasses) throws IOException {
    BufferedReader r = new BufferedReader(new FileReader("UnicodeData.txt"));
    String line;
    while (null != (line = r.readLine())) {
      line = stripComment(line);
      line = line.trim();

      if (line.length() == 0) {
        // Empty line
      } else {
        String[] f = split(line, ';');

        if (f[0].length() == 4) {
          if (!f[5].equals("")) {
            if (isCompatibilityMapping(f[5])) {
              compatibility.put(f[0], stripCompatibilityTag(f[5]));
            } else {
              compatibility.put(f[0], f[5]);
              if (!exclusions.contains(f[0])) {
                canonical.put(f[0], f[5]);
              }
            }
          }
          if (!f[3].equals("0")) {
            combiningClasses.put(new Integer(Integer.parseInt(f[0], 16)), f[3]);
          }
        } else {
          // Skip code points > 0xffff
        }
      }
    }
    r.close();
  }

  private static void printDecompositionKeys(final SortedMap<String, Integer> compatibilityKeys) throws IOException {
    PrintWriter w = new PrintWriter(new FileWriter("DecompositionKeys.java"));
    w.println("/* This file is automatically generated.  DO NOT EDIT!");
    w.println("   Instead, edit GenerateNFKC.java and re-run.  */");
    w.println();
    w.println("package gnu.inet.encoding;");
    w.println();
    w.println("/**");
    w.println(" * Auto-generated class containing Unicode NFKC tables.");
    w.println(" */");
    w.println("public class DecompositionKeys");
    w.println("{");
    w.println("  public static final int[] KEYS = new int[] {");
    for (String k : compatibilityKeys.keySet()) {
      int index = (compatibilityKeys.get(k)).intValue();
      w.println("    '\\u" + k + "', " + index + ",");
    }
    w.println("  };");
    w.println("}");
    w.close();
  }

  private static void printDecompositionMappings(final List<String> compatibilityMappings) throws IOException {
    PrintWriter w = new PrintWriter(new FileWriter("DecompositionMappings.java"));
    w.println("/* This file is automatically generated.  DO NOT EDIT!");
    w.println("   Instead, edit GenerateNFKC.java and re-run.  */");
    w.println();
    w.println("package gnu.inet.encoding;");
    w.println();
    w.println("/**");
    w.println(" * Auto-generated class containing Unicode NFKC tables.");
    w.println(" */");
    w.println("public class DecompositionMappings");
    w.println("{");
    w.println("  public static final String[] MAPPINGS = new String[] {");
    for (String m : compatibilityMappings) {
      w.println("    \"" + toJavaString(m) + "\",");
    }
    w.println("  };");
    w.println("}");
    w.close();
  }

  private static void printCombiningClass(final SortedMap<Integer, String> combiningClasses) throws IOException {
    PrintWriter w = new PrintWriter(new FileWriter("CombiningClass.java"));
    w.println("/* This file is automatically generated.  DO NOT EDIT!");
    w.println("   Instead, edit GenerateNFKC.java and re-run.  */");
    w.println();
    w.println("package gnu.inet.encoding;");
    w.println();
    w.println("/**");
    w.println(" * Auto-generated class containing Unicode NFKC tables.");
    w.println(" */");
    w.println("public class CombiningClass");
    w.println("{");
    w.println("  public static final int[][] CLASSES = new int[][] {");
    StringBuilder index = new StringBuilder();

    int count = 0;

    for (int i = 0; i < 256; i++) {
      boolean empty = true;

      StringBuilder page = new StringBuilder();
      page.append("    /* Page ").append(i).append(" */\n    {");

      for (int j = 0; j < 256; j++) {
        Integer c = new Integer((i << 8) + j);
        String cc = combiningClasses.get(c);

        if (0 == (j & 31)) {
          page.append("\n      ");
        }
        if (cc == null) {
          page.append("0, ");
        } else {
          page.append(cc).append(", ");
          empty = false;
        }
      }
      page.append("\n    },");

      index.append("    ");

      if (!empty) {
        w.println(page.toString());
        index.append(count++);
        index.append(",\n");
      } else {
        index.append("-1,\n");
      }
    }
    w.println("  };\n");

    w.println("  public static final int[] CLASSES_IDX = new int[] {");
    w.print(index.toString());
    w.println("  };");
    w.println("}");
    w.close();
  }

  private static void printCanonicalComposition(final SortedMap<String, String[]> singleFirstComposition,
                                                final SortedMap<String, String[]> singleSecondComposition,
                                                final SortedMap<String, SortedMap<String, String>> complexComposition,
                                                final int composeLookupMax) throws IOException {
    PrintWriter w = new PrintWriter(new FileWriter("Composition.java"));
    w.println("/* This file is automatically generated.  DO NOT EDIT!");
    w.println("   Instead, edit GenerateNFKC.java and re-run.  */");
    w.println();
    w.println("package gnu.inet.encoding;");
    w.println();
    w.println("/**");
    w.println(" * Auto-generated class containing Unicode NFKC tables.");
    w.println(" */");
    w.println("public class Composition");
    w.println("{");

    Iterator<String> i;
    int index = 0;

    SortedMap<Integer, Integer> indices = new TreeMap<Integer, Integer>();

    i = complexComposition.keySet().iterator();
    while (i.hasNext()) {
      String s0 = i.next();
      indices.put(new Integer(Integer.parseInt(s0, 16)), new Integer(index));
      index++;
    }

    int multiSecondStart = index;

    w.println("  public static final char[][] MULTI_FIRST = new char[][] {");
    i = complexComposition.keySet().iterator();
    while (i.hasNext()) {
      String s0 = i.next();
      SortedMap<String, String> m = complexComposition.get(s0);

      SortedMap<Integer, String> line = new TreeMap<Integer, String>();
      int maxIndex = 1;

      for (String s1 : m.keySet()) {
        String k = m.get(s1);

        Integer s1i = new Integer(Integer.parseInt(s1, 16));

        if (!indices.containsKey(s1i)) {
          indices.put(s1i, new Integer(index));
          index++;
        }
        line.put(indices.get(s1i), k);
        maxIndex = Math.max(maxIndex, (indices.get(s1i)).intValue());
      }

      w.print("    { ");
      for (int j = multiSecondStart; j <= maxIndex; j++) {
        if (line.containsKey(new Integer(j))) {
          String s = line.get(new Integer(j));
          w.print("'" + toJavaString(s) + "', ");
        } else {
          w.print("       0, ");
        }
      }
      w.println("},");
    }
    w.println("  };");

    int singleFirstStart = index;

    w.println("  public static final char[][] SINGLE_FIRST = new char[][] {");
    i = singleFirstComposition.keySet().iterator();
    while (i.hasNext()) {
      String k = i.next();
      String[] v = (singleFirstComposition.get(k));
      w.println("    { '" + toJavaString(v[0]) + "', '" + toJavaString(v[1]) + "' },");

      if (indices.containsKey(new Integer(Integer.parseInt(k, 16)))) {
        System.out.println(k + " already indexed!");
      }

      indices.put(new Integer(Integer.parseInt(k, 16)), new Integer(index));
      index++;
    }
    w.println("  };");

    int singleSecondStart = index;

    w.println("  public static final char[][] SINGLE_SECOND = new char[][] {");
    i = singleSecondComposition.keySet().iterator();
    while (i.hasNext()) {
      String k = i.next();
      String[] v = (singleSecondComposition.get(k));
      w.println("    { '" + toJavaString(v[0]) + "', '" + toJavaString(v[1]) + "' },");

      indices.put(new Integer(Integer.parseInt(k, 16)), new Integer(index));
      index++;
    }
    w.println("  };");

    w.println("  public static final int MULTI_SECOND_START = " + multiSecondStart + ";");
    w.println("  public static final int SINGLE_FIRST_START = " + singleFirstStart + ";");
    w.println("  public static final int SINGLE_SECOND_START = " + singleSecondStart + ";");

    StringBuilder compositionPages = new StringBuilder();

    w.println("  public static final int[] COMPOSE_PAGE = new int[] {");
    int pageCount = 0;
    for (int j = 0; j * 256 < composeLookupMax + 255; j++) {
      boolean empty = true;
      StringBuilder page = new StringBuilder();
      for (int k = 0; k < 256; k++) {
        if (k % 16 == 0) {
          page.append("\n      ");
        }
        if (indices.containsKey(new Integer(j * 256 + k))) {
          page.append(indices.get(new Integer(j * 256 + k)));
          page.append(", ");
          empty = false;
        } else {
          page.append("-1, ");
        }
      }

      if (empty) {
        w.println("    -1,");
      } else {
        w.println("    " + pageCount + ",");
        compositionPages.append("    {");
        compositionPages.append(page);
        compositionPages.append("\n    },\n");
        pageCount++;
      }
    }
    w.println("  };");

    w.println("  public static final int[][] COMPOSE_DATA = new int[][] {");
    w.print(compositionPages);
    w.println("  };");

    w.println("}");
    w.close();
  }
}
