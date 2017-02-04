package us.kbase.auth2.lib;

import us.kbase.auth2.lib.exceptions.IllegalParameterException;
import us.kbase.auth2.lib.exceptions.MissingParameterException;

/** Miscellaneous utility functions including checking strings for existence and length,
 * safely adding numbers, and clearing byte arrays.
 * @author gaprice@lbl.gov
 *
 */
public class Utils {

	//TODO TESTS
	
	/** Check that a string is non-null and has at least one non-whitespace character.
	 * @param s the string to check.
	 * @param name the name of the string to use in any error messages.
	 * @throws MissingParameterException if the string fails the check.
	 */
	public static void checkString(final String s, final String name)
			throws MissingParameterException {
		try {
			checkString(s, name, -1);
		} catch (IllegalParameterException e) {
			throw new RuntimeException("Programming error: " +
					e.getMessage(), e);
		}
	}
	
	/** Check that a string is non-null, has at least one non-whitespace character, and is below
	 * a specified length.
	 * @param s the string to check.
	 * @param name the name of the string to use in any error messages.
	 * @param max the maximum number of code points in the string. If 0 or less, the length is not
	 * checked.
	 * @throws MissingParameterException if the string is null or contains only whitespace
	 * characters.
	 * @throws IllegalParameterException if the string is too long.
	 */
	public static void checkString(
			final String s,
			final String name,
			final int max)
			throws MissingParameterException, IllegalParameterException {
		if (s == null || s.trim().isEmpty()) {
			throw new MissingParameterException(name);
		}
		
		if (max > 0 && codePoints(s) > max) {
			throw new IllegalParameterException(
					name + " size greater than limit " + max);
		}
	}
	
	private static int codePoints(final String s) {
		return s.codePointCount(0, s.length());
	}
	
	/** As checkString(), but doesn't throw a checked exception.
	 * @param s the string to check.
	 * @param name the name of the string to use in any error messages.
	 */
	public static void checkStringNoCheckedException(
			final String s,
			final String name) {
		if (s == null || s.trim().isEmpty()) {
			throw new IllegalArgumentException("Missing argument: " + name);
		}
	}

	/** Adds two longs, returning Long.MAX_VALUE if the calculation overflows.
	 * @param a a long.
	 * @param b a long.
	 * @return Long.MAX_VALUE if a + b > Long.MAX_VALUE else a + b
	 */
	public static long addLong(final long a, final long b) {
		final long c;
		if (Long.MAX_VALUE - a < b) {
			c = Long.MAX_VALUE;
		} else {
			c = a + b;
		}
		return c;
	}
	
	/** Sets all the elements of a byte array to 0.
	 * @param bytes the byte array to clear.
	 */
	public static void clear(final byte[] bytes) {
		for (int i = 0; i < bytes.length; i++) {
			bytes[i] = 0;
		}
	}
}
