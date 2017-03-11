package us.kbase.test.auth2.lib;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static us.kbase.test.auth2.TestCommon.set;
import static us.kbase.test.auth2.lib.AuthenticationTester.initTestAuth;

import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import org.junit.Test;

import com.google.common.base.Optional;

import us.kbase.auth2.cryptutils.RandomDataGenerator;
import us.kbase.auth2.lib.AuthUser;
import us.kbase.auth2.lib.Authentication;
import us.kbase.auth2.lib.DisplayName;
import us.kbase.auth2.lib.EmailAddress;
import us.kbase.auth2.lib.Role;
import us.kbase.auth2.lib.TokenName;
import us.kbase.auth2.lib.UserDisabledState;
import us.kbase.auth2.lib.UserName;
import us.kbase.auth2.lib.exceptions.DisabledUserException;
import us.kbase.auth2.lib.exceptions.ErrorType;
import us.kbase.auth2.lib.exceptions.InvalidTokenException;
import us.kbase.auth2.lib.exceptions.NoSuchTokenException;
import us.kbase.auth2.lib.exceptions.NoSuchUserException;
import us.kbase.auth2.lib.exceptions.UnauthorizedException;
import us.kbase.auth2.lib.storage.AuthStorage;
import us.kbase.auth2.lib.token.HashedToken;
import us.kbase.auth2.lib.token.IncomingToken;
import us.kbase.auth2.lib.token.TokenSet;
import us.kbase.auth2.lib.token.TokenType;
import us.kbase.test.auth2.TestCommon;
import us.kbase.test.auth2.lib.AuthenticationTester.TestAuth;

public class AuthenticationTokenTest {
	
	/* tests token get, create, revoke, and getBare. */
	
	private static final HashedToken TOKEN1;
	private static final HashedToken TOKEN2;
	static {
		try {
		TOKEN1 = new HashedToken(TokenType.LOGIN, Optional.absent(),
				UUID.randomUUID(), "whee", new UserName("foo"), Instant.ofEpochMilli(3000),
				Instant.ofEpochMilli(4000));
		TOKEN2 = new HashedToken(TokenType.EXTENDED_LIFETIME, Optional.of(new TokenName("baz")),
				UUID.randomUUID(), "whee1", new UserName("foo"), Instant.ofEpochMilli(5000),
				Instant.ofEpochMilli(6000));
		} catch (Exception e) {
			throw new RuntimeException("Fix yer tests newb", e);
		}
	}
	
	@Test
	public void getToken() throws Exception {
		final TestAuth testauth = initTestAuth();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		
		final IncomingToken t = new IncomingToken("foobar");
		
		final Instant now = Instant.now();
		final UUID id = UUID.randomUUID();
		
		final HashedToken expected = new HashedToken(TokenType.LOGIN, Optional.absent(), id,
				"whee", new UserName("foo"), now, now);
		
		when(storage.getToken(t.getHashedToken())).thenReturn(expected);
		
		final HashedToken ht = auth.getToken(t);
		assertThat("incorrect token", ht, is(new HashedToken(TokenType.LOGIN, Optional.absent(),
				id, "whee", new UserName("foo"), now, now)));
	}
	
	@Test
	public void getTokenFailNull() throws Exception {
		final Authentication auth = initTestAuth().auth;
		failGetToken(auth, null, new NullPointerException("token"));
	}

	
	@Test
	public void getTokenFailNoSuchToken() throws Exception {
		final TestAuth testauth = initTestAuth();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		
		final IncomingToken t = new IncomingToken("foobar");
		
		when(storage.getToken(t.getHashedToken())).thenThrow(new NoSuchTokenException("foo"));
		
		failGetToken(auth, t, new InvalidTokenException());
	}

	private void failGetToken(
			final Authentication auth,
			final IncomingToken t,
			final Exception e) {
		try {
			auth.getToken(t);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}
	
	@Test
	public void getTokens() throws Exception {
		final TestAuth testauth = initTestAuth();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		
		final IncomingToken t = new IncomingToken("foobar");
		
		final Instant now = Instant.now();
		final UUID id = UUID.randomUUID();
		
		final HashedToken expected = new HashedToken(TokenType.LOGIN, null, id,
				"whee", new UserName("foo"), now, now);
		
		when(storage.getToken(t.getHashedToken())).thenReturn(expected, (HashedToken) null);
		
		when(storage.getTokens(new UserName("foo"))).thenReturn(set(TOKEN1, TOKEN2));
		
		final TokenSet ts = auth.getTokens(t);
		assertThat("incorrect token set", ts, is(new TokenSet(expected, set(TOKEN2, TOKEN1))));
	}
	
	@Test
	public void getTokensFailNull() throws Exception {
		final Authentication auth = initTestAuth().auth;
		failGetTokens(auth, null, new NullPointerException("token"));
	}

	
	@Test
	public void getTokensFailNoSuchToken() throws Exception {
		final TestAuth testauth = initTestAuth();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		
		final IncomingToken t = new IncomingToken("foobar");
		
		when(storage.getToken(t.getHashedToken())).thenThrow(new NoSuchTokenException("foo"));
		
		failGetTokens(auth, t, new InvalidTokenException());
	}
	
	private void failGetTokens(
			final Authentication auth,
			final IncomingToken t,
			final Exception e) {
		try {
			auth.getToken(t);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}
	
	@Test
	public void getTokensUser() throws Exception {
		final AuthUser admin = new AuthUser(new UserName("admin"), new EmailAddress("f@g.com"),
				new DisplayName("bar"), Collections.emptySet(), set(Role.ADMIN),
				Collections.emptySet(), Instant.now(), null, new UserDisabledState());
		getTokensUser(admin);
	}
	
	@Test
	public void getTokensUserFailNonAdmin() throws Exception {
		final AuthUser admin = new AuthUser(new UserName("admin"), new EmailAddress("f@g.com"),
				new DisplayName("bar"), Collections.emptySet(), set(Role.DEV_TOKEN),
				Collections.emptySet(), Instant.now(), null, new UserDisabledState());
		failGetTokensUser(admin, new UnauthorizedException(ErrorType.UNAUTHORIZED));
	}
	
	@Test
	public void getTokensUserFailCreate() throws Exception {
		final AuthUser admin = new AuthUser(new UserName("admin"), new EmailAddress("f@g.com"),
				new DisplayName("bar"), Collections.emptySet(), set(Role.CREATE_ADMIN),
				Collections.emptySet(), Instant.now(), null, new UserDisabledState());
		failGetTokensUser(admin, new UnauthorizedException(ErrorType.UNAUTHORIZED));
	}
	
	@Test
	public void getTokensUserFailRoot() throws Exception {
		final AuthUser admin = new AuthUser(UserName.ROOT, new EmailAddress("f@g.com"),
				new DisplayName("bar"), Collections.emptySet(), set(Role.ROOT),
				Collections.emptySet(), Instant.now(), null, new UserDisabledState());
		failGetTokensUser(admin, new UnauthorizedException(ErrorType.UNAUTHORIZED));
	}
	
	@Test
	public void getTokensUserFailDisabled() throws Exception {
		final AuthUser admin = new AuthUser(new UserName("admin"), new EmailAddress("f@g.com"),
				new DisplayName("bar"), Collections.emptySet(), set(Role.DEV_TOKEN),
				Collections.emptySet(), Instant.now(), null,
				new UserDisabledState("foo", new UserName("baz"), Instant.now()));
		failGetTokensUser(admin, new DisabledUserException());
	}
	
	@Test
	public void getTokensUserFailNulls() throws Exception {
		final TestAuth testauth = initTestAuth();
		final Authentication auth = testauth.auth;
		
		failGetTokensUser(auth, null, new UserName("foo"), new NullPointerException("token"));
		failGetTokensUser(auth, new IncomingToken("foo"), null,
				new NullPointerException("userName"));
	}
	
	@Test
	public void getTokensUserFailInvalidToken() throws Exception {
		final TestAuth testauth = initTestAuth();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		
		final IncomingToken t = new IncomingToken("foobarbaz");
		
		when(storage.getToken(t.getHashedToken())).thenThrow(new NoSuchTokenException("foo"));
		
		failGetTokensUser(auth, t, new UserName("bar"), new InvalidTokenException());
	}
	
	@Test
	public void forceResetAllPasswordsFailCatastrophicNoUser() throws Exception {
		final TestAuth testauth = initTestAuth();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		
		final IncomingToken t = new IncomingToken("foobarbaz");
		
		final HashedToken token = new HashedToken(TokenType.LOGIN, Optional.absent(),
				UUID.randomUUID(), "wubba", new UserName("admin"), Instant.now(), Instant.now());
		
		when(storage.getToken(t.getHashedToken())).thenReturn(token, (HashedToken) null);
		
		when(storage.getUser(new UserName("admin"))).thenThrow(new NoSuchUserException("admin"));
		
		failGetTokensUser(auth, t, new UserName("bar"), new RuntimeException(
				"There seems to be an error in the storage system. Token was valid, but no user"));
	}

	private void getTokensUser(final AuthUser admin) throws Exception {
		final TestAuth testauth = initTestAuth();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		
		final IncomingToken t = new IncomingToken("foobarbaz");
		
		final HashedToken token = new HashedToken(TokenType.LOGIN, Optional.absent(),
				UUID.randomUUID(), "wubba", admin.getUserName(), Instant.now(), Instant.now());
		
		when(storage.getToken(t.getHashedToken())).thenReturn(token, (HashedToken) null);
		
		when(storage.getUser(admin.getUserName())).thenReturn(admin, (AuthUser) null);
		
		when(storage.getTokens(new UserName("foo"))).thenReturn(set(TOKEN1, TOKEN2));
		
		try {
			final Set<HashedToken> tokens = auth.getTokens(t, new UserName("foo"));
			assertThat("incorrect tokens", tokens, is(set(TOKEN1, TOKEN2)));
		} catch (Throwable th) {
			if (admin.isDisabled()) {
				verify(storage).deleteTokens(admin.getUserName());
			}
			throw th;
		}
	}
	
	private void failGetTokensUser(final AuthUser admin, final Exception e) {
		try {
			getTokensUser(admin);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}
	
	private void failGetTokensUser(
			final Authentication auth,
			final IncomingToken token,
			final UserName name,
			final Exception e) {
		try {
			auth.getTokens(token, name);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}
	
	@Test
	public void getBareToken() throws Exception {
		final TestAuth testauth = initTestAuth();
		final Authentication auth = testauth.auth;
		final RandomDataGenerator rand = testauth.randGen;
		
		when(rand.getToken()).thenReturn("foobar");
		
		assertThat("incorrect token", auth.getBareToken(), is("foobar"));
	}
	
	@Test
	public void revokeSelf() throws Exception {
		final TestAuth testauth = initTestAuth();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		
		final IncomingToken t = new IncomingToken("foobar");
		
		final UUID id = UUID.randomUUID();
		
		final HashedToken ht = new HashedToken(TokenType.LOGIN, null, id, "baz",
				new UserName("foo"), Instant.now(), Instant.now());
		
		when(storage.getToken(t.getHashedToken())).thenReturn(ht, (HashedToken) null);
		
		final Optional<HashedToken> res = auth.revokeToken(t);
		
		verify(storage).deleteToken(new UserName("foo"), id);
		
		assertThat("incorrect token", res, is(Optional.of(ht)));
	}
	
	@Test
	public void revokeSelfNoToken() throws Exception {
		final TestAuth testauth = initTestAuth();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		
		final IncomingToken t = new IncomingToken("foobar");
		
		when(storage.getToken(t.getHashedToken())).thenThrow(new NoSuchTokenException("foo"));
		
		final Optional<HashedToken> res = auth.revokeToken(t);
		
		assertThat("incorrect token", res, is(Optional.absent()));
	}
	
	@Test
	public void revokeSelfFail() throws Exception {
		final Authentication auth = initTestAuth().auth;
		try {
			auth.revokeToken(null);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NullPointerException("token"));
		}
	}
	
	@Test
	public void revokeToken() throws Exception {
		final TestAuth testauth = initTestAuth();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		
		final IncomingToken t = new IncomingToken("foobar");
		
		final UUID target = UUID.randomUUID();
		
		final HashedToken ht = new HashedToken(TokenType.LOGIN, null, UUID.randomUUID(), "baz",
				new UserName("foo"), Instant.now(), Instant.now());
		
		when(storage.getToken(t.getHashedToken())).thenReturn(ht, (HashedToken) null);
		
		auth.revokeToken(t, target);
		
		verify(storage).deleteToken(new UserName("foo"), target);
	}
	
	@Test
	public void revokeTokenFailBadIncomingToken() throws Exception {
		final TestAuth testauth = initTestAuth();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		
		final IncomingToken t = new IncomingToken("foobar");
		
		when(storage.getToken(t.getHashedToken())).thenThrow(new NoSuchTokenException("foo"));
		
		failRevokeToken(auth, t, UUID.randomUUID(), new InvalidTokenException());
		
	}
	
	@Test
	public void revokeTokenFailNoSuchToken() throws Exception {
		final TestAuth testauth = initTestAuth();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		
		final IncomingToken t = new IncomingToken("foobar");
		
		final UUID target = UUID.randomUUID();
		
		final HashedToken ht = new HashedToken(TokenType.LOGIN, null, UUID.randomUUID(), "baz",
				new UserName("foo"), Instant.now(), Instant.now());
		
		when(storage.getToken(t.getHashedToken())).thenReturn(ht, (HashedToken) null);
		
		doThrow(new NoSuchTokenException(target.toString()))
			.when(storage).deleteToken(new UserName("foo"), target);
		
		failRevokeToken(auth, t, target, new NoSuchTokenException(target.toString()));
	}
	
	@Test
	public void revokeTokenFailNulls() throws Exception {
		final Authentication auth = initTestAuth().auth;
		failRevokeToken(auth, null, UUID.randomUUID(), new NullPointerException("token"));
		failRevokeToken(auth, new IncomingToken("f"), null, new NullPointerException("tokenID"));
	}
	
	private void failRevokeToken(
			final Authentication auth,
			final IncomingToken t,
			final UUID target,
			final Exception e) {
		try {
			auth.revokeToken(t, target);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}
	
	@Test
	public void revokeTokenAdmin() throws Exception {
		final AuthUser admin = new AuthUser(new UserName("admin"), new EmailAddress("f@g.com"),
				new DisplayName("bar"), Collections.emptySet(), set(Role.ADMIN),
				Collections.emptySet(), Instant.now(), null, new UserDisabledState());

		revokeTokenAdmin(admin);
	}
	
	@Test
	public void revokeTokenAdminFailStdUser() throws Exception {
		final AuthUser admin = new AuthUser(new UserName("admin"), new EmailAddress("f@g.com"),
				new DisplayName("bar"), Collections.emptySet(), set(Role.SERV_TOKEN),
				Collections.emptySet(), Instant.now(), null, new UserDisabledState());

		failRevokeTokenAdmin(admin, new UnauthorizedException(ErrorType.UNAUTHORIZED));
	}
	
	@Test
	public void revokeTokenAdminFailCreate() throws Exception {
		final AuthUser admin = new AuthUser(new UserName("admin"), new EmailAddress("f@g.com"),
				new DisplayName("bar"), Collections.emptySet(), set(Role.CREATE_ADMIN),
				Collections.emptySet(), Instant.now(), null, new UserDisabledState());

		failRevokeTokenAdmin(admin, new UnauthorizedException(ErrorType.UNAUTHORIZED));
	}
	
	@Test
	public void revokeTokenAdminFailRoot() throws Exception {
		final AuthUser admin = new AuthUser(UserName.ROOT, new EmailAddress("f@g.com"),
				new DisplayName("bar"), Collections.emptySet(), set(Role.ROOT),
				Collections.emptySet(), Instant.now(), null, new UserDisabledState());

		failRevokeTokenAdmin(admin, new UnauthorizedException(ErrorType.UNAUTHORIZED));
	}
	
	@Test
	public void revokeTokenAdminFailDisabled() throws Exception {
		final AuthUser admin = new AuthUser(new UserName("admin"), new EmailAddress("f@g.com"),
				new DisplayName("bar"), Collections.emptySet(), set(Role.SERV_TOKEN),
				Collections.emptySet(), Instant.now(), null,
				new UserDisabledState("foo", new UserName("bar"), Instant.now()));

		failRevokeTokenAdmin(admin, new DisabledUserException());
	}
	
	@Test
	public void revokeTokenAdminFailNulls() throws Exception {
		final Authentication auth = initTestAuth().auth;
		failRevokeTokenAdmin(auth, null, new UserName("foo"), UUID.randomUUID(),
				new NullPointerException("token"));
		failRevokeTokenAdmin(auth, new IncomingToken("f"), null, UUID.randomUUID(),
				new NullPointerException("userName"));
		failRevokeTokenAdmin(auth, new IncomingToken("f"), new UserName("foo"), null,
				new NullPointerException("tokenID"));
	}
	
	@Test
	public void revokeTokenAdminFailBadToken() throws Exception {
		final TestAuth testauth = initTestAuth();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		
		final IncomingToken t = new IncomingToken("foobar");
		final UUID target = UUID.randomUUID();
		
		when(storage.getToken(t.getHashedToken())).thenThrow(new NoSuchTokenException("foo"));
		
		failRevokeTokenAdmin(auth, t, new UserName("foo"), target, new InvalidTokenException());
	}
	
	@Test
	public void revokeTokenAdminFailCatastropic() throws Exception {
		final TestAuth testauth = initTestAuth();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		
		final IncomingToken t = new IncomingToken("foobar");
		
		final UUID target = UUID.randomUUID();
		
		final HashedToken ht = new HashedToken(TokenType.LOGIN, null, UUID.randomUUID(), "baz",
				new UserName("foo"), Instant.now(), Instant.now());
		
		when(storage.getToken(t.getHashedToken())).thenReturn(ht, (HashedToken) null);
		
		when(storage.getUser(new UserName("foo"))).thenThrow(new NoSuchUserException("foo"));
		
		failRevokeTokenAdmin(auth, t, new UserName("bar"), target, new RuntimeException(
				"There seems to be an error in the " +
				"storage system. Token was valid, but no user"));
	}
	
	@Test
	public void revokeTokenAdminFailNoSuchToken() throws Exception {
		final TestAuth testauth = initTestAuth();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		
		final IncomingToken t = new IncomingToken("foobar");
		final UUID target = UUID.randomUUID();
		
		final HashedToken ht = new HashedToken(TokenType.LOGIN, null, UUID.randomUUID(), "baz",
				new UserName("foo"), Instant.now(), Instant.now());
		
		final AuthUser admin = new AuthUser(new UserName("admin"), new EmailAddress("f@g.com"),
				new DisplayName("bar"), Collections.emptySet(), set(Role.ADMIN),
				Collections.emptySet(), Instant.now(), null, new UserDisabledState());
		
		when(storage.getToken(t.getHashedToken())).thenReturn(ht, (HashedToken) null);
		
		when(storage.getUser(new UserName("foo"))).thenReturn(admin);
		
		doThrow(new NoSuchTokenException(target.toString()))
				.when(storage).deleteToken(new UserName("bar"), target);
		
		failRevokeTokenAdmin(auth, t, new UserName("bar"), target,
				new NoSuchTokenException(target.toString()));
	}

	private void revokeTokenAdmin(final AuthUser admin) throws Exception {
		final TestAuth testauth = initTestAuth();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		
		final IncomingToken t = new IncomingToken("foobar");
		
		final UUID target = UUID.randomUUID();
		
		final HashedToken ht = new HashedToken(TokenType.LOGIN, null, UUID.randomUUID(), "baz",
				admin.getUserName(), Instant.now(), Instant.now());
		
		when(storage.getToken(t.getHashedToken())).thenReturn(ht, (HashedToken) null);
		
		when(storage.getUser(admin.getUserName())).thenReturn(admin);
		
		try {
			auth.revokeToken(t, new UserName("whee"), target);
		
			verify(storage).deleteToken(new UserName("whee"), target);
		} catch (Throwable th) {
			if (admin.isDisabled()) {
				verify(storage).deleteTokens(admin.getUserName());
			}
			throw th;
		}
	}
	
	private void failRevokeTokenAdmin(
			final AuthUser admin,
			final Exception e) {
		try {
			revokeTokenAdmin(admin);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}
	
	private void failRevokeTokenAdmin(
			final Authentication auth,
			final IncomingToken t,
			final UserName name,
			final UUID target,
			final Exception e) {
		try {
			auth.revokeToken(t, name, target);
			fail("exception expected");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}
	
	@Test
	public void revokeAllTokensUser() throws Exception {
		final TestAuth testauth = initTestAuth();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		
		final IncomingToken t = new IncomingToken("foobar");
		
		final HashedToken ht = new HashedToken(TokenType.LOGIN, null, UUID.randomUUID(), "baz",
				new UserName("foo"), Instant.now(), Instant.now());
		
		when(storage.getToken(t.getHashedToken())).thenReturn(ht, (HashedToken) null);
		
		auth.revokeTokens(t);
		
		verify(storage).deleteTokens(new UserName("foo"));
	}
	
	@Test
	public void revokeAllTokensUserFailNull() throws Exception {
		final Authentication auth = initTestAuth().auth;
		failRevokeAllTokensUser(auth, null, new NullPointerException("token"));
	}
	
	@Test
	public void revokeAllTokenUserFailBadToken() throws Exception {
		final TestAuth testauth = initTestAuth();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		
		final IncomingToken t = new IncomingToken("foobar");
		
		when(storage.getToken(t.getHashedToken())).thenThrow(new NoSuchTokenException("foo"));
		
		failRevokeAllTokensUser(auth, t, new InvalidTokenException());
	}
	
	private void failRevokeAllTokensUser(
			final Authentication auth,
			final IncomingToken t,
			final Exception e) {
		try {
			auth.revokeTokens(t);
			fail("expected exceptoin");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}

	@Test
	public void revokeAllTokensAdminAll() throws Exception {
		final AuthUser admin = new AuthUser(new UserName("admin"), new EmailAddress("f@g.com"),
				new DisplayName("bar"), Collections.emptySet(), set(Role.ADMIN),
				Collections.emptySet(), Instant.now(), null, new UserDisabledState());
		
		revokeAllTokensAdminAll(admin);
	}
	
	@Test
	public void revokeAllTokensAdminAllFailStdUser() throws Exception {
		final AuthUser admin = new AuthUser(new UserName("admin"), new EmailAddress("f@g.com"),
				new DisplayName("bar"), Collections.emptySet(), set(Role.SERV_TOKEN),
				Collections.emptySet(), Instant.now(), null, new UserDisabledState());

		failRevokeAllTokensAdminAll(admin, new UnauthorizedException(ErrorType.UNAUTHORIZED));
	}
	
	@Test
	public void revokeAllTokensAdminAllFailCreate() throws Exception {
		final AuthUser admin = new AuthUser(new UserName("admin"), new EmailAddress("f@g.com"),
				new DisplayName("bar"), Collections.emptySet(), set(Role.CREATE_ADMIN),
				Collections.emptySet(), Instant.now(), null, new UserDisabledState());

		failRevokeAllTokensAdminAll(admin, new UnauthorizedException(ErrorType.UNAUTHORIZED));
	}
	
	@Test
	public void revokeAllTokensAdminAllFailRoot() throws Exception {
		final AuthUser admin = new AuthUser(UserName.ROOT, new EmailAddress("f@g.com"),
				new DisplayName("bar"), Collections.emptySet(), set(Role.ROOT),
				Collections.emptySet(), Instant.now(), null, new UserDisabledState());

		failRevokeAllTokensAdminAll(admin, new UnauthorizedException(ErrorType.UNAUTHORIZED));
	}
	
	@Test
	public void revokeAllTokensAdminAllFailDisabled() throws Exception {
		final AuthUser admin = new AuthUser(new UserName("admin"), new EmailAddress("f@g.com"),
				new DisplayName("bar"), Collections.emptySet(), set(Role.SERV_TOKEN),
				Collections.emptySet(), Instant.now(), null,
				new UserDisabledState("foo", new UserName("bar"), Instant.now()));

		failRevokeAllTokensAdminAll(admin, new DisabledUserException());
	}
	
	@Test
	public void revokeAllTokensAdminAllFailNull() throws Exception {
		final Authentication auth = initTestAuth().auth;
		failRevokeAllTokensAdminAll(auth, null, new NullPointerException("token"));
	}
	
	@Test
	public void revokeAllTokensAdminAllFailBadToken() throws Exception {
		final TestAuth testauth = initTestAuth();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		
		final IncomingToken t = new IncomingToken("foobar");
		
		when(storage.getToken(t.getHashedToken())).thenThrow(new NoSuchTokenException("foo"));
		
		failRevokeAllTokensAdminAll(auth, t, new InvalidTokenException());
	}
	
	@Test
	public void revokeAllTokensAdminAllFailCatastropic() throws Exception {
		final TestAuth testauth = initTestAuth();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		
		final IncomingToken t = new IncomingToken("foobar");
		
		final HashedToken ht = new HashedToken(TokenType.LOGIN, null, UUID.randomUUID(), "baz",
				new UserName("foo"), Instant.now(), Instant.now());
		
		when(storage.getToken(t.getHashedToken())).thenReturn(ht, (HashedToken) null);
		
		when(storage.getUser(new UserName("foo"))).thenThrow(new NoSuchUserException("foo"));
		
		failRevokeAllTokensAdminAll(auth, t, new RuntimeException(
				"There seems to be an error in the " +
				"storage system. Token was valid, but no user"));
	}
	
	private void revokeAllTokensAdminAll(final AuthUser admin) throws Exception {
		final TestAuth testauth = initTestAuth();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		
		final IncomingToken t = new IncomingToken("foobar");
		
		final HashedToken ht = new HashedToken(TokenType.LOGIN, null, UUID.randomUUID(), "baz",
				admin.getUserName(), Instant.now(), Instant.now());
		
		when(storage.getToken(t.getHashedToken())).thenReturn(ht, (HashedToken) null);
		
		when(storage.getUser(admin.getUserName())).thenReturn(admin);
		
		try {
			auth.revokeAllTokens(t);
		
			verify(storage).deleteTokens();
		} catch (Throwable th) {
			if (admin.isDisabled()) {
				verify(storage).deleteTokens(admin.getUserName());
			}
			throw th;
		}
	}
	
	private void failRevokeAllTokensAdminAll(
			final AuthUser admin,
			final Exception e) {
		try {
			revokeAllTokensAdminAll(admin);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}
	
	private void failRevokeAllTokensAdminAll(
			final Authentication auth,
			final IncomingToken t,
			final Exception e) {
		try {
			auth.revokeAllTokens(t);
			fail("expected exceptoin");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}
	
	@Test
	public void revokeAllTokensAdminUser() throws Exception {
		final AuthUser admin = new AuthUser(new UserName("admin"), new EmailAddress("f@g.com"),
				new DisplayName("bar"), Collections.emptySet(), set(Role.ADMIN),
				Collections.emptySet(), Instant.now(), null, new UserDisabledState());
		
		revokeAllTokensAdminUser(admin);
	}
	
	@Test
	public void revokeAllTokensAdminUserFailStdUser() throws Exception {
		final AuthUser admin = new AuthUser(new UserName("admin"), new EmailAddress("f@g.com"),
				new DisplayName("bar"), Collections.emptySet(), set(Role.SERV_TOKEN),
				Collections.emptySet(), Instant.now(), null, new UserDisabledState());

		failRevokeAllTokensAdminUser(admin, new UnauthorizedException(ErrorType.UNAUTHORIZED));
	}
	
	@Test
	public void revokeAllTokensAdminUserFailCreate() throws Exception {
		final AuthUser admin = new AuthUser(new UserName("admin"), new EmailAddress("f@g.com"),
				new DisplayName("bar"), Collections.emptySet(), set(Role.CREATE_ADMIN),
				Collections.emptySet(), Instant.now(), null, new UserDisabledState());

		failRevokeAllTokensAdminUser(admin, new UnauthorizedException(ErrorType.UNAUTHORIZED));
	}
	
	@Test
	public void revokeAllTokensAdminUserFailRoot() throws Exception {
		final AuthUser admin = new AuthUser(UserName.ROOT, new EmailAddress("f@g.com"),
				new DisplayName("bar"), Collections.emptySet(), set(Role.ROOT),
				Collections.emptySet(), Instant.now(), null, new UserDisabledState());

		failRevokeAllTokensAdminUser(admin, new UnauthorizedException(ErrorType.UNAUTHORIZED));
	}
	
	@Test
	public void revokeAllTokensAdminUserFailDisabled() throws Exception {
		final AuthUser admin = new AuthUser(new UserName("admin"), new EmailAddress("f@g.com"),
				new DisplayName("bar"), Collections.emptySet(), set(Role.SERV_TOKEN),
				Collections.emptySet(), Instant.now(), null,
				new UserDisabledState("foo", new UserName("bar"), Instant.now()));

		failRevokeAllTokensAdminUser(admin, new DisabledUserException());
	}
	
	@Test
	public void revokeAllTokensAdminUserFailNull() throws Exception {
		final Authentication auth = initTestAuth().auth;
		failRevokeAllTokensAdminUser(auth, null, new UserName("foo"),
				new NullPointerException("token"));
		failRevokeAllTokensAdminUser(auth, new IncomingToken("f"), null,
				new NullPointerException("userName"));
	}
	
	@Test
	public void revokeAllTokensAdminUserFailBadToken() throws Exception {
		final TestAuth testauth = initTestAuth();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		
		final IncomingToken t = new IncomingToken("foobar");
		
		when(storage.getToken(t.getHashedToken())).thenThrow(new NoSuchTokenException("foo"));
		
		failRevokeAllTokensAdminUser(auth, t, new UserName("foo"), new InvalidTokenException());
	}
	
	@Test
	public void revokeAllTokensAdminUserFailCatastropic() throws Exception {
		final TestAuth testauth = initTestAuth();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		
		final IncomingToken t = new IncomingToken("foobar");
		
		final HashedToken ht = new HashedToken(TokenType.LOGIN, null, UUID.randomUUID(), "baz",
				new UserName("foo"), Instant.now(), Instant.now());
		
		when(storage.getToken(t.getHashedToken())).thenReturn(ht, (HashedToken) null);
		
		when(storage.getUser(new UserName("foo"))).thenThrow(new NoSuchUserException("foo"));
		
		failRevokeAllTokensAdminUser(auth, t, new UserName("whee"), new RuntimeException(
				"There seems to be an error in the " +
				"storage system. Token was valid, but no user"));
	}
	
	private void revokeAllTokensAdminUser(final AuthUser admin) throws Exception {
		final TestAuth testauth = initTestAuth();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		
		final IncomingToken t = new IncomingToken("foobar");
		
		final HashedToken ht = new HashedToken(TokenType.LOGIN, null, UUID.randomUUID(), "baz",
				admin.getUserName(), Instant.now(), Instant.now());
		
		when(storage.getToken(t.getHashedToken())).thenReturn(ht, (HashedToken) null);
		
		when(storage.getUser(admin.getUserName())).thenReturn(admin);
		
		try {
			auth.revokeAllTokens(t, new UserName("whee"));
		
			verify(storage).deleteTokens(new UserName("whee"));
		} catch (Throwable th) {
			if (admin.isDisabled()) {
				verify(storage).deleteTokens(admin.getUserName());
			}
			throw th;
		}
	}
	
	private void failRevokeAllTokensAdminUser(
			final AuthUser admin,
			final Exception e) {
		try {
			revokeAllTokensAdminUser(admin);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}
	
	private void failRevokeAllTokensAdminUser(
			final Authentication auth,
			final IncomingToken t,
			final UserName name,
			final Exception e) {
		try {
			auth.revokeAllTokens(t, name);
			fail("expected exceptoin");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}
	
	
	//TODO NOW TEST create
	
}