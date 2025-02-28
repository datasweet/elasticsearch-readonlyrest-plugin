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

package org.elasticsearch.plugin.readonlyrest.wiring.requestcontext;

import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.IndicesRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.get.MultiGetRequest;
import org.elasticsearch.action.search.MultiSearchRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.plugin.readonlyrest.acl.LoggedUser;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Created by sscarduzio on 13/04/2017.
 */
public class SubRequestContext extends Delayed implements IndicesRequestContext {
  private final Logger logger = Loggers.getLogger(getClass());
  private final RequestContext originalRC;
  private final Object originalSubRequest;
  private SubRCTransactionalIndices indices = new SubRCTransactionalIndices(this);

  public SubRequestContext(RequestContext originalRequestContext, Object originalSubRequest) {
    super("src");
    this.originalRC = originalRequestContext;
    this.originalSubRequest = originalSubRequest;

    // Register transactional fields
    indices.delegateTo(this);
  }

  static List<? extends IndicesRequest> extractNativeSubrequests(ActionRequest r) {

    if (r instanceof MultiSearchRequest) {
      return ((MultiSearchRequest) r).requests();

    }
    else if (r instanceof MultiGetRequest) {
      return ((MultiGetRequest) r).getItems();

    }
    else if (r instanceof BulkRequest) {
      return ((BulkRequest) r).requests();
    }

    else return new ArrayList<>(0);
  }

  public Object getOriginalSubRequest() {
    return originalSubRequest;
  }

  public Set<String> getIndices() {
    return indices.getInitial();
  }

  public void setIndices(Set<String> newIndices) {
    if (newIndices.size() == 0) {
      throw new RCUtils.RRContextException(
        "Attempted to set empty indices list in a sub-request this would allow full access, therefore this is forbidden." +
          " If this was intended, set '*' as indices.");
    }

    newIndices.remove("<no-index>");
    newIndices.remove("");

    Set<String> oldIndices = indices.get();
    if (newIndices.equals(oldIndices)) {
      logger.debug("id: " + getId() + " - Not replacing in sub-request. Indices are the same. Old:" + oldIndices + " New:" + newIndices);
      return;
    }
    logger.debug("id: " + getId() + " - Replacing indices in sub-request. Old:" + oldIndices + " New:" + newIndices);

    indices.mutate(newIndices);
  }

  @Override
  public Optional<LoggedUser> getLoggedInUser() {
    return originalRC.getLoggedInUser();
  }

  @Override
  public void setLoggedInUser(LoggedUser user) {
    originalRC.setLoggedInUser(user);
  }

  public boolean involvesIndices() {
    return true;
  }

  public Set<String> getExpandedIndices() {
    return originalRC.getExpandedIndices(indices.getInitial());
  }

  @Override
  public String getId() {
    return originalRC.getId() + "-sub-" + originalSubRequest.hashCode();
  }

  public Set<String> getAllIndicesAndAliases() {
    return originalRC.getAllIndicesAndAliases();
  }


  public Boolean isReadRequest() {
    if (originalSubRequest instanceof SearchRequest || originalSubRequest instanceof MultiGetRequest.Item) {
      return true;
    }
    else if (originalSubRequest instanceof DocWriteRequest<?>) {
      return false;
    }
    else {
      throw new RCUtils.RRContextException(
        "Cannot detect if read or write request " + originalSubRequest.getClass().getSimpleName());
    }
  }

  @Override
  public Optional<String> applyVariables(String original) {
    return originalRC.applyVariables(original);
  }


  public String toString() {
    return "sub-request: { original:" + originalRC.getClass().getSimpleName() +
      ", sub:" + originalSubRequest.getClass().getSimpleName() + "}";
  }

}
