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


package org.elasticsearch.plugin.readonlyrest.rules;

import static org.mockito.Mockito.when;

import java.util.ArrayList;

import org.elasticsearch.plugin.readonlyrest.acl.blocks.Group;
import org.elasticsearch.plugin.readonlyrest.acl.blocks.rules.RuleExitResult;
import org.elasticsearch.plugin.readonlyrest.acl.blocks.rules.RuleNotConfiguredException;
import org.elasticsearch.plugin.readonlyrest.acl.blocks.rules.SyncRule;
import org.elasticsearch.plugin.readonlyrest.acl.blocks.rules.impl.GroupsSyncRule;
import org.elasticsearch.plugin.readonlyrest.oauth.OAuthToken;
import org.elasticsearch.plugin.readonlyrest.wiring.requestcontext.RequestContext;
import org.junit.Test;
import org.mockito.Mockito;

import junit.framework.TestCase;

public class GroupsSyncRuleTests extends TestCase {

	private OAuthToken token;
	private Group grp;
	
	protected void setUp() {
		token = Mockito.mock(OAuthToken.class);
		grp = Mockito.mock(Group.class);
	}
	private RuleExitResult match(RequestContext rc, Group grp) throws RuleNotConfiguredException {
		SyncRule r = new GroupsSyncRule(grp);
		return r.match(rc);
	}
	
	@Test
	public void testNoGroupsConfigured() {
		RequestContext rc = Mockito.mock(RequestContext.class);
		try {
			match(rc, null);
		} catch (RuleNotConfiguredException e) {
			assertTrue(true);
		}
		assertFalse(false);
	}
	
	@Test
	public void testNoTokenRoles() throws RuleNotConfiguredException {
		RequestContext rc = Mockito.mock(RequestContext.class);	
		when(rc.getToken()).thenReturn(token);
		when(token.getRoles()).thenReturn(null);
		when(grp.getGroup()).thenReturn("Test");
		RuleExitResult res = match(rc, grp);
		assertFalse(res.isMatch());
	}
	
	@Test
	public void testGroupKibanaNoToken() throws RuleNotConfiguredException {
		RequestContext rc = Mockito.mock(RequestContext.class);
		when(rc.getToken()).thenReturn(null);
		when(grp.getGroup()).thenReturn("Kibana");
		RuleExitResult res = match(rc, grp);
		assertTrue(res.isMatch());
	}
	
	@Test
	public void testGroupKibanaWithToken() throws RuleNotConfiguredException {
		RequestContext rc = Mockito.mock(RequestContext.class);
		when(rc.getToken()).thenReturn(token);
		when(grp.getGroup()).thenReturn("Kibana");
		RuleExitResult res = match(rc, grp);
		assertFalse(res.isMatch());
	}
	
	@Test
	public void testGroupAdmin() throws RuleNotConfiguredException {
		RequestContext rc = Mockito.mock(RequestContext.class);
		when(rc.getToken()).thenReturn(token);
		when(grp.getGroup()).thenReturn("Admin");
		ArrayList<String> tokenRoles = new ArrayList<>();
		tokenRoles.add("Admin");
		tokenRoles.add("Viewer");
		tokenRoles.add("Editor");
		when(token.getRoles()).thenReturn(tokenRoles);
		RuleExitResult res = match(rc, grp);
		assertTrue(res.isMatch());
	}
	
	@Test
	public void testGroupAdminSoloRole() throws RuleNotConfiguredException {
		RequestContext rc = Mockito.mock(RequestContext.class);
		when(rc.getToken()).thenReturn(token);
		when(grp.getGroup()).thenReturn("Admin");
		ArrayList<String> tokenRoles = new ArrayList<>();
		tokenRoles.add("Admin");
		when(token.getRoles()).thenReturn(tokenRoles);
		RuleExitResult res = match(rc, grp);
		assertTrue(res.isMatch());
	}
	
	@Test
	public void testViewerAgenceGroup() throws RuleNotConfiguredException {
		RequestContext rc = Mockito.mock(RequestContext.class);
		when(rc.getToken()).thenReturn(token);
		when(grp.getGroup()).thenReturn("viewer_agence");
		ArrayList<String> tokenRoles = new ArrayList<>();
		tokenRoles.add("Viewer");
		tokenRoles.add("viewer_agence");
		when(token.getRoles()).thenReturn(tokenRoles);
		RuleExitResult res = match(rc, grp);
		assertTrue(res.isMatch());
	}
	
	@Test
	public void testViewerAgenceForViewerToken() throws RuleNotConfiguredException {
		RequestContext rc = Mockito.mock(RequestContext.class);
		when(rc.getToken()).thenReturn(token);
		when(grp.getGroup()).thenReturn("viewer_agence");
		ArrayList<String> tokenRoles = new ArrayList<>();
		tokenRoles.add("Viewer");
		when(token.getRoles()).thenReturn(tokenRoles);
		RuleExitResult res = match(rc, grp);
		assertFalse(res.isMatch());
	}
	
}
