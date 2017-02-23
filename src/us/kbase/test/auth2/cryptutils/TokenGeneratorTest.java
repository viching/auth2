package us.kbase.test.auth2.cryptutils;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import org.apache.commons.codec.binary.Base32;
import org.junit.Test;

import us.kbase.auth2.cryptutils.RandomDataGenerator;

public class TokenGeneratorTest {
	
	public static final String PASSWORD_CHARACTERS =
			"abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789+!@$%&*";
	
	@Test
	public void getToken() throws Exception {
		// not much to test here other than it's base32 compatible and 160 bits
		final String t = new RandomDataGenerator().getToken();
		final byte[] b = new Base32().decode(t);
		assertThat("incorrect bit count", b.length, is(20));
	}
	
	@Test
	public void failCreatePassword() throws Exception {
		try {
			new RandomDataGenerator().getTemporaryPassword(7);
			fail("got bad temp pwd");
		} catch (IllegalArgumentException e) {
			assertThat("incorrect exception message", e.getMessage(),
					is("length must be > 7"));
		}
	}
	
	@Test
	public void getTempPwd() throws Exception {
		//again not much to test here other than the size is right and the characters are correct
		final char[] pwd = new RandomDataGenerator().getTemporaryPassword(8);
		assertThat("incorrect pwd length", pwd.length, is(8));
		for (final char c: pwd) {
			if (PASSWORD_CHARACTERS.indexOf(c) < 0) {
				fail("Illegal character in pwd: " + c);
			}
		}
	}

}
